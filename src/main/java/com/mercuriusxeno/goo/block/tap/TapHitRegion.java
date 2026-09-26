package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.block.ShapeHitCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;

/**
 * The part of a tap a hit lands on, so each HUD panel shows only for its own
 * part: the rate panel for the valve, the canister panel for the canister,
 * and neither for the body (decision valve-panel-reads-rate).
 */
public enum TapHitRegion {
    VALVE,
    CANISTER,
    BODY;

    /** Pixels per block. */
    private static final double PIXELS_PER_BLOCK = 16;

    /** South-facing canister slot shape, in blocks: 6..10 x, 4..16 y, 1..5 z pixels. */
    static final VoxelShape SOUTH_CANISTER_SLOT = Shapes.box(6 / PIXELS_PER_BLOCK, 4 / PIXELS_PER_BLOCK,
            1 / PIXELS_PER_BLOCK, 10 / PIXELS_PER_BLOCK, 16 / PIXELS_PER_BLOCK, 5 / PIXELS_PER_BLOCK);

    /** Per-facing canister slot shapes. */
    static final Map<Direction, VoxelShape> CANISTER_SLOT_SHAPES = TapShapeBuilder.buildSubShapes(SOUTH_CANISTER_SLOT);

    /**
     * @param facing the tap's facing
     * @return the canister slot shape for that facing, block-local
     */
    public static VoxelShape canisterSlotShape(Direction facing) {
        return CANISTER_SLOT_SHAPES.getOrDefault(facing, SOUTH_CANISTER_SLOT);
    }

    /**
     * Sorts a hit on a tap: the valve first, then the canister slot while a
     * canister stands in it, else the body.
     *
     * @param hit         the hit on the tap
     * @param pos         the tap's position
     * @param facing      the tap's facing
     * @param hasCanister whether a canister stands in the slot
     * @return the part the hit lands on
     */
    public static TapHitRegion of(BlockHitResult hit, BlockPos pos, Direction facing, boolean hasCanister) {
        if (ShapeHitCheck.hitInsideShape(hit, pos, TapValve.shape(facing))) {
            return VALVE;
        }
        if (hasCanister && ShapeHitCheck.hitInsideShape(hit, pos, canisterSlotShape(facing))) {
            return CANISTER;
        }
        return BODY;
    }
}
