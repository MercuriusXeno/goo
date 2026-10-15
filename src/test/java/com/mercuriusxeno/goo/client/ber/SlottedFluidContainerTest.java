package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a slot holding two goo types submits its fill once, carrying both
 * bands, at the slot's own bounds, which a single-type slot's fill draws at
 * (decision one-pass-blends-every-type).
 */
class SlottedFluidContainerTest {

    private static final SlotFluidGeometry.SlotGeometry GEOMETRY =
        new SlotFluidGeometry.SlotGeometry(2f / 16f, 3f / 16f, 13f / 16f, 0.5f / 16f);
    private static final float[][] CENTERS = {{4f / 16f, 4f / 16f}, {12f / 16f, 12f / 16f}};
    private static final float FILL = 0.6f;

    @Test
    void aTwoTypeSlotSubmitsOneSurfaceAtTheSingleTypeSlotsBounds() {
        SlotState empty = new SlotState();
        SlotState mingled = new SlotState();
        mingled.fill = FILL;
        mingled.bands = TypeBands.over(new GooContents(Map.of(GooTypes.ROCK, 200, GooTypes.BLAZE, 400)));
        List<List<TypeBand>> submitted = new ArrayList<>();
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();

        SlottedFluidContainer.submitMingledFluids((bands, emitter) -> {
            submitted.add(bands);
            emitter.accept(new RenderContext(new PoseStack().last(), recorder, 0));
        }, new SlotState[] {empty, mingled}, GEOMETRY, CENTERS);

        assertEquals(List.of(mingled.bands), submitted);
        assertEquals(positions(singleTypeSlot(CENTERS[1]).vertices()), positions(recorder.vertices()));
    }

    /** The faces a single-type slot at a center draws, as the single-surface pass emits them. */
    private static RecordingVertexConsumer singleTypeSlot(float[] center) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0);
        CuboidBounds b = SlotFluidGeometry.computeBounds(GEOMETRY, center[0], center[1], FILL);
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV1()).thenReturn(1f);
        SlotFluidGeometry.renderFluidTop(ctx, b, sprite, GooRenderUtil.OPAQUE_WHITE);
        SlotFluidGeometry.renderFluidSides(ctx, b, sprite, FILL, GEOMETRY, GooRenderUtil.OPAQUE_WHITE);
        return recorder;
    }

    private static List<List<Float>> positions(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().map(vertex -> List.of(vertex.x(), vertex.y(), vertex.z())).toList();
    }
}
