package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.canister.ICanisterAttachable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** CanisterPlacementValidator.isSupportedBelow: the block below supports a canister when its top covers the central 12x12 pixels, or when it is an attachable machine with capacity. */
class CanisterPlacementValidatorTest {

    private static final double PIXEL = 1.0 / 16.0;
    private static final BlockPos BELOW = new BlockPos(0, 64, 0);

    private static VoxelShape pixels(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Shapes.box(minX * PIXEL, minY * PIXEL, minZ * PIXEL, maxX * PIXEL, maxY * PIXEL, maxZ * PIXEL);
    }

    private static Level levelWithSupportShape(VoxelShape supportShape, BlockEntity blockEntity) {
        Level level = mock(Level.class);
        BlockState state = mock(BlockState.class);
        when(state.getBlockSupportShape(level, BELOW)).thenReturn(supportShape);
        when(level.getBlockState(BELOW)).thenReturn(state);
        when(level.getBlockEntity(BELOW)).thenReturn(blockEntity);
        return level;
    }

    private static BlockEntity attachable(boolean canAttachOnTop) {
        BlockEntity machine = mock(BlockEntity.class, withSettings().extraInterfaces(ICanisterAttachable.class));
        when(((ICanisterAttachable) machine).canAttachOnTop()).thenReturn(canAttachOnTop);
        return machine;
    }

    @Nested
    class TopFaceShape {

        @Test
        void fullCubeSupports() {
            assertTrue(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(Shapes.block(), null), BELOW));
        }

        @Test
        void vatInsetOnePixelSupports() {
            VoxelShape vat = pixels(1, 0, 1, 15, 16, 15);
            assertTrue(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(vat, null), BELOW));
        }

        @Test
        void topSlabSupports() {
            VoxelShape topSlab = pixels(0, 8, 0, 16, 16, 16);
            assertTrue(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(topSlab, null), BELOW));
        }

        @Test
        void footprintExactlyCoveredSupports() {
            VoxelShape footprint = pixels(2, 0, 2, 14, 16, 14);
            assertTrue(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(footprint, null), BELOW));
        }

        @Test
        void topOnePixelShortOfFootprintRefuses() {
            VoxelShape shortTop = pixels(3, 0, 2, 14, 16, 14);
            assertFalse(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(shortTop, null), BELOW));
        }

        @Test
        void crucibleHollowRimRefuses() {
            VoxelShape crucible = Shapes.or(pixels(0, 0, 0, 16, 13, 16),
                    pixels(2, 13, 2, 14, 16, 4), pixels(2, 13, 12, 14, 16, 14),
                    pixels(2, 13, 4, 4, 16, 12), pixels(12, 13, 4, 14, 16, 12));
            assertFalse(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(crucible, null), BELOW));
        }

        @Test
        void signPostTopRefuses() {
            VoxelShape signPost = pixels(4, 0, 4, 12, 16, 12);
            assertFalse(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(signPost, null), BELOW));
        }

        @Test
        void bottomSlabRefuses() {
            VoxelShape bottomSlab = pixels(0, 0, 0, 16, 8, 16);
            assertFalse(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(bottomSlab, null), BELOW));
        }

        @Test
        void emptyShapeRefuses() {
            assertFalse(CanisterPlacementValidator.isSupportedBelow(levelWithSupportShape(Shapes.empty(), null), BELOW));
        }
    }

    @Nested
    class AttachableMachine {

        @Test
        void attachableWithCapacitySupportsOverEmptyShape() {
            Level level = levelWithSupportShape(Shapes.empty(), attachable(true));
            assertTrue(CanisterPlacementValidator.isSupportedBelow(level, BELOW));
        }

        @Test
        void attachableWithoutCapacityRefusesOverEmptyShape() {
            Level level = levelWithSupportShape(Shapes.empty(), attachable(false));
            assertFalse(CanisterPlacementValidator.isSupportedBelow(level, BELOW));
        }
    }
}
