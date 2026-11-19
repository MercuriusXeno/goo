package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nozzles shape the cone a sprayer throws its drips in, read through the launch each share of the cone takes
 * under a nozzle's shape: the relations the decisions state, never the angles, which are tuning values
 * (decisions choke-nozzle-narrows-the-cone, jet-nozzle-sprays-a-really-tight-stream,
 * sprinkler-nozzle-sprays-evenly-across-a-really-wide-cone, fan-nozzle-flattens-and-widens-the-cone).
 */
class SprayerNozzleTest {

    /** A residual's own cone, apex to rim, in degrees. */
    private static final double RESIDUAL_CONE = 15;
    private static final Vec3 LEVEL_LOOK = new Vec3(1, 0, 0);
    private static final int FEW_SHARES = 200;
    private static final int MANY_SHARES = 2000;
    private static final int RINGS = 4;
    private static final double FEWEST_IN_A_RING = 0.2;
    private static final double MOST_IN_A_RING = 0.3;
    private static final double WALL_DISTANCE = 3;
    /** Half the width of three cells, blocks. */
    private static final double THREE_CELLS_HALF = 1.5;

    @Test
    void chokeNarrowsAndJetNarrowsFurther() {
        double open = widest(SprayerNozzle.SPRAY, FEW_SHARES, SprayerNozzleTest::offTheLook);
        double choke = widest(SprayerNozzle.THIN, FEW_SHARES, SprayerNozzleTest::offTheLook);
        double jet = widest(SprayerNozzle.JET, FEW_SHARES, SprayerNozzleTest::offTheLook);

        assertTrue(choke < open, "choke " + choke + " should be narrower than the residual's own " + open);
        assertTrue(jet < choke, "jet " + jet + " should be narrower than the choke " + choke);
    }

    @Test
    void sprinklerSpraysWideAndEven() {
        double open = widest(SprayerNozzle.SPRAY, MANY_SHARES, SprayerNozzleTest::offTheLook);
        List<Vec3> launches = launches(SprayerNozzle.WIDE, MANY_SHARES);
        double rim = Math.toRadians(SprayerNozzle.WIDE.shape(RESIDUAL_CONE).sideDegrees() / 2);
        int[] inRing = new int[RINGS];
        for (Vec3 launch : launches) {
            double areaShare = Math.pow(offTheLook(launch) / rim, 2);
            inRing[Math.min(RINGS - 1, (int) (areaShare * RINGS))]++;
        }

        double sprinkler = widest(SprayerNozzle.WIDE, MANY_SHARES, SprayerNozzleTest::offTheLook);
        assertTrue(sprinkler > open, "sprinkler " + sprinkler + " should be wider than the residual's own " + open);
        for (int ring = 0; ring < RINGS; ring++) {
            double share = inRing[ring] / (double) MANY_SHARES;
            assertTrue(share >= FEWEST_IN_A_RING && share <= MOST_IN_A_RING,
                    "ring " + ring + " of equal area takes " + share + " of the drips");
        }
    }

    @Test
    void fanFlattensAndWidens() {
        double openAcross = widest(SprayerNozzle.SPRAY, FEW_SHARES, SprayerNozzleTest::sideways);
        double openUpDown = widest(SprayerNozzle.SPRAY, FEW_SHARES, SprayerNozzleTest::vertical);
        double fanAcross = widest(SprayerNozzle.LINE, FEW_SHARES, SprayerNozzleTest::sideways);
        double fanUpDown = widest(SprayerNozzle.LINE, FEW_SHARES, SprayerNozzleTest::vertical);

        assertTrue(fanAcross > openAcross, "the fan should be wider across, " + fanAcross + " to " + openAcross);
        assertTrue(fanUpDown < openUpDown, "the fan should be thinner up and down, " + fanUpDown + " to "
                + openUpDown);
    }

    /**
     * A line sprayed at a wall three blocks off spans at least three cells across: goo lays a cell a block, so a
     * narrower band reads as the plain spray's one or two cells.
     * operator report 2026-11-18: Line does not work
     */
    @Test
    void lineSpansThreeCellsAcrossAtThreeBlocks() {
        double halfAcross = widest(SprayerNozzle.LINE, FEW_SHARES, SprayerNozzleTest::sideways);
        assertTrue(Math.tan(halfAcross) * WALL_DISTANCE >= THREE_CELLS_HALF,
                "the line reaches " + Math.tan(halfAcross) * WALL_DISTANCE + " blocks either side at the wall");
    }

    /**
     * Wide lays twice the goo, Thin reaches farther than the plain spray and Jet farther still.
     * operator ruling 2026-11-18: Wide doubles the goo it puts down; Thin goes farther; Jet goes even farther
     */
    @Test
    void wideDoublesTheGooAndThinAndJetReachFarther() {
        assertEquals(2 * SprayerNozzle.SPRAY.dripScale(), SprayerNozzle.WIDE.dripScale());
        assertTrue(SprayerNozzle.THIN.reachScale() > SprayerNozzle.SPRAY.reachScale());
        assertTrue(SprayerNozzle.JET.reachScale() > SprayerNozzle.THIN.reachScale());
    }

    private static double widest(SprayerNozzle nozzle, int shares, ToDoubleFunction<Vec3> angle) {
        return launches(nozzle, shares).stream().mapToDouble(angle).max().orElse(0);
    }

    private static List<Vec3> launches(SprayerNozzle nozzle, int shares) {
        ConeShape shape = nozzle.shape(RESIDUAL_CONE);
        return IntStream.rangeClosed(1, shares).boxed()
                .flatMap(held -> CoatRay.scatteredShares(held, 1).stream())
                .map(share -> StreamCone.launchDirection(LEVEL_LOOK, shape, share.tilt(), share.turn()))
                .toList();
    }

    private static double offTheLook(Vec3 launch) {
        return Math.acos(Math.min(1, launch.normalize().dot(LEVEL_LOOK)));
    }

    private static double sideways(Vec3 launch) {
        return Math.abs(Math.atan2(launch.z, launch.x));
    }

    private static double vertical(Vec3 launch) {
        return Math.abs(Math.asin(launch.normalize().y));
    }
}
