package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each knife of a released Knives charge leaves the blob floating ahead of
 * the hand where it stands at that knife's own launch tick, the eye, the look
 * and the hand's place in the backhand all read then, so the knives spew along
 * the hand's path through the backhand: the payload the
 * watchers draw it from and a struck mob's aim both start there
 * (decisions knives-blob-floats-ahead-of-the-hand-and-spews-the-knives,
 * knives-sling-then-morph-to-flechettes).
 */
class KnifeRainSlingTest {

    private static final Vec3 EYE = new Vec3(3, 65.62, -2);
    /** The ticks the released charge was held, part way through the wind-up. */
    private static final int HELD = 18;
    private static final Vec3 VELOCITY = new Vec3(0.1, 0.2, 0.95);
    private static final double WITHIN = 1e-9;

    @Test
    void knivesLeavingAtDifferentTicksOfTheBackhandLeaveFromDifferentPointsAcrossTheChest() {
        Vec3 look = Vec3.directionFromRotation(10f, 20f);

        Vec3 first = KnifeRain.leavesFrom(EYE, 20f, look, true, HELD, 0);
        Vec3 flung = KnifeRain.leavesFrom(EYE, 20f, look, true, HELD, KnifeRain.BACKHAND_OUT_TICKS);

        assertTrue(first.distanceTo(flung) > 0.1, "the blob should travel with the hand through the backhand, from "
                + first + " to " + flung);
    }

