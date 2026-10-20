package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.*;

/**
 * Redistributes goo across a connected vat stack so fluid settles to the
 * bottom-most vats first, like a real contiguous tank. Called whenever fluid
 * changes or stack topology changes on the server side. Void settles first,
 * so each vat's capacity is figured from the void it takes and the stack
 * never loses room (decision each-void-mb-adds-n-plus-one-capacity).
 */
public final class VatStackRedistributor {

    private VatStackRedistributor() {
    }

    /**
     * Collects all fluid in the stack containing {@code pos}, then fills
     * vats bottom-up, each to its own capacity before overflowing to the
     * next. Uses {@link GooFluidHandler#loadFrom} which suppresses change
     * callbacks to avoid re-entrance during bulk mutation.
     *
     * @param level the server level
     * @param pos   any vat position in the stack
     */
    public static void redistribute(Level level, BlockPos pos) {
        List<VatBlockEntity> stack = collectStack(level, pos);
        if (stack.size() <= 1 || anyRedistributing(stack)) {
            return;
        }
        setRedistributing(stack, true);
        try {
            doRedistribute(stack);
        } finally {
            setRedistributing(stack, false);
        }
    }

    /**
     * Returns true if any vat in the stack is already mid-redistribution.
     *
     * @param stack the vat stack to check
     * @return true if any vat has the redistributing flag set
     */
    private static boolean anyRedistributing(List<VatBlockEntity> stack) {
        for (VatBlockEntity vat : stack) {
            if (vat.redistributing) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the redistributing guard flag on every vat in the stack.
     *
     * @param stack the vat stack
     * @param value the flag value to set
     */
    private static void setRedistributing(List<VatBlockEntity> stack, boolean value) {
        for (VatBlockEntity vat : stack) {
            vat.redistributing = value;
        }
    }

    /**
     * Sums all goo, then fills vats bottom-up greedily: the labeled vats first, each
     * taking its label's type alone, then the unlabeled vats taking the rest, so goo
     * of another type settles in the unlabeled vats and none is left without a home
     * (decision labeled-vessel-accepts-only-its-label-type).
     *
     * @param stack the vat stack, bottom-to-top
     */
    private static void doRedistribute(List<VatBlockEntity> stack) {
        Map<ResourceKey<GooTypeDefinition>, Integer> pool = mergePool(stack);
        stack.stream().filter(vat -> vat.getGooLabel() != null).forEach(vat -> applySlice(vat, pool));
        stack.stream().filter(vat -> vat.getGooLabel() == null).forEach(vat -> applySlice(vat, pool));
    }

    /**
     * Merges all goo from every vat in the stack into a single mutable pool.
     *
     * @param stack the vat stack to merge
     * @return the merged goo pool
     */
    private static Map<ResourceKey<GooTypeDefinition>, Integer> mergePool(List<VatBlockEntity> stack) {
        Map<ResourceKey<GooTypeDefinition>, Integer> pool = new HashMap<>();
        for (VatBlockEntity vat : stack) {
            for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> e : vat.getContents().contents().entrySet()) {
                pool.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return pool;
    }

    /**
     * Takes a capacity-sized slice from the pool and applies it to the vat if changed.
     *
     * @param vat  the target vat
     * @param pool the mutable goo pool to take from
     */
    private static void applySlice(VatBlockEntity vat, Map<ResourceKey<GooTypeDefinition>, Integer> pool) {
        GooContents slice = takeSlice(pool, ContainerCapacity.VAT_BASE, vat.getGooLabel());
        if (!slice.equals(vat.getContents())) {
            vat.getFluidHandler().loadFrom(slice);
            vat.syncAfterRedistribution();
        }
    }

    /**
     * Takes one vat's share from the pool, removing consumed volume: every mB
     * of void first, then other goo up to the capacity that void grows the
     * base to. Returns an immutable {@link GooContents} for one vat.
     *
     * @param pool the mutable goo pool
     * @param base the vat's capacity with no void, in mB
     * @return the goo contents
     */
    static GooContents takeSlice(Map<ResourceKey<GooTypeDefinition>, Integer> pool, int base) {
        if (pool.isEmpty() || base <= 0) {
            return GooContents.EMPTY;
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> slice = new HashMap<>();
        int voidTaken = takeVoid(pool, slice);
        int remaining = ContainerCapacity.capacity(base, voidTaken) - voidTaken;
        var it = pool.entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            remaining -= takeEntry(it, it.next(), remaining, slice);
        }
        return new GooContents(slice);
    }

    /**
     * Takes one vat's share as {@link #takeSlice(Map, int)} does, a labeled vat drawing
     * on its label's type alone and leaving the rest of the pool standing.
     * decision labeled-vessel-accepts-only-its-label-type
     *
     * @param pool  the mutable goo pool
     * @param base  the vat's capacity with no void, in mB
     * @param label the vat's goo label type, or null where it wears none
     * @return the goo contents
     */
    static GooContents takeSlice(Map<ResourceKey<GooTypeDefinition>, Integer> pool, int base,
                                 @Nullable ResourceKey<GooTypeDefinition> label) {
        if (label == null) {
            return takeSlice(pool, base);
        }
        Integer held = pool.remove(label);
        if (held == null) {
            return GooContents.EMPTY;
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> own = new HashMap<>(Map.of(label, held));
        GooContents slice = takeSlice(own, base);
        pool.putAll(own);
        return slice;
    }

    /**
     * Moves every mB of void from the pool into the slice.
     *
     * @param pool  the mutable goo pool
     * @param slice the slice being built
     * @return the void moved, in mB
     */
    private static int takeVoid(Map<ResourceKey<GooTypeDefinition>, Integer> pool,
                                Map<ResourceKey<GooTypeDefinition>, Integer> slice) {
        Integer voidHeld = pool.remove(GooTypes.VOID);
        if (voidHeld == null || voidHeld <= 0) {
            return 0;
        }
        slice.put(GooTypes.VOID, voidHeld);
        return voidHeld;
    }

    /**
     * Consumes up to {@code remaining} mB from one pool entry, adds to slice, and returns amount taken.
     *
     * @param it        the pool iterator (for removal)
     * @param entry     the current pool entry
     * @param remaining the remaining capacity in mB
     * @param slice     the slice being built
     * @return the amount of mB taken
     */
    private static int takeEntry(Iterator<Map.Entry<ResourceKey<GooTypeDefinition>, Integer>> it,
                                 Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry, int remaining, Map<ResourceKey<GooTypeDefinition>, Integer> slice) {
        int take = Math.min(entry.getValue(), remaining);
        slice.put(entry.getKey(), take);
        if (take >= entry.getValue()) {
            it.remove();
        } else {
            entry.setValue(entry.getValue() - take);
        }
        return take;
    }

    /**
     * Walks the stack vertically from {@code pos} to collect all connected
     * {@link VatBlockEntity} instances, returned bottom-to-top.
     *
     * @param level the current level
     * @param pos   the block position
     * @return the list
     */
    private static List<VatBlockEntity> collectStack(Level level, BlockPos pos) {
        BlockPos bottom = findStackBottom(level, pos);
        return collectUpward(level, bottom);
    }

    /**
     * Walks upward from the bottom position, collecting VatBlockEntity instances.
     *
     * @param level  the current level
     * @param bottom the bottom-most vat position
     * @return the vat list, bottom-to-top
     */
    private static List<VatBlockEntity> collectUpward(Level level, BlockPos bottom) {
        List<VatBlockEntity> stack = new ArrayList<>();
        BlockPos cursor = bottom;
        while (level.getBlockEntity(cursor) instanceof VatBlockEntity vat) {
            stack.add(vat);
            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) {
                break;
            }
            cursor = cursor.above();
        }
        return stack;
    }

    /**
     * Walks downward from the given position to find the lowest vat in the stack.
     *
     * @param level the current level
     * @param pos   any position in the stack
     * @return the bottom-most vat position
     */
    private static BlockPos findStackBottom(Level level, BlockPos pos) {
        BlockPos bottom = pos;
        while (level.getBlockState(bottom.below()).getBlock() instanceof VatBlock) {
            bottom = bottom.below();
        }
        return bottom;
    }
}
