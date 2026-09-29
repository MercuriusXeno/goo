package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.registry.GooRingParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * FrostRimeVisuals gives frost_tunnel its per-layer effect: a white-blue ring
 * before each layer, then snowflakes and cloud scaled by the blocks frozen
 * (decision themed-ring-before-every-layer), on a mocked ServerLevel.
 */
class FrostRimeVisualsTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -20);
    private static final int TUNNEL_STACKS = 3;
    private static final int LAYER = 0;
    private static final int DESTROYED = 9;
    private static final int STRUCK_FLAKES = 12;
    private static final int FLAKES_PER_FROZEN_BLOCK = 4;
    private static final int WHITE_BLUE = 0xCCE6F5;

    @BeforeAll
    static void standRegistries() {
        SentParticles.standRegistries();
    }

    @Test
    void frostTunnelWalksWithFrostRime() {
        ProgressiveAreaStep walk = (ProgressiveAreaStep) AbilityJson.decode("frost_tunnel").behaviors().getFirst();
        assertEquals(LayerVisualsType.FROST_RIME, walk.visuals());
        assertSame(FrostRimeVisuals.INSTANCE, LayerVisualsType.byName(LayerVisualsType.FROST_RIME));
    }

    @Test
    void tunnelLayerPreviewSendsOneWhiteBlueRingAndNoSnowflake() {
        ServerLevel level = mock(ServerLevel.class);
        float reach = ChainFootprint.tunnelFaceReach(TUNNEL_STACKS);

        FrostRimeVisuals.INSTANCE.preview(level, ORIGIN, Direction.UP, LAYER, TUNNEL_STACKS, reach);

        assertEquals(List.of(new GooRingParticleOptions(Direction.DOWN, reach, WHITE_BLUE)), SentParticles.of(level));
    }

    @Test
    void struckLayerSendsSnowflakesAndCloudScaledByTheBlocksFrozen() {
        ServerLevel level = mock(ServerLevel.class);

        FrostRimeVisuals.INSTANCE.onLayerStruck(level, ORIGIN, Direction.UP, LAYER, DESTROYED);

        List<ParticleOptions> sent = SentParticles.of(level);
        assertEquals(List.of(ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD), sent);
        assertTrue(sent.stream().noneMatch(GooRingParticleOptions.class::isInstance));
        verify(level).sendParticles(eq(ParticleTypes.SNOWFLAKE), anyDouble(), anyDouble(), anyDouble(),
                eq(STRUCK_FLAKES + FLAKES_PER_FROZEN_BLOCK * DESTROYED),
                anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void struckLayerThatFrozeNothingSendsNothing() {
        ServerLevel level = mock(ServerLevel.class);

        FrostRimeVisuals.INSTANCE.onLayerStruck(level, ORIGIN, Direction.UP, LAYER, 0);

        assertTrue(SentParticles.of(level).isEmpty());
    }
}
