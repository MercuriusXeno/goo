package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.network.HoldMarks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vacuum's wave and reach: a residual cell breaks on a tick its distance from
 * the eye sets, the reach's far end about 10 ticks in, and the hold reaches
 * every residual inside its cone that the eye sees past other residuals
 * (decision vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave).
 */
class VacuumStepTest {

    private static final double SHIPPED_RANGE = 8;
    private static final double CONE = 40;
    /** The eye, looking east along x across the reach. */
    private static final Vec3 EYE = new Vec3(0.5, 2.5, 0.5);
    private static final Vec3 LOOK_EAST = new Vec3(SHIPPED_RANGE, 0, 0);

    @Nested
    class Wave {

        private final double ticksPerBlock = shippedVacuum().ticksPerBlock();

        @Test
        void aFartherCellBreaksOnALaterTick() {
            assertTrue(VacuumStep.breaksOn(2, ticksPerBlock) < VacuumStep.breaksOn(5, ticksPerBlock));
            assertTrue(VacuumStep.breaksOn(5, ticksPerBlock) < VacuumStep.breaksOn(SHIPPED_RANGE, ticksPerBlock));
        }

        @Test
        void theReachsFarEndBreaksTenTicksIn() {
            assertEquals(10, VacuumStep.breaksOn(SHIPPED_RANGE, ticksPerBlock));
        }

        private static VacuumStep shippedVacuum() {
            AbilityDefinition vacuum = AbilityJson.decode("void_vacuum");
            return vacuum.behaviors().stream().filter(VacuumStep.class::isInstance).map(VacuumStep.class::cast)
                    .findFirst().orElseThrow();
        }
    }

    /**
     * A reached residual decays toward nothing through the mingle noise before
     * it breaks, its decay ending on the tick its distance sets
     * (decision vacuumed-residuals-decay-through-mingle-noise-before-breaking).
     */
    @Nested
    class Decay {

        private static final int DECAY_TICKS = 5;
        private static final BlockPos CELL = new BlockPos(4, 2, 0);
        private final VacuumStep vacuum = new VacuumStep(1.25, DECAY_TICKS);

        @Test
        void aFarCellsDecayEndsOnTheTickItsDistanceSets() {
            int breaksOn = VacuumStep.breaksOn(SHIPPED_RANGE, 1.25);

            assertEquals(breaksOn, vacuum.decayStartsOn(SHIPPED_RANGE) + DECAY_TICKS - 1);
        }

        @Test
        void aNearCellDecaysFromTheHoldsFirstTick() {
            assertEquals(ChannelAim.FIRST_TICK, vacuum.decayStartsOn(1));
        }

        @Test
        void aReachedResidualMinglesTowardNothingAsItMeltsAway() {
            ChannelHost host = residualHost(0.2f);

            vacuum.tick(new StepContext(host, 0, 0));

            verify(host).exposeBlock(CELL, Blocks.AIR.defaultBlockState(), 1f / DECAY_TICKS, BlockExposures.MELTS_AWAY);
        }

        @Test
        void aFullyDecayedResidualBreaksAndSendsItsTransformToNothing() {
            ChannelHost host = residualHost(1f);
            BlockState residual = host.stateAt(CELL);

            vacuum.tick(new StepContext(host, 0, 0));

            verify(host).breakBlock(CELL);
            verify(host).sendTransform(CELL, residual, Blocks.AIR.defaultBlockState());
        }

        /**
         * A host holding one residual cell four blocks ahead of the eye, the
         * hold old enough to have reached it, its exposure answering a share.
         */
        private ChannelHost residualHost(float share) {
            ChannelHost host = mock(ChannelHost.class);
            BlockState residual = mock(BlockState.class);
            Block residualBlock = mock(Block.class);
            when(residual.getBlock()).thenReturn(residualBlock);
            when(residual.getCollisionShape(any(), any())).thenReturn(Shapes.empty());
            when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(EYE.add(LOOK_EAST), null, CONE, 20, true)));
            when(host.eye()).thenReturn(EYE);
            when(host.holdMarks()).thenReturn(new HoldMarks());
            when(host.blockIn(any(), eq(VacuumStep.SLIME_RESIDUALS))).thenAnswer(call -> CELL.equals(call.getArgument(0)));
            when(host.stateAt(any())).thenReturn(residual);
            when(host.exposeBlock(any(), any(), anyFloat(), anyInt())).thenReturn(share);
            return host;
        }
    }

    @Nested
    class Reach {

        @Test
        void aResidualAheadInsideTheConeIsReached() {
            BlockPos ahead = new BlockPos(4, 2, 0);

            assertEquals(List.of(ahead), reached(Set.of(ahead), Set.of()));
        }

        @Test
        void aResidualBehindTheEyeIsNotReached() {
            assertEquals(List.of(), reached(Set.of(new BlockPos(-3, 2, 0)), Set.of()));
        }

        @Test
        void aResidualPastTheReachIsNotReached() {
            assertEquals(List.of(), reached(Set.of(new BlockPos(10, 2, 0)), Set.of()));
        }

        @Test
        void aResidualOutsideTheConesAngleIsNotReached() {
            // four ahead and three aside stands near 37 degrees off the look, past the cone's 20
            assertEquals(List.of(), reached(Set.of(new BlockPos(4, 2, 3)), Set.of()));
        }

        @Test
        void aResidualBehindASolidBlockIsNotReached() {
            assertEquals(List.of(), reached(Set.of(new BlockPos(6, 2, 0)), Set.of(new BlockPos(3, 2, 0))));
        }

        @Test
        void aResidualBehindAnotherResidualIsReached() {
            BlockPos near = new BlockPos(2, 2, 0);
            BlockPos far = new BlockPos(6, 2, 0);

            assertEquals(List.of(near, far), reached(Set.of(far, near), Set.of()));
        }

        @Test
        void reachedResidualsRunNearestFirst() {
            BlockPos near = new BlockPos(2, 2, 0);
            BlockPos middle = new BlockPos(4, 2, 0);
            BlockPos far = new BlockPos(7, 2, 0);

            assertEquals(List.of(near, middle, far), reached(Set.of(far, near, middle), Set.of()));
        }

        private static List<BlockPos> reached(Set<BlockPos> residuals, Set<BlockPos> solid) {
            Predicate<BlockPos> passable = pos -> !solid.contains(pos);
            return VacuumStep.residualsInCone(EYE, LOOK_EAST, CONE, residuals::contains, passable);
        }
    }
}
