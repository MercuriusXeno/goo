package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The aim each badge resolves: mob favors the entity and falls back to the
 * block, world favors the block, self aims nothing, and every other badge aims
 * the ray's point (decision target-kind-configured-per-ability).
 */
class AimTargetsTest {

    private static final BlockPos BLOCK = new BlockPos(3, 64, -7);
    private static final TargetResult BLOCK_TARGET = TargetResult.block(BLOCK, Direction.NORTH);
    private static final TargetResult POINT_TARGET = TargetResult.pointOnBlock(new Vec3(1.5, 65.2, 2.25), BLOCK, Direction.UP);
    private static final Vec3 ENTITY_POINT = new Vec3(8.3, 65.1, 4.4);
    private static final double CEILING = 320;
    private static final double FLOOR = -64;

    private static TargetResult faceOf(BlockHitResult hit) {
        return TargetResult.block(hit.getBlockPos(), hit.getDirection(), hit.getLocation());
    }

    /**
     * A look meeting no block within range drops to the farthest ground under
     * it within range of the eye, sliding in from the look's end, and lights
     * nothing where no ground under the look is in range
     * (decision dropped-ground-highlight-lights-only-within-range).
     */
    @Nested
    class TheDroppedHighlight {

        private static final double RANGE = 16;
        private static final double EYE_HEIGHT = 1.62;
        private static final Vec3 LEVEL = new Vec3(1, 0, 0);

        /**
         * Ground whose top stands at the height the column's x gives: the
         * clip meets a straight drop at that top, and a level look above the
         * ground meets nothing.
         */
        private static AimTargets.BlockClip groundTopping(DoubleUnaryOperator topAt) {
            return (from, to) -> {
                double top = topAt.applyAsDouble(from.x);
                boolean drop = from.x == to.x && from.z == to.z;
                if (!drop || top > from.y || top < to.y) {
                    return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
                }
                Vec3 met = new Vec3(from.x, top, from.z);
                return new BlockHitResult(met, Direction.UP, BlockPos.containing(from.x, top - 1, from.z), false);
            };
        }

        private static TargetResult droppedFrom(Vec3 eye, DoubleUnaryOperator topAt) {
            AimTargets.BlockClip ground = groundTopping(topAt);
            return AimTargets.blockTargetAlong(AimTargets.AimRay.along(eye, LEVEL, RANGE), ground,
                    AimTargetsTest::faceOf, new AimTargets.Ground(ground, CEILING, FLOOR));
        }

        @Test
        void aLevelLookOverLevelGroundLightsGroundTheServerTakes() {
            Vec3 eye = new Vec3(0.5, 65 + EYE_HEIGHT, 0.5);

            TargetResult target = droppedFrom(eye, x -> 65);

            TargetResult.BlockTarget lit = assertInstanceOf(TargetResult.BlockTarget.class, target);
            assertTrue(lit.point().distanceTo(eye) <= RANGE, "The lit ground should lie within range of the eye");
            assertTrue(GooThrowHandler.takesAim(eye, new AABB(lit.pos()), lit.point(), RANGE),
                    "The server's range gate should take a release at the lit ground");
        }

        @Test
        void fromALedgeOverADeepDropTheLedgesGroundLights() {
            Vec3 eye = new Vec3(0.5, 65 + EYE_HEIGHT, 0.5);
            double ledgeEnds = eye.x + 8;

            TargetResult target = droppedFrom(eye, x -> x < ledgeEnds ? 65 : 35);

            TargetResult.BlockTarget lit = assertInstanceOf(TargetResult.BlockTarget.class, target);
            assertEquals(64, lit.pos().getY(), "The ledge's ground should light, not the floor below");
            assertTrue(lit.point().distanceTo(eye) <= RANGE, "The lit ground should lie within range of the eye");
        }

        @Test
        void anEyeHighOverTheOnlyGroundLightsNothing() {
            Vec3 eye = new Vec3(0.5, 35 + 30, 0.5);

            assertSame(TargetResult.NONE, droppedFrom(eye, x -> 35));
        }
    }

    /** Answers fixed sources: the entity hit given, a block target and a point target, each within range or not. */
    private record FixedSources(AimAssistResolver.@Nullable AimHit entityHit, boolean inRange)
            implements AimTargets.AimSources {

        FixedSources(AimAssistResolver.@Nullable AimHit entityHit) {
            this(entityHit, true);
        }

        @Override
        public boolean reaches(Vec3 point) {
            return inRange;
        }

        @Override
        public TargetResult blockTarget() {
            return BLOCK_TARGET;
        }

        @Override
        public TargetResult pointTarget() {
            return POINT_TARGET;
        }

        @Override
        public Vec3 entityPoint(Entity entity) {
            return ENTITY_POINT;
        }
    }

