package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One vat column as the HUD and the vat renderer both read it: walked once per
 * game tick through VatColumn and handed to every member's reader for the
 * rest of that tick (decision one-panel-painter-takes-rows).
 *
 * @param bottom   the bottom member's position
 * @param column   the column's span
 * @param members  each member's block entity bottom to top, null where it has none yet
 * @param contents every member's contents summed
 * @param capacity every member's capacity summed
 * @param water    every member's water summed (decision diagnose-then-fix-waterlogged-gasket-link)
 */
public record VatStack(BlockPos bottom, VatColumn column, List<@Nullable VatBlockEntity> members,
                       GooContents contents, int capacity, long water) {

    /** Every member position's column for the tick the cache was filled in. */
    private static final Map<BlockPos, VatStack> STACKS_THIS_TICK = new HashMap<>();
    /** The identity of the level the cache was filled from, so the cache holds no level alive. */
    private static int cachedLevelIdentity;
    /** The game tick the cache was filled in. */
    private static long cachedTick = Long.MIN_VALUE;

    /**
     * Returns the column holding the vat at pos, walking it at most once per tick.
     *
     * @param level the client level
     * @param pos   a position in the column
     * @return the column, or null when pos holds no vat
     */
    public static @Nullable VatStack at(Level level, BlockPos pos) {
        int levelIdentity = System.identityHashCode(level);
        if (levelIdentity != cachedLevelIdentity || level.getGameTime() != cachedTick) {
            STACKS_THIS_TICK.clear();
            cachedLevelIdentity = levelIdentity;
            cachedTick = level.getGameTime();
        }
        VatStack cached = STACKS_THIS_TICK.get(pos);
        if (cached != null || !isVat(level, pos.getY(), pos)) {
            return cached;
        }
        VatStack stack = walk(level, pos);
        for (int y = stack.column.bottomY(); y <= stack.column.topY(); y++) {
            STACKS_THIS_TICK.put(pos.atY(y), stack);
        }
        return stack;
    }

    /**
     * Returns the top member's position.
     *
     * @return the top position
     */
    public BlockPos top() {
        return bottom.atY(column.topY());
    }

    /**
     * Walks the column through the vat links and reads every member.
     *
     * @param level the client level
     * @param pos   a vat position in the column
     * @return the column
     */
    private static VatStack walk(Level level, BlockPos pos) {
        VatColumn column = VatColumn.walk(pos.getY(),
                y -> isVat(level, y, pos),
                y -> level.getBlockState(pos.atY(y)).getValue(VatBlock.VAT_ABOVE),
                y -> level.getBlockState(pos.atY(y)).getValue(VatBlock.VAT_BELOW));
        List<@Nullable VatBlockEntity> members = new ArrayList<>();
        int capacity = 0;
        long water = 0;
        for (int y = column.bottomY(); y <= column.topY(); y++) {
            BlockEntity be = level.getBlockEntity(pos.atY(y));
            VatBlockEntity vat = be instanceof VatBlockEntity v ? v : null;
            members.add(vat);
            capacity += vat == null ? 0 : vat.getCapacity();
            water += vat == null ? 0 : vat.getWaterVolume();
        }
        GooContents contents = column.sum(y -> contentsOf(members.get(column.indexFromBottom(y))));
        return new VatStack(pos.atY(column.bottomY()), column, Collections.unmodifiableList(members),
                contents, capacity, water);
    }

    /**
     * Returns whether the block at a Y in pos's column is a vat.
     *
     * @param level the client level
     * @param y     the Y to read
     * @param pos   any position in the column
     * @return true when the block there is a vat
     */
    private static boolean isVat(Level level, int y, BlockPos pos) {
        BlockState state = level.getBlockState(pos.atY(y));
        return state.getBlock() instanceof VatBlock;
    }

    /**
     * Returns a member's contents.
     *
     * @param vat the member's block entity, or null
     * @return its contents, or null when it has no block entity
     */
    private static @Nullable GooContents contentsOf(@Nullable VatBlockEntity vat) {
        return vat == null ? null : vat.getContents();
    }
}
