package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flames' fire shape, which the server burns with and the client draws: the
 * jet, the damped bounce off the face the crosshair meets, never reaching
 * behind that face or back toward the player, and the hold's front growing
 * out over a quarter second (decision flames-pixelated-fire-stream).
 */
class FlameShapeTest {

    private static final double DELTA = 1e-9;
    private static final double TAN = 0.1763;
    private static final Vec3 EYE = new Vec3(0, 1.5, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SIDE = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static AABB mobAt(double x, double y, double z) {
        return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 0.9, z + 0.3);
    }

    @Test
    void aGlancingHitKeepsItsPushAlongTheFaceAndPeelsOffItSoftly() {
        Vec3 bounce = FlameShape.bounceAxis(new Vec3(1, 0, 1).normalize(), new Vec3(0, 0, -1));
        Vec3 expected = new Vec3(1, 0, -FlameShape.RESTITUTION).normalize();
        assertEquals(expected.x, bounce.x, DELTA);
        assertEquals(expected.z, bounce.z, DELTA);
    }

    @Test
    void underACeilingTheBounceDoesNotClimbIntoIt() {
        assertEquals(0, FlameShape.climbAlong(new Vec3(0, -1, 0)).length(), DELTA);
        assertEquals(UP, FlameShape.climbAlong(UP));
    }

    @Test
    void theFrontCrossesTheRangeInAQuarterSecond() {
        assertEquals(0, FlameShape.frontAfter(0, 6), DELTA);
        assertEquals(6 * 2 / 5.0, FlameShape.frontAfter(2, 6), DELTA);
        assertEquals(6, FlameShape.frontAfter(40, 6), DELTA);
    }

    @Test
    void theJetTouchesAMobInItsPathAndMissesOneBesideIt() {
        FlameShape open = new FlameShape(EYE, EAST, SIDE, TAN, 6, null, null);
        assertTrue(open.touches(mobAt(4, 1.1, 0)));
        assertFalse(open.touches(mobAt(4, 1.1, 3)));
        assertFalse(open.touches(mobAt(-2, 1.1, 0)));
    }

    @Test
    void theJetStopsWhereItsFrontHasReached() {
        FlameShape young = new FlameShape(EYE, EAST, SIDE, TAN, 2, null, null);
        assertFalse(young.touches(mobAt(5, 1.1, 0)));
    }

    @Test
    void nothingBurnsBehindTheFaceTheCrosshairMeets() {
        Vec3 hit = new Vec3(3, 1.5, 0);
        FlameShape cut = new FlameShape(EYE, EAST, SIDE, TAN, 6, hit, new Vec3(-1, 0, 0));
        assertTrue(cut.bounces());
        assertFalse(cut.touches(mobAt(4.5, 1.1, 0)), "burned behind the wall");
    }

    @Test
    void aShallowFloorHitSkidsAwayAndNeverBackTowardThePlayer() {
        Vec3 down = new Vec3(4, -1.5, 0).normalize();
        Vec3 hit = EYE.add(down.scale(1.5 / -down.y));
        FlameShape skid = new FlameShape(EYE, down, SIDE, TAN, 6, hit, UP);
        assertTrue(skid.touches(mobAt(hit.x + 0.6, 0, 0)), "missed the fire skidding on past the hit");
        // Low on the floor under the jet, where only a bounce reaching back toward the player would burn.
        AABB chickenUnderTheJet = new AABB(hit.x - 2.8, 0, -0.2, hit.x - 2.4, 0.3, 0.2);
        assertFalse(skid.touches(chickenUnderTheJet), "burned back toward the player along the floor");
    }

    @Test
    void underACeilingTheFireRollsOnBelowTheFaceAwayFromThePlayer() {
        Vec3 upward = new Vec3(4, 1.5, 0).normalize();
        Vec3 hit = EYE.add(upward.scale(1.5 / upward.y));
        FlameShape roll = new FlameShape(EYE, upward, SIDE, TAN, 6, hit, new Vec3(0, -1, 0));
        assertTrue(roll.body(hit.add(0.5, -0.4, 0)) > 0, "nothing rolling just under the ceiling past the hit");
        assertTrue(roll.body(hit.add(0.5, 0.2, 0)) < -1, "fire inside the ceiling");
    }
}
