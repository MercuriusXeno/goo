package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * Tests that a crucible submits one surface whatever goo types it shows, every
 * vertex at the surface height, carrying each type's share and noise seed in
 * the fixed type order (decision one-pass-blends-every-type).
 */
class CrucibleMingledSurfaceTest {

    private static final float SURFACE_Y = 0.875f;

    private record Submission(List<TypeBand> bands, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    static Stream<Arguments> contentsTypesAndShares() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 700), List.of(GooTypes.BLAZE), List.of(1f)),
            Arguments.of(Map.of(GooTypes.FROST, 750, GooTypes.BLAZE, 250),
                List.of(GooTypes.BLAZE, GooTypes.FROST), List.of(1f, 0.75f)),
            Arguments.of(Map.of(GooTypes.FROST, 300, GooTypes.ASTRAL, 100, GooTypes.BLAZE, 600),
                List.of(GooTypes.ASTRAL, GooTypes.BLAZE, GooTypes.FROST), List.of(1f, 600f / 700f, 0.3f)));
    }

    @ParameterizedTest
    @MethodSource("contentsTypesAndShares")
    void oneSurfaceCarriesEveryTypeInTypeOrderAtTheSurfaceHeight(Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
                                                                 List<ResourceKey<GooTypeDefinition>> types,
                                                                 List<Float> shares) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        CrucibleRenderState state = mock(CrucibleRenderState.class);
        state.typeBands = TypeBands.over(new GooContents(volumes));
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
        List<Submission> submissions = new ArrayList<>();

        CrucibleBlockEntityRenderer.renderMingledSurface((bands, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            emitter.accept(new RenderContext(new PoseStack().last(), recorder, 0));
            submissions.add(new Submission(bands, recorder.vertices()));
        }, state, CrucibleBasin.footprintForVolume(CrucibleBasin.RIM_VOLUME), SURFACE_Y);

        assertEquals(1, submissions.size());
        Submission surface = submissions.getFirst();
        assertEquals(types, surface.bands().stream().map(TypeBand::type).toList());
        assertEquals(types.stream().map(GooTypes::indexOf).toList(),
            surface.bands().stream().map(TypeBand::seed).toList());
        for (int k = 0; k < shares.size(); k++) {
            assertEquals(shares.get(k), surface.bands().get(k).share(), 1e-6f);
        }
        assertFalse(surface.vertices().isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : surface.vertices()) {
            assertEquals(SURFACE_Y, vertex.y());
        }
    }
}
