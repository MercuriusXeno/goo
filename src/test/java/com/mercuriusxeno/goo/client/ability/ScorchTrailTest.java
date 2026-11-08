package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sunbeam's scorch trail takes each spot the ray burns, ages every mark on
 * the game clock and drops it once the fade time has passed
 * (decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades), and
 * draws a mark only where the block it burns on stands.
 */
class ScorchTrailTest {

    private static final Vec3 SPOT = new Vec3(4.5, 64.0, -2.5);
    private static final double START = 1000;
    private static final double TOLERANCE = 1e-9;

    private static ScorchTrail burntOnceAt(double now) {
        ScorchTrail trail = new ScorchTrail();
        trail.burn(SPOT, Direction.UP, now);
        return trail;
    }

    @Nested
    class Aging {

        @Test
        void aFreshMarkBurnsWhiteHot() {
            ScorchTrail trail = burntOnceAt(START);
            assertEquals(1, trail.marks().getFirst().heat(START), TOLERANCE);
        }

        @Test
        void aMarkCoolsHalfwayAtHalfTheFadeTime() {
            ScorchTrail trail = burntOnceAt(START);
            assertEquals(0.5, trail.marks().getFirst().heat(START + ScorchTrail.FADE_TICKS / 2), TOLERANCE);
        }

        @Test
        void aMarkTheBeamHoldsOnStaysWhiteHot() {
            ScorchTrail trail = burntOnceAt(START);
            trail.burn(SPOT, Direction.UP, START + ScorchTrail.FADE_TICKS / 2);
            double now = START + ScorchTrail.FADE_TICKS / 2;
            assertEquals(1, trail.marks().getLast().heat(now), TOLERANCE);
        }
    }

    @Nested
    class Expiry {

        @Test
        void aMarkStandsUntilTheFadeTime() {
            ScorchTrail trail = burntOnceAt(START);
            trail.expire(START + ScorchTrail.FADE_TICKS - 1);
            assertEquals(1, trail.marks().size());
        }

        @Test
        void aMarkDropsOnceTheFadeTimePasses() {
            ScorchTrail trail = burntOnceAt(START);
            List<ScorchTrail.Mark> burnt = trail.marks();
            trail.expire(START + ScorchTrail.FADE_TICKS);
            assertEquals(1, burnt.size());
            assertEquals(List.of(), trail.marks());
        }

        @Test
        void theOlderEndOfATrailDropsFirst() {
            ScorchTrail trail = burntOnceAt(START);
            Vec3 later = SPOT.add(1, 0, 0);
            trail.burn(later, Direction.UP, START + 20);
            trail.expire(START + ScorchTrail.FADE_TICKS + 1);
            assertTrue(trail.marks().stream().allMatch(mark -> mark.bornAt() > START));
            assertEquals(later, trail.marks().getLast().point());
        }
    }

    @Nested
    class Standing {

        /** The block SPOT's mark burns on: the one under its upward face. */
        private static final BlockPos UNDER_THE_SPOT = new BlockPos(4, 63, -3);
        private static final int GAP_WIDTH = 2;

        @Test
        void aMarkDrawsWhileItsBlockStands() {
            ScorchTrail trail = burntOnceAt(START);
            assertEquals(trail.marks(), trail.marksOnBlocks(UNDER_THE_SPOT::equals));
        }

        @Test
        void aMarkWhoseBlockIsGoneDrawsNot() {
            ScorchTrail trail = burntOnceAt(START);
            assertEquals(List.of(), trail.marksOnBlocks(pos -> false));
        }

        @Test
        void aRakeAcrossAGapDrawsNoMarkOverTheGap() {
            ScorchTrail trail = burntOnceAt(START);
            trail.burn(SPOT.add(GAP_WIDTH, 0, 0), Direction.UP, START + 1);
            BlockPos gap = UNDER_THE_SPOT.east();
            List<ScorchTrail.Mark> drawn = trail.marksOnBlocks(pos -> !pos.equals(gap));
            assertTrue(drawn.stream().noneMatch(mark -> mark.block().equals(gap)), "a mark drawn over the gap");
            assertTrue(drawn.stream().anyMatch(mark -> mark.block().equals(UNDER_THE_SPOT)));
            assertTrue(drawn.stream().anyMatch(mark -> mark.block().equals(gap.east())));
        }
    }

    @Nested
    class Trailing {

        @Test
        void aRakeAcrossOneFaceLeavesAnUnbrokenTrail() {
            ScorchTrail trail = burntOnceAt(START);
            trail.burn(SPOT.add(1, 0, 0), Direction.UP, START + 1);
            List<ScorchTrail.Mark> marks = trail.marks();
            for (int i = 1; i < marks.size(); i++) {
                assertTrue(marks.get(i).point().distanceTo(marks.get(i - 1).point()) <= ScorchTrail.MARK_SPACING
                        + TOLERANCE, "a gap between marks " + (i - 1) + " and " + i);
            }
            assertEquals(SPOT.add(1, 0, 0), marks.getLast().point());
        }

        @Test
        void aJumpToAnotherFaceBridgesNothing() {
            ScorchTrail trail = burntOnceAt(START);
            trail.burn(SPOT.add(0, 0.5, 0.5), Direction.NORTH, START + 1);
            assertEquals(2, trail.marks().size());
        }
    }
}
