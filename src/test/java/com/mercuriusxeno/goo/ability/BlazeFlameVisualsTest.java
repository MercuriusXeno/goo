package com.mercuriusxeno.goo.ability;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Blaze rings each layer in its heat orange before it breaks, then sends its
 * flames, lava and embers scaled by the blocks destroyed (decision
 * themed-ring-before-every-layer), on a mocked ServerLevel.
 */
class BlazeFlameVisualsTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -20);
    private static final int TUNNEL_STACKS = 3;
    private static final int LAYER = 0;
    private static final int DESTROYED = 9;
    private static final int FLAMES_PER_BLOCK = 6;
    private static final int HEAT_ORANGE = 0xFF8E28;

    @BeforeAll
    static void standRegistries() {
        SentParticles.standRegistries();
    }

    @Test
    void tunnelLayerPreviewSendsOneOrangeRingAndNoFlame() {
        ServerLevel level = mock(ServerLevel.class);
        float reach = ChainFootprint.tunnelFaceReach(TUNNEL_STACKS);

        BlazeFlameVisuals.INSTANCE.preview(level, ORIGIN, Direction.SOUTH, LAYER, TUNNEL_STACKS, reach);

        assertEquals(List.of(new GooRingParticleOptions(Direction.NORTH, reach, HEAT_ORANGE)), SentParticles.of(level));
    }

    @Test
    void struckLayerSendsFlamesLavaAndEmbersScaledByTheBlocksDestroyed() {
        ServerLevel level = mock(ServerLevel.class);

        BlazeFlameVisuals.INSTANCE.onLayerStruck(level, ORIGIN, Direction.SOUTH, LAYER, DESTROYED);

        List<ParticleOptions> sent = SentParticles.of(level);
        assertEquals(List.of(ParticleTypes.FLAME, ParticleTypes.LAVA, ParticleTypes.SMALL_FLAME), sent);
        assertTrue(sent.stream().noneMatch(GooRingParticleOptions.class::isInstance));
        verify(level).sendParticles(eq(ParticleTypes.FLAME), anyDouble(), anyDouble(), anyDouble(),
                eq(FLAMES_PER_BLOCK * DESTROYED), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void struckLayerThatDestroyedNothingSendsNothing() {
        ServerLevel level = mock(ServerLevel.class);

        BlazeFlameVisuals.INSTANCE.onLayerStruck(level, ORIGIN, Direction.SOUTH, LAYER, 0);

        assertTrue(SentParticles.of(level).isEmpty());
    }
}
