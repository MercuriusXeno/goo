package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The stacks inserted into the crucible's melt pool in arrival order, the head dissolving
 * first while the rest wait (decision pool-keeps-stacks-in-order). Each item melts on its
 * own clock of ceil(mB ^ exponent) ticks, moving its goo into the reservoir in proportion
 * as the clock advances (decision melt-time-is-mb-to-a-power).
 */
public final class CrucibleMeltQueue {

    /** Saves and syncs the queue as its entries, head first. */
    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf();

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

    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Appends one entry per stack, in the order given; a stack carrying no goo appends nothing.
     *
     * @param stacks the stacks that arrived in the pool
     */
    public void appendAll(List<ValuedStack> stacks) {
        for (ValuedStack stack : stacks) {
            if (stack.volume() > 0) {
                entries.addLast(new Entry(stack.item(), stack.count(), stack.unit(), 0));
            }
        }
    }

    /**
     * Advances the head item one tick on the given clock, moving its share of the tick from
     * the pool into the sink; an item whose last tick lands leaves its entry's count. The
     * tick holds, moving nothing and advancing nothing, when the sink would refuse any of the
     * share (decision crucible-refuses-past-two-billion).
     *
     * @param exponent the burning fuel's melt exponent
     * @param pool     the melt pool's contents
     * @param sink     the reservoir
     * @return the pool after the tick
     */
    public GooContents advanceHead(double exponent, GooContents pool, MeltSink sink) {
        Entry head = entries.peekFirst();
        if (head == null) {
            return pool;
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> share = head.shareOfNextTick(exponent, pool);
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> part : share.entrySet()) {
            if (sink.accept(part.getKey(), part.getValue(), true) < part.getValue()) {
                return pool;
            }
        }
        GooContents after = CrucibleCapacity.drainAccepted(pool, share,
                (type, amount) -> sink.accept(type, amount, false));
        entries.removeFirst();
        Entry advanced = head.advanced(exponent);
        if (advanced.count() > 0) {
            entries.addFirst(advanced);
        }
        return after;
    }

    /** Empties the queue, as the pool it mirrors empties. */
    public void clear() {
        entries.clear();
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
     * Returns the oldest entry, the one dissolving.
     *
     * @return the head entry, or null when the queue is empty
     */
    public @Nullable Entry head() {
        return entries.peekFirst();
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
     * Replaces the queue with loaded entries, head first.
     *
     * @param loaded the entries to hold
     */
    public void loadFrom(List<Entry> loaded) {
        entries.clear();
        entries.addAll(loaded);
    }

    /**
     * One inserted stack and how far its head item has melted.
     *
     * @param item     the item's registry id
     * @param count    the items of the stack still melting, the head item among them
     * @param unit     the goo one item carries
     * @param progress how far the head item has melted, from 0 whole to 1 gone
     */
    public record Entry(Identifier item, int count, GooContents unit, double progress) {

        /** Saves an entry by its four fields. */
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.fieldOf("count").forGetter(Entry::count),
                GooContents.CODEC.fieldOf("unit").forGetter(Entry::unit),
                Codec.DOUBLE.optionalFieldOf("progress", 0.0).forGetter(Entry::progress)
        ).apply(instance, Entry::new));

        /** The share of a tick a finishing check allows for floating error in the summed progress. */
        private static final double FINISH_TOLERANCE = 0.5;

        /**
         * Returns how far the head item has dissolved, from 0 whole to 1 gone.
         *
         * @return the head item's progress
         */
        public float dissolveFraction() {
            return (float) progress;
        }

        /**
         * Returns true when the next tick on the given clock is the head item's last.
         *
         * @param exponent the burning fuel's melt exponent
         * @return true if the next tick finishes the head item
         */
        boolean finishesNextTick(double exponent) {
            long ticks = CrucibleMath.meltTicks(unit.totalVolume(), exponent);
            return (progress + 1.0 / ticks) * ticks >= ticks - FINISH_TOLERANCE;
        }

        /**
         * Returns the goo the head item moves on its next tick: each type's volume up to the
         * progress after the tick, less what earlier ticks moved, the last tick moving the
         * rest, and never more than the pool holds.
         *
         * @param exponent the burning fuel's melt exponent
         * @param pool     the melt pool's contents
         * @return the mB of each type to move
         */
        Map<ResourceKey<GooTypeDefinition>, Integer> shareOfNextTick(double exponent, GooContents pool) {
            boolean finishes = finishesNextTick(exponent);
            double next = progress + 1.0 / CrucibleMath.meltTicks(unit.totalVolume(), exponent);
            Map<ResourceKey<GooTypeDefinition>, Integer> share = new HashMap<>();
            unit.getAll().forEach((type, volume) -> {
                int target = finishes ? volume : movedBy(volume, next);
                int amount = Math.min(target - movedBy(volume, progress), pool.getVolume(type));
                if (amount > 0) {
                    share.put(type, amount);
                }
            });
            return share;
        }

        /**
         * Returns this entry one tick on: the head item's progress advanced, or the head item
         * gone and the next one whole when the tick finishes it.
         *
         * @param exponent the burning fuel's melt exponent
         * @return the advanced entry
         */
        Entry advanced(double exponent) {
            if (finishesNextTick(exponent)) {
                return new Entry(item, count - 1, unit, 0);
            }
            return new Entry(item, count, unit, progress + 1.0 / CrucibleMath.meltTicks(unit.totalVolume(), exponent));
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
