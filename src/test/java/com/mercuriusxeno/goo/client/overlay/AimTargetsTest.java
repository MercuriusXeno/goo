package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    /** Answers fixed sources: the entity hit given, a block target and a point target. */
    private record FixedSources(AimAssistResolver.@Nullable AimHit entityHit) implements AimTargets.AimSources {
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
        @EnumSource(value = AbilityBadge.class, names = {"SELF", "BREW"})
        void selfAimsNothing(AbilityBadge badge) {
            assertSame(TargetResult.NONE, resolve(badge, null).target());
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"FREE", "CHANNELED", "PRISM", "TAP"})
        void freeAndChanneledAimTheRaysPointWhereNoMobIsNear(AbilityBadge badge) {
            assertEquals(POINT_TARGET, resolve(badge, null).target());
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"FREE", "CHANNELED", "PRISM", "TAP"})
        void freeAimLocksOntoAMobNearTheRay(AbilityBadge badge) {
            Entity zombie = mock(Entity.class);

            AimState.Resolution resolution = resolve(badge, new AimAssistResolver.AimHit.EntityHit(zombie));

            TargetResult.EntityTarget target = assertInstanceOf(TargetResult.EntityTarget.class, resolution.target());
            assertSame(zombie, target.entity());
            assertEquals(ENTITY_POINT, target.point());
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

            assertEquals(new TargetResult.PointTarget(reach, new BlockPos(10, 120, -41), Direction.UP, false), target);
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
}
