package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
