package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import com.mercuriusxeno.goo.ability.program.BoreStep;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drawn drill extends from Bore's blob, wherever it has eased to, to
 * its point: where the sight meets the first breakable
 * block within reach, or the end of reach where it meets none. It narrows
 * from a wide mouth at the blob to the point radius Bore breaks with, never
 * wider than a block
 * (decisions bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand,
 * bore-drill-point-floats-along-the-crosshair-to-the-aimed-block, bore-shows-a-big-drill-from-the-glove).
 */
class BoreDrillTest {

    /** A survival player's block reach. */
    private static final double REACH = 4.5;
    /** rock_bore.json's point radius. */
    private static final double POINT_RADIUS = 0.1;
    private static final double SLACK = 1e-9;
    private static final double HALF_A_BLOCK = 0.5;
    /** An eye in the middle of a block's face height, looking along +Z, level. */
    private static final Vec3 EYE = new Vec3(3.5, 70.5, -8.5);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final CameraEye.Sight SIGHT = new CameraEye.Sight(EYE, LOOK);
    /** Yaw 0 looks along +Z. */
    private static final float YAW = 0f;
    /** Where the lifted blob rests by a glove in the right hand: right is -X facing +Z. */
    private static final Vec3 RIGHT_HAND_BLOB = EYE.add(-0.4, -0.35, 0.6);
    private static final int SAMPLES = 64;
    private static final Predicate<BlockPos> NOTHING = cell -> false;

    private static Vec3 farEndOf(ConeSections.Volume volume) {
        return volume.apex().add(volume.axis().scale(volume.range()));
    }

    /**
     * The drill extends from the blob wherever it has eased to: its mouth at
     * the blob's place and its far end at the point
     * (decisions bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand,
     * bore-blob-eases-to-place-the-drill-at-the-crosshair).
     */
    @Nested
    class TheMouth {

