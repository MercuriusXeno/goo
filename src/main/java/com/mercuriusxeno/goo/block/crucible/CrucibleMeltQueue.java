package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The stacks inserted into the crucible's melt pool in arrival order (decision
 * pool-keeps-stacks-in-order). Every item of every stack melts on its own clock of
 * ceil(mB ^ exponent) ticks, moving its goo into the reservoir in proportion as the
 * clock advances (decision melt-time-is-mb-to-a-power); a lone fuel advances one item
 * per tick, a cursor passing over the items in turn (decision lone-fuel-advances-one-item),
 * and the combo advances every item every tick (decision combo-advances-every-item).
 */
public final class CrucibleMeltQueue {

    /** Saves and syncs the queue as its entries, head first, and the cursor. */
    public static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(Saved::entries),
            Codec.INT.optionalFieldOf("cursor", 0).forGetter(Saved::cursor)
    ).apply(instance, Saved::new));

    /**
     * Where a melt tick moves goo: the reservoir.
     */
    public interface MeltSink {

        /**
         * Offers goo of one type to the sink.
         *
         * @param type     the goo type
         * @param amount   the mB offered
         * @param simulate true to answer what the sink would take without taking it
         * @return the mB taken, or that would be taken
         */
        int accept(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate);
    }

    private final List<Entry> entries = new ArrayList<>();
    /** The item the next lone-fuel tick advances, counted over every entry's items head first. */
    private int cursor;

    /**
     * Appends one entry per stack, in the order given, each of its items whole; a stack
     * carrying no goo appends nothing.
     *
     * @param stacks the stacks that arrived in the pool
     */
    public void appendAll(List<ValuedStack> stacks) {
        for (ValuedStack stack : stacks) {
            if (stack.volume() > 0) {
                entries.add(new Entry(stack.item(), stack.unit(), Collections.nCopies(stack.count(), 0.0)));
            }
        }
    }

    /**
     * Advances the item under the cursor one tick on the given clock, then moves the cursor
     * to the next item, wrapping to the first (decision lone-fuel-advances-one-item).
     *
     * @param exponent the burning fuel's melt exponent
     * @param pool     the melt pool's contents
     * @param sink     the reservoir
     * @return the pool after the tick
     */
    public GooContents advanceNext(double exponent, GooContents pool, MeltSink sink) {
        int items = itemCount();
        if (items == 0) {
            return pool;
        }
        int at = cursor % items;
        Advance advance = advanceItem(at, exponent, pool, sink);
        int left = itemCount();
        cursor = left == 0 ? 0 : (advance.finished() ? at : at + 1) % left;
        return advance.pool();
    }

    /**
     * Advances every item one tick on the given clock, the cursor staying on the item it
     * held (decision combo-advances-every-item).
     *
     * @param exponent the burning fuel's melt exponent
     * @param pool     the melt pool's contents
     * @param sink     the reservoir
     * @return the pool after the tick
     */
    public GooContents advanceEvery(double exponent, GooContents pool, MeltSink sink) {
        GooContents after = pool;
        for (int index = itemCount() - 1; index >= 0; index--) {
            Advance advance = advanceItem(index, exponent, after, sink);
            after = advance.pool();
            if (advance.finished() && index < cursor) {
                cursor--;
            }
        }
        int left = itemCount();
        cursor = left == 0 ? 0 : cursor % left;
        return after;
    }

    /**
     * Advances one item one tick, moving its share of the tick from the pool into the sink;
     * an item whose last tick lands leaves its entry. The tick holds, moving nothing and
     * advancing nothing, when the sink would refuse any of the share (decision
     * crucible-refuses-past-two-billion).
     *
     * @param index    the item, counted over every entry's items head first
     * @param exponent the burning fuel's melt exponent
     * @param pool     the melt pool's contents
     * @param sink     the reservoir
     * @return the pool after the tick and whether the item finished
     */
    private Advance advanceItem(int index, double exponent, GooContents pool, MeltSink sink) {
        int entryIndex = 0;
        int item = index;
        while (item >= entries.get(entryIndex).count()) {
            item -= entries.get(entryIndex).count();
            entryIndex++;
        }
        Entry entry = entries.get(entryIndex);
        Map<ResourceKey<GooTypeDefinition>, Integer> share = entry.shareOfNextTick(item, exponent, pool);
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> part : share.entrySet()) {
            if (sink.accept(part.getKey(), part.getValue(), true) < part.getValue()) {
                return new Advance(pool, false);
            }
        }
        GooContents after = CrucibleCapacity.drainAccepted(pool, share,
                (type, amount) -> sink.accept(type, amount, false));
        boolean finished = entry.finishesNextTick(item, exponent);
        Entry advanced = entry.withItemAdvanced(item, exponent);
        if (advanced.count() > 0) {
            entries.set(entryIndex, advanced);
        } else {
            entries.remove(entryIndex);
        }
        return new Advance(after, finished);
    }

    /**
     * Returns the items still melting across every entry.
     *
     * @return the item count
     */
    private int itemCount() {
        int items = 0;
        for (Entry entry : entries) {
            items += entry.count();
        }
        return items;
    }

    /** Empties the queue, as the pool it mirrors empties. */
    public void clear() {
        entries.clear();
        cursor = 0;
    }

    /**
     * Returns true when no stack waits to melt.
     *
     * @return true if the queue holds no entry
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Returns the oldest entry, the one drawn dissolving.
     *
     * @return the head entry, or null when the queue is empty
     */
    public @Nullable Entry head() {
        return entries.isEmpty() ? null : entries.getFirst();
    }

    /**
     * Returns the entries waiting behind the head, oldest first.
     *
     * @return the waiting entries
     */
    public List<Entry> waiting() {
        List<Entry> all = entries();
        return all.isEmpty() ? all : all.subList(1, all.size());
    }

    /**
     * Returns every entry, head first.
     *
     * @return a copy of the entries
     */
    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /**
     * Returns the queue as it saves: its entries and its cursor.
     *
     * @return the saved form
     */
    public Saved saved() {
        return new Saved(entries(), cursor);
    }

    /**
     * Replaces the queue with a loaded one.
     *
     * @param loaded the entries, head first, and the cursor
     */
    public void loadFrom(Saved loaded) {
        entries.clear();
        entries.addAll(loaded.entries());
        cursor = loaded.cursor();
    }

    /**
     * The queue as it saves.
     *
     * @param entries the entries, head first
     * @param cursor  the item the next lone-fuel tick advances
     */
    public record Saved(List<Entry> entries, int cursor) {
    }

    /**
     * The pool after one item's tick, and whether the tick finished the item.
     *
     * @param pool     the pool after the tick
     * @param finished true if the item melted whole and left its entry
     */
    private record Advance(GooContents pool, boolean finished) {
    }

    /**
     * One inserted stack and how far each of its items still melting has melted.
     *
     * @param item     the item's registry id
     * @param unit     the goo one item carries
     * @param progress each item's progress, from 0 whole to 1 gone, oldest item first
     */
    public record Entry(Identifier item, GooContents unit, List<Double> progress) {

        /** Saves an entry by its three fields. */
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Entry::item),
                GooContents.CODEC.fieldOf("unit").forGetter(Entry::unit),
                Codec.DOUBLE.listOf().fieldOf("progress").forGetter(Entry::progress)
        ).apply(instance, Entry::new));

        /** The share of a tick a finishing check allows for floating error in the summed progress. */
        private static final double FINISH_TOLERANCE = 0.5;

        /**
         * Keeps the progress as an unmodifiable copy.
         *
         * @param item     the item's registry id
         * @param unit     the goo one item carries
         * @param progress each item's progress
         */
        public Entry {
            progress = List.copyOf(progress);
        }

        /**
         * Returns the items of the stack still melting.
         *
         * @return the item count
         */
        public int count() {
            return progress.size();
        }

        /**
         * Returns how far the stack's first item has dissolved, from 0 whole to 1 gone.
         *
         * @return the first item's progress
         */
        public float dissolveFraction() {
            return progress.isEmpty() ? 1f : progress.getFirst().floatValue();
        }

        /**
         * Returns the ticks one item melts in on the given clock.
         *
         * @param exponent the burning fuel's melt exponent
         * @return the melt time in ticks
         */
        private long ticks(double exponent) {
            return CrucibleMath.meltTicks(unit.totalVolume(), exponent);
        }

        /**
         * Returns true when the next tick on the given clock is the item's last.
         *
         * @param index    the item within the stack
         * @param exponent the burning fuel's melt exponent
         * @return true if the next tick finishes the item
         */
        boolean finishesNextTick(int index, double exponent) {
            long ticks = ticks(exponent);
            return (progress.get(index) + 1.0 / ticks) * ticks >= ticks - FINISH_TOLERANCE;
        }

        /**
         * Returns the goo an item moves on its next tick: each type's volume up to the
         * progress after the tick, less what earlier ticks moved, the last tick moving the
         * rest, and never more than the pool holds.
         *
         * @param index    the item within the stack
         * @param exponent the burning fuel's melt exponent
         * @param pool     the melt pool's contents
         * @return the mB of each type to move
         */
        Map<ResourceKey<GooTypeDefinition>, Integer> shareOfNextTick(int index, double exponent, GooContents pool) {
            boolean finishes = finishesNextTick(index, exponent);
            double before = progress.get(index);
            double next = before + 1.0 / ticks(exponent);
            Map<ResourceKey<GooTypeDefinition>, Integer> share = new HashMap<>();
            unit.getAll().forEach((type, volume) -> {
                int target = finishes ? volume : movedBy(volume, next);
                int amount = Math.min(target - movedBy(volume, before), pool.getVolume(type));
                if (amount > 0) {
                    share.put(type, amount);
                }
            });
            return share;
        }

        /**
         * Returns this entry with one item a tick on: its progress advanced, or the item
         * gone when the tick finishes it.
         *
         * @param index    the item within the stack
         * @param exponent the burning fuel's melt exponent
         * @return the advanced entry
         */
        Entry withItemAdvanced(int index, double exponent) {
            List<Double> advanced = new ArrayList<>(progress);
            if (finishesNextTick(index, exponent)) {
                advanced.remove(index);
            } else {
                advanced.set(index, progress.get(index) + 1.0 / ticks(exponent));
            }
            return new Entry(item, unit, advanced);
        }

        /**
         * Returns the mB of a type an item has moved at the given progress.
         *
         * @param volume   the item's volume of the type
         * @param fraction the item's progress
         * @return the mB moved
         */
        private static int movedBy(int volume, double fraction) {
            return (int) Math.floor(volume * fraction);
        }
    }
}
