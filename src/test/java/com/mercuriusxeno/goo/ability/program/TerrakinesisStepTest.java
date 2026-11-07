package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terrakinesis gathers its hunk from the struck block through joined
 * liftable blocks within the radius, and a left click hucks it
 * (decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity); it tears
 * free along its seams alone with a low stone-break
 * (decision terrakinesis-tears-free-with-a-stone-tear-and-seam-dust), only once
 * the wind-up has run, rumbling as it does
 * (decision terrakinesis-shakes-the-blocks-before-the-tear).
 */
class TerrakinesisStepTest {

    private static final BlockPos STRUCK = new BlockPos(0, 64, 0);
    private static final double RADIUS = 1.5;
    private static final TerrakinesisStep TERRAKINESIS = new TerrakinesisStep(6, RADIUS, 2.5, 3.5, 1.5);

    @Nested
    class HunkCells {

        @Test
        void gathersEveryJoinedLiftableBlockWithinTheRadius() {
            List<BlockPos> cells = TerrakinesisStep.hunkCells(STRUCK, RADIUS, pos -> true);
            assertEquals(STRUCK, cells.getFirst());
            // the 3x3x3 around the struck block, its 8 corners lying past 1.5
            assertEquals(19, cells.size());
            assertTrue(cells.stream().allMatch(cell -> cell.distSqr(STRUCK) <= RADIUS * RADIUS));
        }

        @Test
        void aBlockJoinedOnlyThroughAnUnliftableOneStays() {
            BlockPos gap = STRUCK.above();
            BlockPos beyond = gap.east();
            Set<BlockPos> liftable = Set.of(STRUCK, beyond);
            assertEquals(List.of(STRUCK), TerrakinesisStep.hunkCells(STRUCK, RADIUS, liftable::contains));
        }

        @Test
        void aWideRadiusStopsAtTheMostBlocks() {
            assertEquals(TerrakinesisStep.MOST_BLOCKS, TerrakinesisStep.hunkCells(STRUCK, 10, pos -> true).size());
        }
    }

    @Nested
    class Tear {

        /** Ground of stone up to y 64, air above. */
        private static boolean stoneGround(BlockPos pos) {
            return pos.getY() <= STRUCK.getY();
        }

        @Test
        void dustRisesFromTheSeamFacesAloneNeverBetweenLiftedCellsNorIntoTheAir() {
            BlockPos east = STRUCK.east();
            BlockPos below = STRUCK.below();
            List<BlockPos> hunk = List.of(STRUCK, east, below);
            Set<String> seams = TerrakinesisStep.seamFaces(hunk, TerrakinesisStepTest.Tear::stoneGround).stream()
                    .map(seam -> seam.cell().toShortString() + " " + seam.face()).collect(Collectors.toSet());
            Set<String> expected = Set.of(
                    face(STRUCK, Direction.WEST), face(STRUCK, Direction.NORTH), face(STRUCK, Direction.SOUTH),
                    face(east, Direction.DOWN), face(east, Direction.EAST), face(east, Direction.NORTH),
                    face(east, Direction.SOUTH),
                    face(below, Direction.DOWN), face(below, Direction.EAST), face(below, Direction.WEST),
                    face(below, Direction.NORTH), face(below, Direction.SOUTH));
            assertEquals(expected, seams);
        }

        private static String face(BlockPos cell, Direction face) {
            return cell.toShortString() + " " + face;
        }

        @Test
        void theTearIsADeepslateBreakPitchedLow() {
            assertEquals(Identifier.withDefaultNamespace("block.deepslate.break"), TerrakinesisStep.TEAR.sound());
            assertTrue(TerrakinesisStep.TEAR.pitch() < 1f, "pitch " + TerrakinesisStep.TEAR.pitch());
        }
    }

    @Nested
    class WindUp {

        @Test
        void theHunkTearsOnTheTickTheWindUpCompletesAndNotBefore() {
            assertTrue(TerrakinesisStep.windsUp(1));
            assertTrue(TerrakinesisStep.windsUp(TerrakinesisStep.WINDUP_TICKS - 1));
            assertFalse(TerrakinesisStep.windsUp(TerrakinesisStep.WINDUP_TICKS));
        }

        @Test
        void theRumbleRisesInPitchOverTheWindUp() {
            float lastPitch = 0f;
            int rumbles = 0;
            for (int held = 1; held < TerrakinesisStep.WINDUP_TICKS; held++) {
                SoundCue rumble = TerrakinesisStep.rumbleAt(held);
                if (rumble != null) {
                    assertTrue(rumble.pitch() > lastPitch, "rumble at " + held + " pitched " + rumble.pitch());
                    lastPitch = rumble.pitch();
                    rumbles++;
                }
            }
            assertTrue(rumbles >= 2, rumbles + " rumbles over the wind-up");
            assertEquals(null, TerrakinesisStep.rumbleAt(TerrakinesisStep.WINDUP_TICKS));
        }
    }

    @Test
    void aHeldHunkHangsWithItsMiddleOnTheLookAtTheFarDistanceWhenTheLookIsLevel() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        AABB hunkBox = new AABB(-0.5, -0.5, -0.5, 0.5, 1.5, 0.5);
        AABB holder = new AABB(0.2, 64, 0.2, 0.8, 65.8, 0.8);
        Vec3 position = TERRAKINESIS.holdPosition(eye, new Vec3(1, 0, 0), 0f, hunkBox, holder);
        Vec3 middle = position.add(hunkBox.getCenter());
        assertEquals(eye.x + 3.5, middle.x, 1e-9);
        assertEquals(eye.y, middle.y, 1e-9);
        assertEquals(eye.z, middle.z, 1e-9);
    }

    @Test
    void lookingStraightDownPushesTheHunkOutUntilItClearsTheHolder() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        AABB hunkBox = new AABB(-1.5, -1, -1.5, 1.5, 1, 1.5);
        AABB holder = new AABB(0.2, 64, 0.2, 0.8, 65.8, 0.8);
        Vec3 position = TERRAKINESIS.holdPosition(eye, new Vec3(0, -1, 0), 90f, hunkBox, holder);
        assertFalse(hunkBox.move(position).intersects(holder), "the hunk at " + position + " overlaps its holder");
    }

    @Test
    void aLeftClickHucksTheHeldHunk() {
        assertTrue(GrabStep.throwsOnAttack(true, List.of(TERRAKINESIS)));
    }
}
