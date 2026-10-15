package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests that a vat holding two goo types submits its column once, carrying
 * both bands in the fixed type order, with every vertex where a single-type
 * column's sits and sprite-relative UVs the shader maps into each type's sprite
 * (decision one-pass-blends-every-type).
 */
class VatMingledSurfaceTest {

    private record Submission(List<TypeBand> bands, List<RecordingVertexConsumer.Vertex> vertices) {
    }

    private static List<Submission> render(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        VatRenderState state = mock(VatRenderState.class);
        state.stackSize = 1;
        state.fillFraction = 0.5f;
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
        state.typeBands = TypeBands.over(new GooContents(volumes));
        List<Submission> submissions = new ArrayList<>();
        VatFluidRenderer.renderMingledFluid((bands, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            emitter.accept(new RenderContext(new PoseStack().last(), recorder, 0));
            submissions.add(new Submission(bands, recorder.vertices()));
        }, state);
        return submissions;
    }

    @Test
    void twoTypesSubmitOneSurfaceCarryingBothBandsInTypeOrder() {
        List<Submission> submissions = render(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750));

        assertEquals(1, submissions.size());
        List<TypeBand> bands = submissions.getFirst().bands();
        assertEquals(List.of(GooTypes.BLAZE, GooTypes.FROST), bands.stream().map(TypeBand::type).toList());
        assertEquals(List.of(1f, 0.75f), bands.stream().map(TypeBand::share).toList());
    }

    @Test
    void everyVertexSitsWhereTheSingleTypeColumnsDoes() {
        List<RecordingVertexConsumer.Vertex> mingled =
            render(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750)).getFirst().vertices();
        List<RecordingVertexConsumer.Vertex> single = render(Map.of(GooTypes.BLAZE, 1000)).getFirst().vertices();

        assertFalse(mingled.isEmpty());
        assertEquals(positions(single), positions(mingled));
    }

    @Test
    void everyUvIsSpriteRelative() {
        List<RecordingVertexConsumer.Vertex> vertices =
            render(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750)).getFirst().vertices();

        assertTrue(vertices.stream().allMatch(vertex -> vertex.u() >= 0f && vertex.u() <= 1f
            && vertex.v() >= 0f && vertex.v() <= 1f));
        assertTrue(vertices.stream().anyMatch(vertex -> vertex.u() == 1f && vertex.v() == 1f));
    }

    private static List<List<Float>> positions(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().map(vertex -> List.of(vertex.x(), vertex.y(), vertex.z())).toList();
    }
}
