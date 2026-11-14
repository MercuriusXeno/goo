package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each knife of a released Knives charge leaves the blob floating ahead of
 * the hand as the player's eye and look stand at that knife's own launch
 * tick, so the knives follow the look through the backhand: the payload the
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

            for (float[] look : new float[][] {{20f, 10f}, {-35f, 40f}}) {
                when(player.getYRot()).thenReturn(look[0]);
                when(player.getViewVector(1f)).thenReturn(Vec3.directionFromRotation(look[1], look[0]));
                expected.add(KnifeRain.leavesFrom(EYE, look[0], Vec3.directionFromRotation(look[1], look[0]), true,
                        HELD));
                KnifeRainSling.launch(player, release, VELOCITY);
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
}
