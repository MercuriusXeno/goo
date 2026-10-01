package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.MobLanding;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A goo landing on a mob, drained from a throw or struck at once by a punch
 * or touch, sends one mob-hit payload and runs the program in the same call.
 */
class GooEffectSchedulerTest {

    private static final int MOB_ID = 42;
    private static final int ARRIVAL_TICK = 7;
    private static final AABB MOB_BOX = new AABB(0, 0, 0, 1, 2, 1);
    private static final Vec3 THROWER_EYE = new Vec3(5, 1, 0.5);

    private ServerLevel level;
    private LivingEntity mob;
    private ServerPlayer thrower;
    private MobLanding landing;

    @BeforeEach
    void standTheMob() {
        level = mock(ServerLevel.class);
        when(level.getRandom()).thenReturn(mock(RandomSource.class));
        mob = mock(LivingEntity.class);
        when(mob.getId()).thenReturn(MOB_ID);
        when(mob.getBoundingBox()).thenReturn(MOB_BOX);
        when(level.getEntity(MOB_ID)).thenReturn(mob);
        thrower = mock(ServerPlayer.class);
        when(thrower.getEyePosition()).thenReturn(THROWER_EYE);
        landing = mock(MobLanding.class);
    }

    private PendingEffect effectOnTheMob(int arrivalTick) {
        return new PendingEffect(arrivalTick, level, thrower, GooTypes.BLAZE, MOB_ID, BlockPos.ZERO, null,
                "goo:blaze_touch");
    }

    private MobHitPayload theOneHitSent() {
        ArgumentCaptor<MobHitPayload> hit = ArgumentCaptor.forClass(MobHitPayload.class);
        verify(landing, times(1)).announceHit(any(), hit.capture());
        return hit.getValue();
    }

    @Nested
    class OnePayloadPerLanding {

        @Test
        void drainedThrowSendsTheStruckMobsIdAndGooType() {
            GooEffectScheduler scheduler = new GooEffectScheduler();
            scheduler.enqueue(effectOnTheMob(ARRIVAL_TICK));

            scheduler.drainArrivedEffects(ARRIVAL_TICK, landing);

            MobHitPayload hit = theOneHitSent();
            assertEquals(MOB_ID, hit.entityId());
            assertEquals(GooTypes.id(GooTypes.BLAZE), hit.gooTypeId());
        }

        @Test
        void throwStillInFlightSendsNothing() {
            GooEffectScheduler scheduler = new GooEffectScheduler();
            scheduler.enqueue(effectOnTheMob(ARRIVAL_TICK));

            scheduler.drainArrivedEffects(ARRIVAL_TICK - 1, landing);

            verify(landing, never()).announceHit(any(), any());
        }

        @Test
        void punchOrTouchLandingAtTickZeroSendsTheStruckMobsIdAndGooType() {
            GooEffectScheduler.applyEffect(effectOnTheMob(0), landing);

            MobHitPayload hit = theOneHitSent();
            assertEquals(MOB_ID, hit.entityId());
            assertEquals(GooTypes.id(GooTypes.BLAZE), hit.gooTypeId());
        }

        @Test
        void hitPointSitsOnTheFaceTowardTheStriker() {
            GooEffectScheduler.applyEffect(effectOnTheMob(0), landing);

            assertEquals(new Vec3(1, 1, 0.5), theOneHitSent().hitPoint());
        }

        @Test
        void mobGoneByArrivalSendsNothingAndRunsNothing() {
            when(level.getEntity(MOB_ID)).thenReturn(null);

            GooEffectScheduler.applyEffect(effectOnTheMob(0), landing);

            verify(landing, never()).announceHit(any(), any());
            verify(landing, never()).runProgram(any(), any());
        }
    }

    @Nested
    class ProgramRunsTheSameCall {

        @Test
        void drainedThrowSendsTheHitThenRunsTheProgramInOneCall() {
            GooEffectScheduler scheduler = new GooEffectScheduler();
            PendingEffect effect = effectOnTheMob(ARRIVAL_TICK);
            scheduler.enqueue(effect);

            scheduler.drainArrivedEffects(ARRIVAL_TICK, landing);

            InOrder order = inOrder(landing);
            order.verify(landing).announceHit(any(), any());
            order.verify(landing).runProgram(effect, mob);
            assertFalse(scheduler.hasPending());
        }

        @Test
        void punchLandingSendsTheHitThenRunsTheProgramInOneCall() {
            PendingEffect effect = effectOnTheMob(0);

            GooEffectScheduler.applyEffect(effect, landing);

            InOrder order = inOrder(landing);
            order.verify(landing).announceHit(any(), any());
            order.verify(landing).runProgram(effect, mob);
        }
    }

    @Nested
    class HitPoint {

        @Test
        void noStrikerStrikesTheBoxCenter() {
            assertEquals(MOB_BOX.getCenter(), GooEffectScheduler.hitPoint(MOB_BOX, null));
        }

        @Test
        void strikerInsideTheBoxStrikesTheBoxCenter() {
            assertEquals(MOB_BOX.getCenter(), GooEffectScheduler.hitPoint(MOB_BOX, new Vec3(0.2, 0.2, 0.2)));
        }
    }
}
