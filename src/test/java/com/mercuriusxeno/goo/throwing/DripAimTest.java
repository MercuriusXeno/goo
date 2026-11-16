package com.mercuriusxeno.goo.throwing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spray's center drip leaves the glove hand, off the sight line, and
 * still strikes the block under the crosshair: its launch axis bends from
 * the hand onto the crosshair's point through the arc gravity and drag
 * give it, and falls back to the look where no launch at the exit speed
 * reaches that point.
 * diagnose-and-correct-the-spray-landing-off-the-aim
 */
class DripAimTest {

    /** Goop's and Slick's shipped throw speed, a caster at rest's exit speed, blocks per tick. */
    private static final double EXIT_SPEED = 0.84;
    /** A standing player's eye, a block's middle over a floor at y 0, looking east. */
    private static final Vec3 EYE = new Vec3(0.5, 1.62, 0.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /**
     * The first-person glove hand off the eye of a player looking east: ahead, below and to the right,
     * which is south while looking east.
     */
    private static final Vec3 HAND = EYE.add(0.6, -0.35, 0.4);
    private static final double WORLD_FLOOR = -64;
    private static final DripArc.Airspace INSIDE_THE_WORLD = point -> point.y >= WORLD_FLOOR;

    /**
     * The blocks a drip may strike: one solid block standing on a floor whose top lies at y 0.
     *
     * @param solid the standing block
     * @return the clip
     */
    private static CoatRay.BlockClip blockOnAFloor(BlockPos solid) {
        AABB box = new AABB(solid);
        return (from, to) -> {
            // the face struck plays no part in which block the drip strikes
            Optional<CoatRay.BlockStrike> onTheBlock = box.clip(from, to)
                    .map(point -> new CoatRay.BlockStrike(point, solid, Direction.UP));
            if (onTheBlock.isPresent()) {
                return onTheBlock;
            }
            if (from.y < 0 || to.y >= 0) {
                return Optional.empty();
            }
            Vec3 point = from.add(to.subtract(from).scale(from.y / (from.y - to.y)));
            return Optional.of(new CoatRay.BlockStrike(point, BlockPos.containing(point.x, -1, point.z),
                    Direction.UP));
        };
    }

    /**
     * Where the center drip strikes, launched from the hand along the axis aimed at the crosshair's point.
     *
     * @param crosshair the point the crosshair lands on
     * @param clip      the blocks the drip may strike
     * @return the struck block
     */
    private static CoatRay.BlockStrike centerDripStrikes(Vec3 crosshair, CoatRay.BlockClip clip) {
        Vec3 axis = DripArc.axisToward(HAND, crosshair, crosshair.subtract(EYE).normalize(), EXIT_SPEED);
        DripArc.Step end = DripArc.flyToItsEnd(HAND, axis.scale(EXIT_SPEED), clip, INSIDE_THE_WORLD);
        assertTrue(end.strike().isPresent(), "the center drip should strike a block, ends at " + end.position());
        return end.strike().get();
    }

    private static void assertStrikes(BlockPos aimed, Vec3 crosshair) {
        CoatRay.BlockStrike struck = centerDripStrikes(crosshair, blockOnAFloor(aimed));
        assertEquals(aimed, struck.block(), "the center drip should strike the block under the crosshair at "
                + crosshair + ", strikes " + struck.block() + " at " + struck.point());
    }

    @Test
    void theCenterDripFromTheHandStrikesAnEyeLevelBlockThreeBlocksAway() {
        assertStrikes(new BlockPos(3, 1, 0), new Vec3(3, EYE.y, EYE.z));
    }

    @Test
    void theCenterDripFromTheHandStrikesAnEyeLevelBlockSevenBlocksAway() {
        assertStrikes(new BlockPos(7, 1, 0), new Vec3(7, EYE.y, EYE.z));
    }

    @Test
    void theCenterDripFromTheHandStrikesTheTopOfABlockFourBlocksAway() {
        assertStrikes(new BlockPos(4, 0, 0), new Vec3(4.5, 1, EYE.z));
    }

    @Test
    void aTargetNoLaunchAtTheExitSpeedReachesTakesTheLook() {
        Vec3 look = new Vec3(1, 0.2, 0).normalize();
        Vec3 farAndHigh = HAND.add(60, 30, 0);

        Vec3 axis = DripArc.axisToward(HAND, farAndHigh, look, EXIT_SPEED);

        assertEquals(look, axis, "a target out of the throw's reach should leave the axis on the look");
    }

    @Test
    void axisLeavesTheLookForATargetOffTheSightLineFromTheHand() {
        Vec3 crosshair = new Vec3(3, EYE.y, EYE.z);

        Vec3 axis = DripArc.axisToward(HAND, crosshair, EAST, EXIT_SPEED);

        assertTrue(axis.z < 0, "the axis should turn from the right-hand glove back toward the crosshair, reads "
                + axis);
    }
}
