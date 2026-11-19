package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
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

    @Test
    void chokeNarrowsAndJetNarrowsFurther() {
        double open = widest(SprayerNozzle.OPEN, FEW_SHARES, SprayerNozzleTest::offTheLook);
        double choke = widest(SprayerNozzle.CHOKE, FEW_SHARES, SprayerNozzleTest::offTheLook);
        double jet = widest(SprayerNozzle.JET, FEW_SHARES, SprayerNozzleTest::offTheLook);

        assertTrue(choke < open, "choke " + choke + " should be narrower than the residual's own " + open);
        assertTrue(jet < choke, "jet " + jet + " should be narrower than the choke " + choke);
    }

    @Test
    void sprinklerSpraysWideAndEven() {
        double open = widest(SprayerNozzle.OPEN, MANY_SHARES, SprayerNozzleTest::offTheLook);
        List<Vec3> launches = launches(SprayerNozzle.SPRINKLER, MANY_SHARES);
        double rim = Math.toRadians(SprayerNozzle.SPRINKLER.shape(RESIDUAL_CONE).sideDegrees() / 2);
        int[] inRing = new int[RINGS];
        for (Vec3 launch : launches) {
            double areaShare = Math.pow(offTheLook(launch) / rim, 2);
            inRing[Math.min(RINGS - 1, (int) (areaShare * RINGS))]++;
        }

        double sprinkler = widest(SprayerNozzle.SPRINKLER, MANY_SHARES, SprayerNozzleTest::offTheLook);
        assertTrue(sprinkler > open, "sprinkler " + sprinkler + " should be wider than the residual's own " + open);
        for (int ring = 0; ring < RINGS; ring++) {
            double share = inRing[ring] / (double) MANY_SHARES;
            assertTrue(share >= FEWEST_IN_A_RING && share <= MOST_IN_A_RING,
                    "ring " + ring + " of equal area takes " + share + " of the drips");
        }
    }

    @Test
    void fanFlattensAndWidens() {
        double openAcross = widest(SprayerNozzle.OPEN, FEW_SHARES, SprayerNozzleTest::sideways);
        double openUpDown = widest(SprayerNozzle.OPEN, FEW_SHARES, SprayerNozzleTest::vertical);
        double fanAcross = widest(SprayerNozzle.FAN, FEW_SHARES, SprayerNozzleTest::sideways);
        double fanUpDown = widest(SprayerNozzle.FAN, FEW_SHARES, SprayerNozzleTest::vertical);

        assertTrue(fanAcross > openAcross, "the fan should be wider across, " + fanAcross + " to " + openAcross);
        assertTrue(fanUpDown < openUpDown, "the fan should be thinner up and down, " + fanUpDown + " to "
                + openUpDown);
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
