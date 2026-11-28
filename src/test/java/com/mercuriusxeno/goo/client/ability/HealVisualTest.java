package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.client.particle.VitalFogParticle;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Heal's fog fills the cone it is handed from the glove along the aim, its edge undulating in a wave rolling outward
 * and never leaning past the rim (decisions vitality-waves-regenerate-and-court,
 * spray-abilities-render-procedurally-bound-to-their-cone).
 */
class HealVisualTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 APEX = new Vec3(1, 2, 3);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double RANGE = 6;
    private static final double CONE = 50;
    private static final double HALF_ANGLE = Math.toRadians(CONE / 2);
    private static final SprayCone ROUND = new SprayCone(APEX, EAST, ConeShape.round(CONE), RANGE);
    private static final Vec3 FEET = new Vec3(10, 64, 10);
    private static final double STAR_HEIGHT = 1.8;
    private static final float STAR_YAW = 60f;
    private static final long SEED = 4717;
    private static final int TICKS = 400;

    /**
     * A cone wider across than up and down draws its fog inside it and its reach
     * (decision spray-abilities-render-procedurally-bound-to-their-cone).
     */
    @Test
    void everyFogPuffDrawnIntoAFlatConeStandsInsideItAndItsReach() {
        SprayCone flat = new SprayCone(APEX, new Vec3(0.3, -0.2, 1).normalize(), new ConeShape(60, 8, false), 7);
        RandomSource random = RandomSource.create(SEED);
        for (int tick = 0; tick < TICKS; tick++) {
            for (Vec3 puff : HealVisual.fogPuffs(flat, random, tick)) {
                assertTrue(StreamCone.contains(flat.apex(), flat.axis(), flat.reach(), flat.shape(), puff),
                        "a puff at tick " + tick + " stands outside at " + puff);
            }
        }
    }

    /**
     * Every puff drawn into a cone wider across than up and down stays inside it and its reach its whole life,
     * drifting out along the aim (decision spray-abilities-render-procedurally-bound-to-their-cone).
     */
    @Test
    void everyFogPuffDriftsItsWholeLifeInsideAFlatConeAndItsReach() {
        SprayCone flat = new SprayCone(APEX, new Vec3(0.3, -0.2, 1).normalize(), new ConeShape(60, 8, false), 7);
        RandomSource random = RandomSource.create(SEED);
        for (int tick = 0; tick < TICKS; tick++) {
            for (Vec3 puff : HealVisual.fogPuffs(flat, random, tick)) {
                Vec3 drift = HealVisual.fogDrift(flat, puff);
                for (int age = 0; age <= VitalFogParticle.LONGEST_LIFE; age++) {
                    Vec3 at = VitalFogParticle.driftedTo(puff, drift, age);
                    assertTrue(StreamCone.contains(flat.apex(), flat.axis(), flat.reach(), flat.shape(), at),
                            "a puff spawned at tick " + tick + " at " + puff + " stands outside at its age " + age
                                    + ": " + at);
                }
            }
        }
    }

    private static double offTheAim(Vec3 point) {
        return Math.acos(Math.min(1, point.subtract(APEX).normalize().dot(EAST)));
    }

    @Test
    void fogAtTheStartOfTheConeSitsOnTheGlove() {
        assertEquals(APEX, HealVisual.fogPoint(ROUND, 0, 1, 0.2, 0));
    }

    @Test
    void fogOnTheAxisRunsAlongTheAim() {
        Vec3 at = HealVisual.fogPoint(ROUND, 0.5, 0, 0, 0);
        assertEquals(new Vec3(4, 2, 3), at);
    }

    /** Halfway out at time 0 the undulation's trough leans the rim's fog three quarters of the half angle out. */
    @Test
    void theTroughLeansTheRimsFogShyOfTheCone() {
        Vec3 at = HealVisual.fogPoint(ROUND, 0.5, 1, 0, 0);
        assertEquals(HALF_ANGLE * 0.75, offTheAim(at), DELTA);
        assertEquals(RANGE * 0.5, at.distanceTo(APEX), DELTA);
    }

    /** A sixth of the way out at time 0 the undulation's crest would swell the rim a quarter past; it holds at the rim. */
    @Test
    void theCrestHoldsTheRimsFogAtTheCone() {
        Vec3 at = HealVisual.fogPoint(ROUND, 1.0 / 6, 1, 0, 0);
        assertEquals(HALF_ANGLE, offTheAim(at), DELTA);
    }

    /** At its crest the undulation swells the rim by a quarter, at its trough it shrinks it by a quarter. */
    @Test
    void theUndulationSwellsTheRimByAQuarterAtItsCrestAndShrinksItByAQuarterAtItsTrough() {
        assertEquals(1.25, HealVisual.undulation(1.0 / 6, 0), DELTA);
        assertEquals(0.75, HealVisual.undulation(0.5, 0), DELTA);
    }

    @Test
    void theUndulationRollsOutwardOverTime() {
        double along = 0.4;
        double later = along + HealVisual.ROLL_PER_TICK / (2 * Math.PI * HealVisual.WAVES_ALONG);
        assertEquals(HealVisual.undulation(along, 0), HealVisual.undulation(later, 1), DELTA);
    }

    @Test
    void anAimStraightUpStillOpensACone() {
        Vec3 at = HealVisual.fogPoint(new SprayCone(APEX, new Vec3(0, 1, 0), ConeShape.round(CONE), RANGE), 1, 1, 0, 0);
        assertTrue(at.subtract(APEX.add(0, RANGE, 0)).length() > 0);
    }

    /**
     * A first-person caster facing yaw 60 sees a star on the lowest rolls 60 degrees to one side of their facing,
     * at yaw 0, 0.7 out past the near clip and at the waist, half their 1.8 height.
     */
    @Test
    void aFirstPersonCastersLowestRollStarRisesAtTheArcsEdgeNearAndAtTheWaist() {
        Vec3 at = HealVisual.starAt(FEET, STAR_HEIGHT, STAR_YAW, 0, 0, 0);
        assertEquals(FEET.x, at.x, DELTA);
        assertEquals(FEET.y + 0.9, at.y, DELTA);
        assertEquals(FEET.z + 0.7, at.z, DELTA);
    }

    /**
     * A first-person caster facing yaw 60 sees a star on the highest rolls 60 degrees to the other side, at yaw 120,
     * 1.2 out and just over the head, 1.15 of their 1.8 height.
     */
    @Test
    void aFirstPersonCastersHighestRollStarRisesAtTheArcsOtherEdgeFarAndOverTheHead() {
        Vec3 at = HealVisual.starAt(FEET, STAR_HEIGHT, STAR_YAW, 1, 1, 1);
        assertEquals(FEET.x - 0.6 * Math.sqrt(3), at.x, DELTA);
        assertEquals(FEET.y + 2.07, at.y, DELTA);
        assertEquals(FEET.z - 0.6, at.z, DELTA);
    }
}
