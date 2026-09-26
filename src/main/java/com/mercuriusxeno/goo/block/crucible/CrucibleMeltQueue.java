package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.ValuedStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The stacks inserted into the crucible's melt pool in arrival order; the goo each melt
 * tick drains is charged against the oldest, so the head dissolves first while the rest
 * wait (decision pool-keeps-stacks-in-order).
 */
public final class CrucibleMeltQueue {

    /** Saves and syncs the queue as its entries, head first. */
    public static final Codec<List<Entry>> CODEC = Entry.CODEC.listOf();

    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Appends one entry per stack, in the order given; a stack carrying no goo appends nothing.
     *
     * @param stacks the stacks that arrived in the pool
     */
    public void appendAll(List<ValuedStack> stacks) {
        for (ValuedStack stack : stacks) {
            if (stack.volume() > 0) {
                entries.addLast(new Entry(stack.item(), stack.count(), stack.volume(), 0));
            }
        }
    }

    /**
     * Charges drained goo against the head, removing each entry it empties and
     * carrying the rest to the entry behind.
     *
     * @param drained the goo volume the pool lost, in mB
     */
    public void charge(long drained) {
        long left = drained;
        while (left > 0 && !entries.isEmpty()) {
            Entry head = entries.removeFirst();
            long taken = Math.min(left, head.remaining());
            left -= taken;
            Entry charged = head.chargedBy(taken);
            if (charged.remaining() > 0) {
                entries.addFirst(charged);
            }
        }
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
     * One inserted stack and how much of its goo has drained.
     *
     * @param item        the item's registry id
     * @param count       the number of items in the stack
     * @param contributed the goo volume the stack brought to the pool, in mB
     * @param charged     the goo volume drained against it so far, in mB
     */
    public record Entry(Identifier item, int count, long contributed, long charged) {

        /** Saves an entry by its four fields. */
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.fieldOf("count").forGetter(Entry::count),
                Codec.LONG.fieldOf("contributed").forGetter(Entry::contributed),
                Codec.LONG.fieldOf("charged").forGetter(Entry::charged)
        ).apply(instance, Entry::new));

        /**
         * Returns the goo still to drain from this stack.
         *
         * @return the remaining volume in mB
         */
        public long remaining() {
            return contributed - charged;
        }

        /**
         * Returns how far this stack has dissolved, from 0 whole to 1 gone.
         *
         * @return the charged volume over the contributed volume
         */
        public float dissolveFraction() {
            return contributed <= 0 ? 1f : (float) ((double) charged / contributed);
        }

        /**
         * Returns this entry with more goo drained against it.
         *
         * @param amount the volume drained, in mB
         * @return the charged entry
         */
        Entry chargedBy(long amount) {
            return new Entry(item, count, contributed, charged + amount);
        }
    }
}
