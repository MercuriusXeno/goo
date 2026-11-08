package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camera never reaches a gate's face: a body is carried while its eye
 * still stands a little before the face, nearer than the camera's near
 * plane would let it come without clipping into the wall, and it lands that
 * same margin before the partner's face
 * (decisions portals-link-by-looking-through-portal-vision, hole-closes-the-space-to-an-opening-ahead).
 */
class GatePassageTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 mouth on a wall's north face, the face the plane z 0. */
    private static final Gate NEAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 0)).minmax(
            new AABB(new BlockPos(1, 2, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on the far face, the plane z 3, facing south. */
    private static final Gate FAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 2)).minmax(
            new AABB(new BlockPos(1, 2, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    /** The camera's near plane, in blocks. */
    private static final double NEAR_PLANE = 0.05;

    @Test
    void anEyeNearerThanTheNearPlaneToTheFaceHasCrossed() {
        Vec3 before = new Vec3(0.5, 1.5, -0.2);
        Vec3 now = new Vec3(0.5, 1.5, -0.03);
        assertTrue(GatePassage.crossed(NEAR, before, now),
                "An eye three hundredths before the face, nearer than the near plane, should cross");
    }

    @Test
    void steppingStraightBackIntoThePartnerCrossesIt() {
        Vec3 eye = new Vec3(0.5, 1.5, -0.03);
        GatePassage.Landing landing = GatePassage.landingThrough(NEAR, FAR, new GatePassage.Held(eye, 1.62), 1.8,
                Vec3.ZERO, 0F, 0F);
        Vec3 landed = landing.feet().add(0, 1.62, 0);
        Vec3 stepBack = landed.subtract(FAR.face().getUnitVec3().scale(STEP_BACK));
        assertTrue(GatePassage.crossed(FAR, landed, stepBack),
                "Stepping back from where it landed should cross the partner again, never into the wall behind it");
    }

    @Test
    void aBodyMovingInPastTheMarginCrossesFromWhereverItsLastTickStood() {
        Vec3 before = new Vec3(0.5, 1.5, FAR_FACE_Z + 0.02);
        Vec3 now = new Vec3(0.5, 1.5, FAR_FACE_Z - 0.05);
        assertTrue(GatePassage.crossed(FAR, before, now), "A body moving in past the margin should cross, though its "
                + "last tick already stood within the margin");
    }

    @Test
    void aBodyMovingOutAcrossTheMarginDoesNotCross() {
        Vec3 before = new Vec3(0.5, 1.5, FAR_FACE_Z - 0.05);
        Vec3 now = new Vec3(0.5, 1.5, FAR_FACE_Z + 0.02);
        org.junit.jupiter.api.Assertions.assertFalse(GatePassage.crossed(FAR, before, now),
                "A body coming out of the face should not cross back");
    }

    /** A floor portal on the top of a block at y 64, its face the plane y 65. */
    private static final Gate FLOOR = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 0)).minmax(
            new AABB(new BlockPos(0, 64, 1))), Direction.UP, Direction.NORTH.getUnitVec3());
    /** A fall fast enough to carry a body past the face and on below it in one tick. */
    private static final Vec3 FAST_FALL = new Vec3(0, -3.0, 0);

    @Test
    void aFastFallThroughAFloorPortalStillCrosses() {
        Vec3 before = new Vec3(0.5, 66.0, 1.0);
        Vec3 now = before.add(FAST_FALL);
        assertTrue(GatePassage.crossed(FLOOR, before, now), "A body falling past a floor portal's face and on "
                + "below it in one tick should still cross it");
    }

    @Test
    void aFastFallSunkDeepIsStillCarried() {
        Vec3 feet = new Vec3(0.5, 61.2, 1.0);
        assertTrue(GatePassage.heldBehind(FLOOR, feet, 1.62, 1.8, FAST_FALL).isPresent(), "A body a fast fall "
                + "carried well past a floor portal's face should still be carried through it");
    }

    /** Straight down, as a camera's pitch reads it. */
    private static final float LOOKING_DOWN = 90F;

    @Test
    void aBodyLookingDownComesOutStillLookingDown() {
        Vec3 eye = new Vec3(0.5, 64.97, 1.0);
        GatePassage.Landing landing = GatePassage.landingThrough(FLOOR, NEAR, new GatePassage.Held(eye, 1.62), 1.8,
                FAST_FALL, 0F, LOOKING_DOWN);
        assertTrue(Math.abs(landing.pitch() - LOOKING_DOWN) < 1F, "A body dropping into a floor portal looking "
                + "down should come out of a wall portal still looking down, pitched " + landing.pitch());
    }

    /** The air time coming up out of a floor gate should give, in blocks risen. */
    private static final double ESCAPE_RISE = 2.0;
    /** A living body's gravity a tick, and the drag its upward speed keeps. */
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    @Test
    void comingUpOutOfAFloorGateLiftsABodyClearOfIt() {
        Vec3 eye = new Vec3(0.5, 1.5, -0.03);
        GatePassage.Landing landing = GatePassage.landingThrough(NEAR, FLOOR, new GatePassage.Held(eye, 1.62), 1.8,
                new Vec3(0, 0, 0.1), 0F, 0F);
        double rise = 0;
        for (double speed = landing.motion().y(); speed > 0; speed = (speed - GRAVITY) * DRAG) {
            rise += speed;
        }
        assertTrue(rise >= ESCAPE_RISE, "A body coming up out of a floor gate should rise at least " + ESCAPE_RISE
                + " blocks to step off it, rises " + rise);
    }

    /** The far mouth's face, the plane z 3. */
    private static final double FAR_FACE_Z = 3.0;

    /** A short step back toward the face just come out of. */
    private static final double STEP_BACK = 0.06;

    @Test
    void aCrossedEyeLandsBeyondTheNearPlaneBeforeThePartnersFace() {
        Vec3 eye = new Vec3(0.5, 1.5, -0.03);
        GatePassage.Landing landing = GatePassage.landingThrough(NEAR, FAR, new GatePassage.Held(eye, 1.62), 1.8,
                Vec3.ZERO, 0F, 0F);
        double out = FAR.out(landing.feet().add(0, 1.62, 0));
        assertTrue(out > NEAR_PLANE, "The eye should land past the near plane before the partner's face, stands "
                + out + " out of it");
    }
}