        @Test
        void itStandsAtTheBlobsEasedPlaceAndItsFarEndAtThePoint() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, BoreDrillShape.SHIPPED, true, NOTHING.negate(), NOTHING);
            Vec3 eased = BoreBlobPlace.placeFor(EYE, LOOK, point, RIGHT_HAND_BLOB, 2, 22.5);
            ConeSections.Volume drill = BoreDrill.volume(eased, point, YAW, POINT_RADIUS);
            assertTrue(eased.distanceTo(RIGHT_HAND_BLOB) > 0.5, "the place stands at the glove's rest");
            assertEquals(eased, drill.apex());
            assertEquals(0, farEndOf(drill).distanceTo(point), 1e-6);
        }
    }

    @Nested
    class ThePoint {

        @Test
        void itStandsWhereTheSightMeetsABreakableBlockTwoBlocksOut() {
            BlockPos stone = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, BoreDrillShape.SHIPPED, true, cell -> !cell.equals(stone),
                    stone::equals);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(0, farEndOf(drill).distanceTo(new Vec3(EYE.x, EYE.y, stone.getZ())), 1e-6);
        }

        @Test
        void itStandsAtTheFaceOfASolidBlockOutsideTheTagTwoBlocksOut() {
            BlockPos bedrock = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, BoreDrillShape.SHIPPED, true, cell -> !cell.equals(bedrock),
                    NOTHING);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(0, farEndOf(drill).distanceTo(new Vec3(EYE.x, EYE.y, bedrock.getZ())), 1e-6);
        }

        @Test
        void itStandsAtTheEndOfReachWhereTheSightMeetsNoBreakableBlock() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, BoreDrillShape.SHIPPED, true, NOTHING.negate(), NOTHING);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(0, farEndOf(drill).distanceTo(EYE.add(LOOK.scale(REACH))), 1e-6);
        }
    }

    /**
     * The drill never passes into a block Bore is not drilling: every solid
     * block its drawn body overlaps is one the bore's pick holds
     * (decisions bore-drill-point-floats-along-the-crosshair-to-the-aimed-block,
     * bore-breaks-what-is-in-the-vortex).
     */
    @Nested
    class TheBlocksItEnters {

        private static final int AROUND = 16;

        /** Every cell the drawn drill's body overlaps, sampled along it and round its rim. */
        private static Set<BlockPos> cellsEntered(ConeSections.Volume drill) {
            Set<BlockPos> cells = new HashSet<>();
            Vec3 side = Math.abs(drill.axis().y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 across = drill.axis().cross(side).normalize();
            Vec3 lift = drill.axis().cross(across).normalize();
            for (int sample = 0; sample < SAMPLES; sample++) {
                double distance = drill.range() * sample / SAMPLES;
                double radius = drill.radiusAt().applyAsDouble(distance) * 0.95;
                Vec3 middle = drill.apex().add(drill.axis().scale(distance));
                cells.add(BlockPos.containing(middle));
                for (int turn = 0; turn < AROUND; turn++) {
                    double angle = 2 * Math.PI * turn / AROUND;
                    cells.add(BlockPos.containing(middle.add(across.scale(Math.cos(angle) * radius))
                            .add(lift.scale(Math.sin(angle) * radius))));
                }
            }
            return cells;
        }

        private static Set<BlockPos> undrilledEntered(Vec3 blob, CameraEye.Sight sight, Predicate<BlockPos> solid,
                                                      Predicate<BlockPos> breakable) {
            Predicate<BlockPos> air = solid.negate();
            Vec3 point = BoreDrill.pointOf(sight, REACH, BoreDrillShape.SHIPPED, true, air, breakable);
            ChannelAim aim = new ChannelAim(sight.eye().add(sight.look().scale(REACH)), null, 0);
            Set<BlockPos> picked = Set.copyOf(BoreStep.progressing(sight.eye(), BoreDrillShape.SHIPPED, true, aim, air,
                    breakable));
            Set<BlockPos> entered = cellsEntered(BoreDrill.volume(blob, point, YAW, POINT_RADIUS));
            return entered.stream().filter(solid).filter(cell -> !picked.contains(cell)).collect(Collectors.toSet());
        }

        @Test
        void itStopsAtASolidBlockItCannotBreak() {
            BlockPos bedrock = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            assertEquals(Set.of(), undrilledEntered(RIGHT_HAND_BLOB, SIGHT, bedrock::equals, NOTHING));
        }
    }

    /** The vortex builds up when the bore starts and disperses after it ends, rather than switching. */
    @Nested
    class Presence {

        @Test
        void itBuildsUpOverTheFadeInWhileHeld() {
            assertEquals(0.5, BoreDrill.presenceAfter(0, true, BoreDrill.FADE_IN_TICKS / 2), SLACK);
            assertEquals(1, BoreDrill.presenceAfter(0.5, true, BoreDrill.FADE_IN_TICKS), SLACK);
        }

        @Test
        void itDispersesOverTheFadeOutOnceReleased() {
            assertEquals(0.5, BoreDrill.presenceAfter(1, false, BoreDrill.FADE_OUT_TICKS / 2), SLACK);
            assertEquals(0, BoreDrill.presenceAfter(0.5, false, BoreDrill.FADE_OUT_TICKS), SLACK);
        }
    }

    @Nested
    class Radius {

        @Test
        void itNarrowsFromItsMouthAtTheBlobToThePointRadiusAndIsNeverWiderThanABlock() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, BoreDrillShape.SHIPPED, true, NOTHING.negate(), NOTHING);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(POINT_RADIUS, drill.radiusAt().applyAsDouble(drill.range()), SLACK);
            double previous = Double.MAX_VALUE;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double distance = drill.range() * sample / SAMPLES;
                double radius = drill.radiusAt().applyAsDouble(distance);
                assertTrue(radius <= previous, "the drill widens to " + radius + " at " + distance);
                assertTrue(radius <= HALF_A_BLOCK, "the drill is " + 2 * radius + " wide at " + distance);
                previous = radius;
            }
            assertTrue(drill.radiusAt().applyAsDouble(0) > POINT_RADIUS, "the drill's mouth is no wider than its point");
        }
    }
}