    @Test
    void eachKnifeLeavesTheFloatPointAsTheLookStandsAtItsLaunchAndAStruckMobsAimStartsThere() {
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(player.level()).thenReturn(level);
        when(level.getServer()).thenReturn(server);
        when(player.getEyePosition()).thenReturn(EYE);
        when(level.clip(any())).thenAnswer(call -> BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO));
        LivingEntity husk = mock(LivingEntity.class);
        when(husk.getBoundingBox()).thenReturn(new AABB(-100, -100, -100, 100, 100, 100));
        AbilityDefinition knives = mock(AbilityDefinition.class);
        when(knives.id()).thenReturn(Identifier.fromNamespaceAndPath("goo", "crystal_knives"));
        GooEffectScheduler effects = mock(GooEffectScheduler.class);
        GooServerState state = mock(GooServerState.class);
        when(state.gooEffects()).thenReturn(effects);
        KnifeRainSling.Release release = new KnifeRainSling.Release(GooTypes.CRYSTAL, knives, true, HELD);
        List<Vec3> expected = new ArrayList<>();
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class);
             MockedStatic<ProjectileUtil> hits = mockStatic(ProjectileUtil.class);
             MockedStatic<GooServerState> states = mockStatic(GooServerState.class)) {
            EntityHitResult huskHit = new EntityHitResult(husk);
            hits.when(() -> ProjectileUtil.getEntityHitResult(any(), any(), any(), any(), any(), any(), anyFloat()))
                    .thenReturn(huskHit);
            states.when(() -> GooServerState.of(server)).thenReturn(state);

            int[] sinceRelease = {0, (int) KnifeRain.BACKHAND_OUT_TICKS};
            float[][] looks = {{20f, 10f}, {-35f, 40f}};
            for (int knife = 0; knife < 2; knife++) {
                float[] look = looks[knife];
                when(player.getYRot()).thenReturn(look[0]);
                when(player.getViewVector(1f)).thenReturn(Vec3.directionFromRotation(look[1], look[0]));
                expected.add(KnifeRain.leavesFrom(EYE, look[0], Vec3.directionFromRotation(look[1], look[0]), true,
                        HELD, sinceRelease[knife]));
                KnifeRainSling.launch(player, release, VELOCITY, sinceRelease[knife]);
            }

            ArgumentCaptor<GlassKnifePayload> sent = ArgumentCaptor.forClass(GlassKnifePayload.class);
            visuals.verify(() -> EntityVisuals.sendToWatchers(any(), sent.capture()), times(2));
            ArgumentCaptor<GooEffectScheduler.PendingEffect> struck =
                    ArgumentCaptor.forClass(GooEffectScheduler.PendingEffect.class);
            verify(effects, times(2)).enqueue(struck.capture());
            assertTrue(expected.get(0).distanceTo(expected.get(1)) > 0.1, "turning the look should move the blob");
            for (int knife = 0; knife < 2; knife++) {
                Vec3 start = sent.getAllValues().get(knife).start();
                assertEquals(0, expected.get(knife).distanceTo(start), WITHIN, "knife " + knife + " should leave "
                        + "the float point " + expected.get(knife) + ", left " + start);
                assertEquals(0, start.distanceTo(struck.getAllValues().get(knife).aim().from()), WITHIN,
                        "the struck mob's aim should start where knife " + knife + " left");
            }
        }
    }

    /**
     * A full Knives charge queues each knife to leave at the release tick
     * plus the pattern's tick for its place, flying the pattern's velocity
     * for that place (decision knives-land-in-staggered-rows-with-no-randomness).
     */
    @Test
    void aFullChargeQueuesEachKnifeAtThePatternsVelocityAndTick() {
        int releaseTick = 400;
        float yaw = 20f;
        float pitch = -10f;
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(player.level()).thenReturn(level);
        when(level.getServer()).thenReturn(server);
        when(server.getTickCount()).thenReturn(releaseTick);
        when(player.getYRot()).thenReturn(yaw);
        when(player.getXRot()).thenReturn(pitch);
        when(player.getEyePosition()).thenReturn(EYE);
        when(player.getViewVector(1f)).thenReturn(Vec3.directionFromRotation(pitch, yaw));
        when(level.clip(any())).thenAnswer(call -> BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO));
        AbilityDefinition knives = AbilityJson.decode("crystal_knives");
        Charge charge = knives.delivery().charge();
        FleckLaunches launches = new FleckLaunches();
        GooServerState state = mock(GooServerState.class);
        when(state.fleckLaunches()).thenReturn(launches);
        List<Vec3> pattern = KnifeRain.releaseVelocities(yaw, pitch, charge, 1f);
        int[] patternTicks = KnifeRain.launchTicks(charge.fleckCount(1f), charge.sweepTicks());
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class);
             MockedStatic<ProjectileUtil> hits = mockStatic(ProjectileUtil.class);
             MockedStatic<GooServerState> states = mockStatic(GooServerState.class);
             MockedStatic<AbilityCharges> charges = mockStatic(AbilityCharges.class);
             MockedStatic<GooValues> values = mockStatic(GooValues.class);
             MockedStatic<GooThrowHandler> reagents = mockStatic(GooThrowHandler.class);
             MockedStatic<KnifeRainSling> hand = mockStatic(KnifeRainSling.class, CALLS_REAL_METHODS)) {
            states.when(() -> GooServerState.of(server)).thenReturn(state);
            charges.when(() -> AbilityCharges.charge(any(), any(), any())).thenReturn(true);
            values.when(() -> GooValues.of(any(Level.class))).thenReturn(mock(IGooValueLookup.class));
            hand.when(() -> KnifeRainSling.gloveOnTheRight(any())).thenReturn(true);

            KnifeRainSling.sling(player, GooTypes.CRYSTAL, knives, knives.delivery().chargeTicks());

            assertEquals(pattern.size(), launches.pendingCount(), "every knife of the pattern should be queued");
            List<Vec3> sent = new ArrayList<>();
            List<Integer> sentAt = new ArrayList<>();
            visuals.when(() -> EntityVisuals.sendToWatchers(any(), any(GlassKnifePayload.class)))
                    .thenAnswer(call -> {
                        sent.add(call.<GlassKnifePayload>getArgument(1).velocity());
                        return null;
                    });
            for (int tick = releaseTick; tick <= releaseTick + charge.sweepTicks(); tick++) {
                int before = sent.size();
                launches.drainArrived(tick);
                for (int knife = before; knife < sent.size(); knife++) {
                    sentAt.add(tick);
                }
            }
            assertEquals(pattern, sent, "each knife should fly the pattern's velocity for its place");
            for (int knife = 0; knife < pattern.size(); knife++) {
                assertEquals(releaseTick + patternTicks[knife], sentAt.get(knife), "knife " + knife
                        + " should leave at the release tick plus its pattern tick");
            }
        }
    }
}
