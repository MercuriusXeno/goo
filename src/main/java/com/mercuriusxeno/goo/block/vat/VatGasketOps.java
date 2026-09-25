package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Gasket face resolution, stacking state computation, and break-time drop
 * logic for VatBlock.
 */
final class VatGasketOps {

    /**
     * Y midpoint for determining cap vs base clicks.
     */
    private static final double FACE_MID_Y = 8.0 / 16.0;

    private VatGasketOps() {
    }

    /**
     * Resolves which gasket face the player is targeting based on hit location.
     *
     * @param hit the ray trace hit result
     * @param pos the block position
     * @return the target gasket property
     */
    static BooleanProperty resolveGasketFace(BlockHitResult hit, BlockPos pos) {
        if (hit.getDirection() == Direction.UP) {
            return VatBlock.GASKET_CAP;
        }
        if (hit.getDirection() == Direction.DOWN) {
            return VatBlock.GASKET_BASE;
        }
        double localY = hit.getLocation().y - pos.getY();
        return localY >= FACE_MID_Y ? VatBlock.GASKET_CAP : VatBlock.GASKET_BASE;
    }

    /**
     * Returns true if the given gasket face is occluded by an adjacent vat.
     *
     * @param state      the block state
     * @param gasketProp the gasket blockstate property
     * @return true if face occluded
     */
    static boolean isFaceOccluded(BlockState state, BooleanProperty gasketProp) {
        return gasketProp == VatBlock.GASKET_CAP
                ? state.getValue(VatBlock.VAT_ABOVE)
                : state.getValue(VatBlock.VAT_BELOW);
    }

    /**
     * Computes VAT_ABOVE/VAT_BELOW from adjacent blocks.
     * Clears gasket flags for faces that become occluded by stacking.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     * @return the computed stack state
     */
    static BlockState computeStackState(BlockState state, Level level, BlockPos pos) {
        boolean above = level.getBlockState(pos.above()).getBlock() instanceof VatBlock;
        boolean below = level.getBlockState(pos.below()).getBlock() instanceof VatBlock;
        BlockState updated = state.setValue(VatBlock.VAT_ABOVE, above).setValue(VatBlock.VAT_BELOW, below);
        return clearOccludedGaskets(updated, above, below);
    }

    /**
     * Clears gasket flags on faces occluded by adjacent vats.
     *
     * @param state the current block state
     * @param above true if a vat is stacked above
     * @param below true if a vat is stacked below
     * @return the updated block state with occluded gasket flags cleared
     */
    private static BlockState clearOccludedGaskets(BlockState state, boolean above, boolean below) {
        BlockState result = state;
        if (above && result.getValue(VatBlock.GASKET_CAP)) {
            result = result.setValue(VatBlock.GASKET_CAP, false);
        }
        if (below && result.getValue(VatBlock.GASKET_BASE)) {
            result = result.setValue(VatBlock.GASKET_BASE, false);
        }
        return result;
    }

    /**
     * Drops a gasket item for each face that had a gasket in the old state
     * but was cleared in the new state due to occlusion.
     *
     * @param oldState the previous block state
     * @param newState the new block state
     * @param level    the current level
     * @param pos      the block position
     */
    static void popOccludedGaskets(
            BlockState oldState, BlockState newState, Level level, BlockPos pos) {
        popFaceGasket(level, pos, GasketRole.RECEIVER,
                oldState.getValue(VatBlock.GASKET_CAP) && !newState.getValue(VatBlock.GASKET_CAP));
        popFaceGasket(level, pos, GasketRole.TRANSMITTER,
                oldState.getValue(VatBlock.GASKET_BASE) && !newState.getValue(VatBlock.GASKET_BASE));
    }

    /**
     * Notifies the vat blocks above and below to re-check their stacking state.
     *
     * @param level the current level
     * @param pos   the block position
     */
    static void notifyVerticalNeighbors(Level level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        if (level.getBlockState(abovePos).getBlock() instanceof VatBlock) {
            level.neighborChanged(abovePos, level.getBlockState(pos).getBlock(), null);
        }
        BlockPos belowPos = pos.below();
        if (level.getBlockState(belowPos).getBlock() instanceof VatBlock) {
            level.neighborChanged(belowPos, level.getBlockState(pos).getBlock(), null);
        }
    }

    /**
     * Drops gasket items for any installed gaskets.
     *
     * @param state the block state
     * @param level the current level
     * @param pos   the block position
     */
    static void dropGaskets(BlockState state, Level level, BlockPos pos) {
        popFaceGasket(level, pos, GasketRole.RECEIVER, state.getValue(VatBlock.GASKET_CAP));
        popFaceGasket(level, pos, GasketRole.TRANSMITTER, state.getValue(VatBlock.GASKET_BASE));
    }

    /**
     * Pops the gasket on one vat face through {@link GasketInstallation#popGasket},
     * clearing the vat's own record of it when the vat stays standing.
     *
     * @param level     the current level
     * @param pos       the block position
     * @param role      the face's role (cap RECEIVER, base TRANSMITTER)
     * @param installed whether that face holds a gasket being popped
     */
    private static void popFaceGasket(Level level, BlockPos pos, GasketRole role, boolean installed) {
        if (!installed) {
            return;
        }
        VatBlockEntity vat = level.getBlockEntity(pos) instanceof VatBlockEntity be ? be : null;
        GasketInstallation.popGasket(level, pos, true, vat == null ? null : vat.getGasketId(role));
        if (vat != null) {
            vat.clearGasket(role);
        }
    }
}
