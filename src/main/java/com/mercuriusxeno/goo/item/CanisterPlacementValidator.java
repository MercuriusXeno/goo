package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.canister.ICanisterAttachable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * Validates canister placement positions and slot constraints.
 * Checks support blocks below and queries ICanisterAttachable for allowed slots.
 */
public final class CanisterPlacementValidator {

    private CanisterPlacementValidator() {
    }

    /**
     * The central 12x12 pixels of a block's top face, which a canister's slots stand on.
     */
    private static final VoxelShape CANISTER_FOOTPRINT = Block.column(12.0, 0.0, 16.0);

    /**
     * Returns true if the block below can support a canister: the top face of its
     * support shape covers the central 12x12 pixels, or it is an ICanisterAttachable
     * machine with capacity on top.
     *
     * @param level    the current level
     * @param belowPos the block position below the canister
     * @return true if the position can support a canister
     */
    static boolean isSupportedBelow(Level level, BlockPos belowPos) {
        if (coversCanisterFootprint(level.getBlockState(belowPos), level, belowPos)) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(belowPos);
        return be instanceof ICanisterAttachable att && att.canAttachOnTop();
    }

    /**
     * Returns true if the top face of the state's support shape covers the central
     * 12x12 pixels. Vanilla's RIGID test checks the outer ring instead and refuses
     * the vat, inset one pixel on each edge (decision canister-support-is-rigid-top-face).
     *
     * @param state the block state below the canister
     * @param level the current level
     * @param pos   the position of that block
     * @return true if the top face covers the canister footprint
     */
    static boolean coversCanisterFootprint(BlockState state, Level level, BlockPos pos) {
        VoxelShape topFace = state.getBlockSupportShape(level, pos).getFaceShape(Direction.UP);
        return !Shapes.joinIsNotEmpty(topFace, CANISTER_FOOTPRINT, BooleanOp.ONLY_SECOND);
    }

    /**
     * Returns the set of allowed slot indices for a canister block at the given
     * position, or null if no constraint applies. Queries the ICanisterAttachable
     * block below (if any) for its allowed slot set.
     *
     * @param level       the current level
     * @param canisterPos the canister block position
     * @return the allowed slots, or null if unconstrained
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull") // null = unconstrained (all slots); empty = none
    static @Nullable Set<Integer> getAllowedSlots(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity be = level.getBlockEntity(belowPos);
        if (be instanceof ICanisterAttachable att) {
            return att.allowedSlots();
        }
        return null;
    }

    /**
     * Returns true if the given slot is allowed for a canister block at the given
     * position. If there is no ICanisterAttachable below, all slots are allowed.
     *
     * @param level       the current level
     * @param canisterPos the canister block position
     * @param slot        the slot index to check
     * @return true if the slot is allowed
     */
    public static boolean isSlotAllowed(Level level, BlockPos canisterPos, int slot) {
        Set<Integer> allowed = getAllowedSlots(level, canisterPos);
        return allowed == null || allowed.contains(slot);
    }

    /**
     * Falls back to an allowed slot if the target slot is disallowed: the first
     * allowed slot on the fixed grid, or, where the machine below lays the slots
     * out at its own centers, the allowed slot nearest the target's grid cell.
     *
     * @param slot  the preferred slot index
     * @param level the current level
     * @param pos   the canister block position
     * @return the constrained slot index
     */
    static int constrainSlot(int slot, Level level, BlockPos pos) {
        if (isSlotAllowed(level, pos, slot)) {
            return slot;
        }
        Set<Integer> allowed = getAllowedSlots(level, pos);
        if (allowed != null && !allowed.isEmpty()) {
            return fallbackSlot(slot, allowed, CanisterSlotLayout.centersAt(level, pos));
        }
        return slot;
    }

    /**
     * The allowed slot a disallowed target falls back to.
     *
     * @param slot    the disallowed target slot, 0-8
     * @param allowed the non-empty allowed slots
     * @param centers the canister block's slot centers
     * @return the fallback slot
     */
    static int fallbackSlot(int slot, Set<Integer> allowed, float[][] centers) {
        if (centers == CanisterSlotLayout.SLOT_CENTERS || slot < 0 || slot >= CanisterSlotLayout.SLOT_COUNT) {
            return allowed.iterator().next();
        }
        float[] cell = CanisterSlotLayout.SLOT_CENTERS[slot];
        return CanisterSlotLayout.nearestAllowed(centers, allowed, cell[0], cell[1]);
    }

    /**
     * Computes the target slot for new block placement using the click location
     * on the adjacent solid block's face.
     *
     * @param clickLoc  the click location in world coordinates
     * @param placePos  the block position being placed
     * @param entryFace the face the placement enters from
     * @return the target slot index (0-8)
     */
    static int computePlacementSlot(Vec3 clickLoc, BlockPos placePos, Direction entryFace) {
        float px = (float) ((clickLoc.x - placePos.getX()) * CanisterSlotResolver.PIXELS_PER_BLOCK);
        float pz = (float) ((clickLoc.z - placePos.getZ()) * CanisterSlotResolver.PIXELS_PER_BLOCK);
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /**
     * Stamps owner UUID on the canister block entity.
     *
     * @param be     the canister block entity
     * @param player the player who placed the canister
     */
    static void stampOwner(CanisterBlockEntity be, Player player) {
        if (player != null) {
            be.setOwner(player.getUUID());
        }
    }
}
