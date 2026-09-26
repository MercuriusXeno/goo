package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stream column tiles its fluid sprite at native scale: one segment per
 * whole block spanning the sprite's full V range, a remainder segment
 * spanning its fraction, and no UV outside the sprite's atlas bounds; the
 * vat's stream draws through the same routine.
 */
class GooStreamTilingTest {

    /** The sprite's atlas bounds, away from zero so a V past them shows. */
    private static final float U0 = 0.25f;
    private static final float U1 = 0.5f;
    private static final float V0 = 0.5f;
    private static final float V1 = 0.75f;
    private static final float CENTER = 0.5f;
    private static final float HALF_WIDTH = 0.5f / 16f;
    private static final float TOP = 3f;
    private static final int COLOR = 0xB0FFFFFF;
    private static final int SIDES = 4;
    private static final int VERTICES_PER_QUAD = 4;
    private static final float TOLERANCE = 1e-4f;
    private static final float HALF = 0.5f;
    private static final float QUARTER = 0.25f;

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(U0);
        when(sprite.getU1()).thenReturn(U1);
        when(sprite.getV0()).thenReturn(V0);
        when(sprite.getV1()).thenReturn(V1);
        return sprite;
    }

    private static List<RecordingVertexConsumer.Vertex> tile(float height) {
        return tile(height, 0f);
    }

    private static List<RecordingVertexConsumer.Vertex> tile(float height, float flowPhase) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        GooStreamRenderer.emitTiledColumn(new RenderContext(new PoseStack().last(), recorder, 0),
                new GooStreamRenderer.StreamColumn(CENTER, CENTER, TOP, TOP - height), HALF_WIDTH, sprite(), COLOR,
                flowPhase);
        return recorder.vertices();
    }

    /**
     * @param vertices every emitted vertex, four per quad
     * @return the Y where the first segment ends, the first place the scrolled sprite wraps
     */
    private static float firstWrapY(List<RecordingVertexConsumer.Vertex> vertices) {
        return (float) vertices.subList(0, VERTICES_PER_QUAD).stream()
                .mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow();
    }

    private static float span(List<RecordingVertexConsumer.Vertex> corners,
                              ToDoubleFunction<RecordingVertexConsumer.Vertex> axis) {
        double min = corners.stream().mapToDouble(axis).min().orElseThrow();
        double max = corners.stream().mapToDouble(axis).max().orElseThrow();
        return (float) (max - min);
    }

    /**
     * @param vertices every emitted vertex, four per quad
     * @return each quad's V span as a fraction of the sprite's V range
     */
    private static List<Float> vSpanFractions(List<RecordingVertexConsumer.Vertex> vertices) {
        int quads = vertices.size() / VERTICES_PER_QUAD;
        return java.util.stream.IntStream.range(0, quads)
                .mapToObj(quad -> span(vertices.subList(quad * VERTICES_PER_QUAD, (quad + 1) * VERTICES_PER_QUAD),
                        RecordingVertexConsumer.Vertex::v) / (V1 - V0))
                .toList();
    }

    private static void assertInsideTheSprite(List<RecordingVertexConsumer.Vertex> vertices) {
        assertTrue(vertices.stream().allMatch(vertex -> vertex.v() >= V0 - TOLERANCE && vertex.v() <= V1 + TOLERANCE
                && vertex.u() >= U0 - TOLERANCE && vertex.u() <= U1 + TOLERANCE), "every UV inside the sprite");
    }

    @Test
    void aThreeBlockColumnTilesThreeWholeSprites() {
        List<RecordingVertexConsumer.Vertex> vertices = tile(3f);

        List<Float> fractions = vSpanFractions(vertices);
        assertEquals(3 * SIDES, fractions.size());
        fractions.forEach(fraction -> assertEquals(1f, fraction, TOLERANCE));
        assertInsideTheSprite(vertices);
    }

    @Test
    void aTwoAndAHalfBlockColumnTilesTwoWholeSpritesAndAHalf() {
        List<RecordingVertexConsumer.Vertex> vertices = tile(2.5f);

        List<Float> fractions = vSpanFractions(vertices);
        assertEquals(3 * SIDES, fractions.size());
        fractions.subList(0, 2 * SIDES).forEach(fraction -> assertEquals(1f, fraction, TOLERANCE));
        fractions.subList(2 * SIDES, 3 * SIDES).forEach(fraction -> assertEquals(HALF, fraction, TOLERANCE));
        assertInsideTheSprite(vertices);
    }

    @Test
    void aFlowingColumnSplitsWhereTheScrolledSpriteWrapsAndStaysInsideIt() {
        List<RecordingVertexConsumer.Vertex> vertices = tile(3f, QUARTER);

        List<Float> fractions = vSpanFractions(vertices);
        assertEquals(4 * SIDES, fractions.size());
        fractions.subList(0, SIDES).forEach(fraction -> assertEquals(QUARTER, fraction, TOLERANCE));
        fractions.subList(SIDES, 3 * SIDES).forEach(fraction -> assertEquals(1f, fraction, TOLERANCE));
        fractions.subList(3 * SIDES, 4 * SIDES).forEach(fraction -> assertEquals(1f - QUARTER, fraction, TOLERANCE));
        assertInsideTheSprite(vertices);
    }

    @Test
    void theTextureRunsDownTheColumnAsTimePasses() {
        float earlier = firstWrapY(tile(3f, QUARTER));
        float later = firstWrapY(tile(3f, QUARTER + QUARTER));

        assertTrue(later < earlier, "the sprite's wrap moves down: " + earlier + " then " + later);
        assertTrue(GooStreamRenderer.flowPhase(20f) > GooStreamRenderer.flowPhase(0f), "the phase grows with time");
    }

    @Test
    void theVatStreamTilesThroughTheSameRoutine() {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        GooStreamRenderer.renderStream(new RenderContext(new PoseStack().last(), recorder, 0),
                new GooStreamRenderer.StreamColumn(CENTER, CENTER, TOP, TOP - 2.5f), sprite(), COLOR, 1f, 0f);

        List<Float> fractions = vSpanFractions(recorder.vertices());
        assertEquals(3 * SIDES, fractions.size());
        fractions.subList(0, 2 * SIDES).forEach(fraction -> assertEquals(1f, fraction, TOLERANCE));
        fractions.subList(2 * SIDES, 3 * SIDES).forEach(fraction -> assertEquals(HALF, fraction, TOLERANCE));
        assertInsideTheSprite(recorder.vertices());
    }
}
