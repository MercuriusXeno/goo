package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooRingParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Rock's layer preview sends goo's ring particle one block in front of the
 * layer, turned to the blast face, sized to the layer's reach and tinted rock
 * tan (decision goo-swirl-ring-particle), on a mocked ServerLevel.
 */
class RockDustVisualsTest {

    private static final BlockPos ORIGIN = new BlockPos(10, 64, -20);
    private static final int TUNNEL_STACKS = 3;
    private static final int LAYER = 1;
    private static final double BLOCK_CENTER = 0.5;
    private static final int ROCK_TAN = 0xC2A868;

    /**
     * ServerLevel's class init reads the built-in registries, which the vanilla bootstrap
     * stands; the bootstrap asks FML whether it runs in production, which a stubbed loader answers.
     */
    @BeforeAll
    static void standRegistries() {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void tunnelLayerPreviewSendsOneTanRingInFrontOfTheLayer(Direction placedFace) {
        ServerLevel level = mock(ServerLevel.class);
        float reach = ChainFootprint.tunnelFaceReach(TUNNEL_STACKS);

        RockDustVisuals.INSTANCE.preview(level, ORIGIN, placedFace, LAYER, TUNNEL_STACKS, reach);

        BlockPos inFront = LayerGeometry.layerCenter(ORIGIN, placedFace, LAYER).relative(placedFace);
        ArgumentCaptor<ParticleOptions> sent = ArgumentCaptor.forClass(ParticleOptions.class);
        verify(level).sendParticles(sent.capture(),
                eq(inFront.getX() + BLOCK_CENTER), eq(inFront.getY() + BLOCK_CENTER), eq(inFront.getZ() + BLOCK_CENTER),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        GooRingParticleOptions ring = assertInstanceOf(GooRingParticleOptions.class, sent.getValue());
        assertEquals(new GooRingParticleOptions(placedFace.getOpposite(), 1.5f, ROCK_TAN), ring);
    }
}
