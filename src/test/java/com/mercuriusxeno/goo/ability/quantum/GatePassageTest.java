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

    /** A portal two tall on a wall's north face, its bottom edge the plane y 0, standing on the ground there. */
    private static final Gate WALL_PORTAL = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 0)).minmax(
            new AABB(new BlockPos(0, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());

    /**
     * A body dropping into the half of a floor portal its up points away
     * from comes out of a wall partner standing in the opening, its feet no
     * lower than the partner's bottom edge, rather than in the ground under
     * it, which it would sink through.
     */
    @Test
    void droppingThroughAFloorPortalsBottomHalfComesOutStandingInTheWallPartner() {
        Vec3 middle = new Vec3(0.5, 64.95, 1.6);
        GatePassage.Landing landing = GatePassage.landingThrough(FLOOR, WALL_PORTAL,
                new GatePassage.Held(middle, 0.9), 1.8, new Vec3(0, -0.3, 0), 0F, 0F);
        assertTrue(landing.feet().y() >= WALL_PORTAL.blocks().minY, "The body should come out standing in the wall "
                + "portal, feet no lower than its bottom edge " + WALL_PORTAL.blocks().minY + ", stands at "
                + landing.feet());
    }

    /** A portal on a wall's west face, its face the plane x 10. */
    private static final Gate WEST_FACING = new Gate(OVERWORLD, new AABB(new BlockPos(10, 0, 0)).minmax(
            new AABB(new BlockPos(10, 1, 0))), Direction.WEST, Direction.UP.getUnitVec3());
    /** Its partner on another wall's south face, the plane z 21. */
    private static final Gate SOUTH_FACING = new Gate(OVERWORLD, new AABB(new BlockPos(20, 0, 20)).minmax(
            new AABB(new BlockPos(20, 1, 20))), Direction.SOUTH, Direction.UP.getUnitVec3());
    /** Minecraft's yaw for each heading, in degrees. */
    private static final float FACING_SOUTH = 0F;
    private static final float FACING_WEST = 90F;
    private static final float FACING_NORTH = 180F;
    private static final float FACING_EAST = -90F;

    /**
     * A body walking into a west-facing portal facing east comes out of a
     * south-facing partner facing south, its facing kept relative to the
     * portal, as the operator ruled.
     */
    @Test
    void aBodyFacingIntoAPortalComesOutFacingOutOfItsPartner() {
        GatePassage.Landing landing = GatePassage.landing(PortalTransform.between(WEST_FACING, SOUTH_FACING),
                new Vec3(9.9, 1.0, 0.5), 0.9, new Vec3(0.2, 0, 0), FACING_EAST, 0F);
        assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(landing.yaw() - FACING_SOUTH)) < 1F, "A body "
                + "facing into the west-facing portal should come out of the south-facing one facing south, faces "
                + landing.yaw());
    }

    /**
     * A body backing into a west-facing portal, facing away from it, comes
     * out of a south-facing partner facing back at it, north.
     */
    @Test
    void aBodyBackingIntoAPortalComesOutFacingItsPartner() {
        GatePassage.Landing landing = GatePassage.landing(PortalTransform.between(WEST_FACING, SOUTH_FACING),
                new Vec3(9.9, 1.0, 0.5), 0.9, new Vec3(0.2, 0, 0), FACING_WEST, 0F);
        assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(landing.yaw() - FACING_NORTH)) < 1F, "A body "
                + "backing into the west-facing portal should come out of the south-facing one facing back at it, "
                + "north, faces " + landing.yaw());
    }

    /**
     * A body passing through a wall gate reads its light from just in front
     * of the gate's face, not from inside the wall its eye stands in, which
     * holds no light, so it is never drawn dark while it passes.
     */
    @Test
    void aBodyPassingAWallGateIsLitFromBeforeItsFace() {
        Vec3 middle = new Vec3(0.5, 1.0, -0.1);
        Vec3 eyeInTheWall = new Vec3(0.5, 1.6, 0.3);
        Vec3 lit = GatePassage.litFrom(java.util.List.of(new GatePassage.Pair(NEAR, FAR)), OVERWORLD, middle, 0.6,
                eyeInTheWall);
        assertTrue(NEAR.out(lit) > 0, "A body passing the gate should be lit from before its face, lit from " + lit);
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

    /** A second floor portal on the top of a block at x 5, y 64, its face the plane y 65. */
    private static final Gate OTHER_FLOOR = new Gate(OVERWORLD, new AABB(new BlockPos(5, 64, 0)).minmax(
            new AABB(new BlockPos(5, 64, 1))), Direction.UP, Direction.NORTH.getUnitVec3());
    /** How many times a body loops through the two floor portals. */
    private static final int LOOPS = 40;
    /** The most ticks a body takes to rise out of a floor portal and fall back in. */
    private static final int MOST_TICKS_A_LOOP = 200;
    /**
     * How far a pass's exit speed may stand over the first pass's: the jitter
     * of how deep past the face the tick it crosses on catches it, a few
     * hundredths, where the lift stacking pass on pass climbed to 1.0.
     */
    private static final double SPEED_TOLERANCE = 0.025;

    /**
     * A body looping through two floor portals, dropping back into the one it
     * came up out of, comes up out of each at the same speed pass after pass,
     * the floor's lift a minimum it never stacks onto
     * (decision portals-link-by-looking-through-portal-vision).
     */
    @Test
    void aBodyLoopingThroughFloorPortalsComesUpNoFasterEachPass() {
        GatePassage.Landing landing = GatePassage.landingThrough(NEAR, FLOOR,
                new GatePassage.Held(new Vec3(0.5, 1.5, -0.03), 1.62), 1.8, new Vec3(0, 0, 0.1), 0F, 0F);
        Gate out = FLOOR;
        Vec3 feet = landing.feet();
        Vec3 motion = new Vec3(0, landing.motion().y(), 0);
        java.util.List<Double> exits = new java.util.ArrayList<>(java.util.List.of(motion.y()));
        for (int pass = 0; pass < LOOPS; pass++) {
            Gate partner = out == FLOOR ? OTHER_FLOOR : FLOOR;
            for (int tick = 0; tick < MOST_TICKS_A_LOOP; tick++) {
                Vec3 middleBefore = feet.add(0, 0.9, 0);
                feet = feet.add(motion);
                motion = new Vec3(0, (motion.y() - GRAVITY) * DRAG, 0);
                if (GatePassage.crossed(out, middleBefore, feet.add(0, 0.9, 0))) {
                    landing = GatePassage.landingThrough(out, partner,
                            GatePassage.heldBehind(out, feet, 1.62, 1.8, motion).orElseThrow(), 1.8, motion, 0F, 0F);
                    feet = landing.feet();
                    motion = landing.motion();
                    exits.add(motion.y());
                    break;
                }
            }
            out = partner;
        }
        assertTrue(exits.stream().allMatch(speed -> speed <= exits.get(0) + SPEED_TOLERANCE), "A body looping "
                + "through floor portals should come up no faster each pass, comes up at " + exits);
    }

    /** An arrow's width, its middle the point that crosses. */
    private static final double ARROW_WIDTH = 0.5;
    /** Three blocks before the near gate's face, square on its oval's middle. */
    private static final Vec3 THREE_BEFORE = new Vec3(0.5, 1.5, -3.0);

    /**
     * A gate's blocks stand open to a body whose path this tick reaches the
     * face within the oval, so a fast projectile's clip runs on into them
     * (decision aim-and-shots-carry-through-linked-gates).
     */
    @Test
    void aFastBodyWhosePathReachesTheOvalFindsTheBlocksOpen() {
        assertTrue(GatePassage.opensToPath(NEAR, THREE_BEFORE, new Vec3(0, 0, 4.0), ARROW_WIDTH),
                "A body three blocks before the face moving four a tick into the oval should find the gate open");
    }

    @Test
    void aFastBodyWhosePathMissesTheOvalMeetsTheBlocks() {
        org.junit.jupiter.api.Assertions.assertFalse(GatePassage.opensToPath(NEAR, THREE_BEFORE,
                new Vec3(4.0, 0, 4.0), ARROW_WIDTH), "A body whose path meets the face aside of the oval should "
                + "meet the gate's blocks");
    }

    @Test
    void aBodyAtRestBeforeTheFaceFindsTheBlocksOpenAsBefore() {
        assertTrue(GatePassage.opensToPath(NEAR, new Vec3(0.5, 1.5, -0.5), Vec3.ZERO, ARROW_WIDTH),
                "A body at rest just before the face should find the gate open, as it always has");
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