    private static AimState.Resolution resolve(AbilityBadge badge, AimAssistResolver.@Nullable AimHit entityHit) {
        return AimTargets.resolveFor(TargetingHint.of(badge), new FixedSources(entityHit));
    }

    @Nested
    class EachBadge {

        @Test
        void mobFavorsTheEntityUnderTheRay() {
            Entity cow = mock(Entity.class);
            AimAssistResolver.AimHit hit = new AimAssistResolver.AimHit.EntityHit(cow);

            AimState.Resolution resolution = resolve(AbilityBadge.MOB, hit);

            TargetResult.EntityTarget target = assertInstanceOf(TargetResult.EntityTarget.class, resolution.target());
            assertSame(cow, target.entity());
            assertEquals(ENTITY_POINT, target.point());
            assertSame(hit, resolution.hit());
        }

        @Test
        void mobAimsNothingWhereNoEntityIsNear() {
            AimState.Resolution resolution = resolve(AbilityBadge.MOB, null);

            assertEquals(TargetResult.NONE, resolution.target());
            assertNull(resolution.hit());
        }

        @Test
        void worldFavorsTheBlockOverAnEntity() {
            AimState.Resolution resolution = resolve(AbilityBadge.WORLD,
                    new AimAssistResolver.AimHit.EntityHit(mock(Entity.class)));

            assertEquals(BLOCK_TARGET, resolution.target());
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"SELF", "DURATIVE"})
        void selfAimsNothing(AbilityBadge badge) {
            assertSame(TargetResult.NONE, resolve(badge, null).target());
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"FREE", "CHANNELED", "RESIDUAL"})
        void freeAndChanneledAimTheRaysPointWhereNoMobIsNear(AbilityBadge badge) {
            assertEquals(POINT_TARGET, resolve(badge, null).target());
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"FREE", "CHANNELED", "RESIDUAL"})
        void freeAimLocksOntoAMobNearTheRay(AbilityBadge badge) {
            Entity zombie = mock(Entity.class);

            AimState.Resolution resolution = resolve(badge, new AimAssistResolver.AimHit.EntityHit(zombie));

            TargetResult.EntityTarget target = assertInstanceOf(TargetResult.EntityTarget.class, resolution.target());
            assertSame(zombie, target.entity());
            assertEquals(ENTITY_POINT, target.point());
        }

        /** decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast */
        @Test
        void aMobPastRangeOfTheEyeLocksNoMobAim() {
            AimState.Resolution resolution = AimTargets.resolveFor(TargetingHint.of(AbilityBadge.MOB),
                    new FixedSources(new AimAssistResolver.AimHit.EntityHit(mock(Entity.class)), false));

            assertSame(TargetResult.NONE, resolution.target());
            assertNull(resolution.hit());
        }
    }

    /**
     * With the camera standing apart from the caster's eye, a face lights
     * only within the ability's range of the eye, the measure the server's
     * range gate reads (decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast).
     */
    @Nested
    class TheCameraApartFromTheEye {

        private static final double RANGE = 16;
        private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
        private static final Vec3 FACING = new Vec3(1, 0, 0);
        /** The camera four blocks behind the eye, as the third-person view stands it. */
        private static final Vec3 BEHIND = EYE.subtract(FACING.scale(4));

        /** A wall facing the ray at the given x: the clip meets it where the segment crosses that plane. */
        private static AimTargets.BlockClip wallAt(double x) {
            return (from, to) -> {
                double fraction = (x - from.x) / (to.x - from.x);
                if (fraction < 0 || fraction > 1) {
                    return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
                }
                Vec3 met = from.add(to.subtract(from).scale(fraction));
                return new BlockHitResult(met, Direction.WEST, BlockPos.containing(met), false);
            };
        }

        /** A wall facing the ray at the given z, for a ray looking along z. */
        private static AimTargets.BlockClip wallAcrossZ(double z) {
            return (from, to) -> {
                double fraction = (z - from.z) / (to.z - from.z);
                if (fraction < 0 || fraction > 1) {
                    return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
                }
                Vec3 met = from.add(to.subtract(from).scale(fraction));
                return new BlockHitResult(met, Direction.NORTH, BlockPos.containing(met), false);
            };
        }

        private static TargetResult faceAlong(AimTargets.AimRay ray, AimTargets.BlockClip clip) {
            return AimTargets.blockTargetAlong(ray, clip, AimTargetsTest::faceOf,
                    new AimTargets.Ground((from, to) -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to)),
                            CEILING, FLOOR));
        }

        @Test
        void aFaceWithinRangeOfTheEyeLightsThoughPastRangeOfTheCamera() {
            AimTargets.AimRay ray = AimTargets.AimRay.fromCamera(BEHIND, FACING, EYE, RANGE);

            TargetResult target = faceAlong(ray, wallAt(EYE.x + RANGE - 0.5));

            TargetResult.BlockTarget lit = assertInstanceOf(TargetResult.BlockTarget.class, target);
            assertEquals(RANGE - 0.5, lit.point().distanceTo(EYE), 1e-6);
        }

        @Test
        void aFacePastRangeOfTheEyeLightsNothingThoughWithinRangeOfTheCamera() {
            Vec3 across = new Vec3(0, 0, 1);
            AimTargets.AimRay ray = AimTargets.AimRay.fromCamera(BEHIND, across, EYE, RANGE);
            double wallZ = BEHIND.z + RANGE - 0.2;

            TargetResult target = faceAlong(ray, wallAcrossZ(wallZ));

            assertEquals(RANGE - 0.2, new Vec3(BEHIND.x, BEHIND.y, wallZ).distanceTo(BEHIND), 1e-6,
                    "The face should stand within range of the camera");
            assertSame(TargetResult.NONE, target);
        }
    }

    @Nested
    class ThePoint {

        @Test
        void aRayMeetingABlockAimsWhereItMetTheFace() {
            Vec3 met = new Vec3(3.4, 64.7, -7.0);

            TargetResult target = AimTargets.pointOf(new BlockHitResult(met, Direction.NORTH, BLOCK, false),
                    new Vec3(30, 70, -60));

            assertEquals(TargetResult.pointOnBlock(met, BLOCK, Direction.NORTH), target);
            assertEquals(met, target.resolveEndpoint());
        }

        @Test
        void aRayInOpenAirAimsItsEndAtRange() {
            Vec3 reach = new Vec3(10.25, 120.5, -40.75);

            TargetResult target = AimTargets.pointOf(BlockHitResult.miss(reach, Direction.UP, BlockPos.containing(reach)),
                    reach);

            assertEquals(new TargetResult.PointTarget(reach, new BlockPos(10, 120, -41), Direction.UP, false, null), target);
        }

        @Test
        void aRayThroughAnEntityAimsWhereItMetTheBox() {
            AABB box = new AABB(4, 64, -1, 5, 66, 1);

            Vec3 point = AimTargets.pointOnEntity(box, new Vec3(0, 65, 0), new Vec3(64, 65, 0));

            assertEquals(new Vec3(4, 65, 0), point);
        }

        @Test
        void anEntityTheAimAssistChoseBesideTheRayAimsItsCenter() {
            AABB box = new AABB(4, 64, 3, 5, 66, 4);

            Vec3 point = AimTargets.pointOnEntity(box, new Vec3(0, 65, 0), new Vec3(64, 65, 0));

            assertEquals(box.getCenter(), point);
        }
    }

    /**
     * The ray starts at the origin the caller passes, the camera as the frame
     * draws it, so the point eases with the camera's eye height (decision
     * aim-ray-starts-at-the-camera).
     */
    @Nested
    class TheRay {

        private static final double WALL_X = 5;
        private static final Vec3 LOOK = new Vec3(1, -0.2, 0).normalize();
        private static final double LOOK_DROP_AT_THE_WALL = -1.0;
        private static final double RANGE = 64;

        /** A flat wall at x = 5 facing the ray: the clip meets it where the segment crosses that plane. */
        private static BlockHitResult clipAgainstTheWall(Vec3 from, Vec3 to) {
            double fraction = (WALL_X - from.x) / (to.x - from.x);
            Vec3 met = from.add(to.subtract(from).scale(fraction));
            return new BlockHitResult(met, Direction.WEST, BlockPos.containing(met), false);
        }

        private static double aimedHeightFrom(double originHeight) {
            AimTargets.AimRay ray = AimTargets.AimRay.along(new Vec3(0, originHeight, 0), LOOK, RANGE);
            return AimTargets.pointAlong(ray, TheRay::clipAgainstTheWall).resolveEndpoint().y;
        }

        @Test
        void originsDifferingInHeightAimPointsDifferingByThatHeight() {
            double standing = aimedHeightFrom(1.62);
            double crouching = aimedHeightFrom(1.27);

            assertEquals(0.35, standing - crouching, 1e-6);
        }

        @ParameterizedTest
        @ValueSource(doubles = {1.62, 1.47, 1.395})
        void anEasingOriginMovesThePointByTheSameEase(double originHeight) {
            assertEquals(originHeight + LOOK_DROP_AT_THE_WALL, aimedHeightFrom(originHeight), 1e-6);
        }
    }
}
