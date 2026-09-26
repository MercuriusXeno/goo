package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a point inside a patch of the upper goo type reads that type at
 * every displacement the undulation applies there (decision
 * diagnose-then-fix-undulation-blend-exposure). The surface pipeline writes
 * depth and draws layer 0 whole, so a point reads the upper type only while
 * the upper layer's rasterized, rippled height stays above layer 0's; where
 * it dips under, layer 0 shows at full colour inside the upper patch. Each
 * point is sampled across ripple phases and world origins, lifting every
 * vertex as goo_fluid_surface.vsh does. Before the fix the vat's upper grid
 * sat outward of layer 0's in XZ and dipped 2.9e-4 blocks under it by a
 * wall, and a thin crucible puddle's upper layer carried a larger capped
 * amplitude that closed the gap to a depth tie in the troughs.
 */
class UndulationKeepsLayerOrderTest {

    private static final double TAU = 2.0 * Math.PI;
    /** Must match goo_fluid_surface.vsh. */
    private static final double PRIMARY_CYCLES_PER_DAY = 600.0;
    private static final double SECONDARY_CYCLES_PER_DAY = 420.0;
    private static final double PRIMARY_WAVENUMBER = 9.0;
    private static final double SECONDARY_WAVENUMBER = 13.0;

    /**
     * The least height the upper layer must keep over layer 0: a gap near
     * zero is a depth tie the rasterizer settles either way, so it exposes
     * layer 0 as surely as a dip.
     */
    private static final float MIN_CLEARANCE = TypeBand.LAYER_LIFT / 4f;
    private static final int POINTS_PER_SIDE = 48;
    private static final int PHASES = 240;
    /** One primary cycle is 1/600 of a day, so this span sweeps several of both waves. */
    private static final float PHASE_SPAN_DAYS = 1f / 60f;
    private static final float[] WORLD_ORIGINS = {0f, 1f, 7f, 23f};
    private static final Map<ResourceKey<GooTypeDefinition>, Integer> TWO_TYPES =
        Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750);

    @ParameterizedTest
    @ValueSource(floats = {0.005f, 0.5f})
    void vatUpperPatchReadsItsTypeAcrossTheRipple(float fillFraction) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        VatRenderState state = mock(VatRenderState.class);
        state.stackSize = 1;
        state.fillFraction = fillFraction;
        state.rippleAmplitude = agitatedAmplitude();
        state.typeBands = TypeBands.over(new GooContents(TWO_TYPES));

        assertUpperLayerStaysOnTop(layers(submitter -> VatFluidRenderer.renderMingledFluid(submitter, state)));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, CrucibleBasin.SPREAD_VOLUME, 16_000L, CrucibleBasin.RIM_VOLUME})
    void crucibleUpperPatchReadsItsTypeAcrossTheRipple(long volume) {
        CrucibleRenderState state = mock(CrucibleRenderState.class);
        state.rippleAmplitude = agitatedAmplitude();
        state.typeBands = TypeBands.over(new GooContents(TWO_TYPES));

        assertUpperLayerStaysOnTop(layers(submitter -> CrucibleBlockEntityRenderer.renderMingledSurface(submitter,
            state, CrucibleBasin.footprintForVolume(volume), CrucibleBasin.surfaceYForVolume(volume))));
    }

    private static float agitatedAmplitude() {
        return RenderContext.RESTING_RIPPLE_AMPLITUDE + SurfaceAgitation.AGITATION_CEILING;
    }

    private static List<List<RecordingVertexConsumer.Vertex>> layers(Consumer<BandedSurfaceSubmitter> render) {
        List<List<RecordingVertexConsumer.Vertex>> layers = new ArrayList<>();
        render.accept((band, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            emitter.accept(RenderContext.banded(new PoseStack().last(), recorder,
                GooRenderUtil.OPAQUE_WHITE, band), sprite());
            layers.add(upwardQuads(recorder.vertices()));
        });
        return layers;
    }

    private static void assertUpperLayerStaysOnTop(List<List<RecordingVertexConsumer.Vertex>> layers) {
        assertEquals(2, layers.size());
        List<RecordingVertexConsumer.Vertex> lower = layers.get(0);
        List<RecordingVertexConsumer.Vertex> upper = layers.get(1);
        float x0 = extreme(lower, true, false);
        float x1 = extreme(lower, true, true);
        float z0 = extreme(lower, false, false);
        float z1 = extreme(lower, false, true);
        for (float origin : WORLD_ORIGINS) {
            for (int t = 0; t < PHASES; t++) {
                float gameTime = PHASE_SPAN_DAYS * t / PHASES;
                for (int i = 1; i < POINTS_PER_SIDE; i++) {
                    for (int j = 1; j < POINTS_PER_SIDE; j++) {
                        float x = x0 + (x1 - x0) * i / POINTS_PER_SIDE;
                        float z = z0 + (z1 - z0) * j / POINTS_PER_SIDE;
                        float gap = height(upper, x, z, origin, gameTime) - height(lower, x, z, origin, gameTime);
                        assertTrue(gap >= MIN_CLEARANCE, "the upper layer clears layer 0 by only " + gap + " at x=" + x
                            + " z=" + z + " origin=" + origin + " gameTime=" + gameTime
                            + ", exposing layer 0 inside the upper patch");
                    }
                }
            }
        }
    }

    private static List<RecordingVertexConsumer.Vertex> upwardQuads(List<RecordingVertexConsumer.Vertex> all) {
        List<RecordingVertexConsumer.Vertex> upward = new ArrayList<>();
        for (int q = 0; q + 3 < all.size(); q += 4) {
            if (all.get(q).ny() > 0f) {
                upward.addAll(all.subList(q, q + 4));
            }
        }
        return upward;
    }

    private static float extreme(List<RecordingVertexConsumer.Vertex> vertices, boolean alongX, boolean highest) {
        float best = highest ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            float value = alongX ? vertex.x() : vertex.z();
            best = highest ? Math.max(best, value) : Math.min(best, value);
        }
        return best;
    }

    /** The rasterized height at (x, z), each quad drawn as the triangles 0-1-2 and 2-3-0. */
    private static float height(List<RecordingVertexConsumer.Vertex> quads, float x, float z,
                                float origin, float gameTime) {
        for (int q = 0; q < quads.size(); q += 4) {
            Float first = triangleHeight(List.of(quads.get(q), quads.get(q + 1), quads.get(q + 2)),
                x, z, origin, gameTime);
            if (first != null) {
                return first;
            }
            Float second = triangleHeight(List.of(quads.get(q + 2), quads.get(q + 3), quads.get(q)),
                x, z, origin, gameTime);
            if (second != null) {
                return second;
            }
        }
        throw new AssertionError("no quad covers " + x + ", " + z);
    }

    private static Float triangleHeight(List<RecordingVertexConsumer.Vertex> corners, float x, float z,
                                        float origin, float gameTime) {
        RecordingVertexConsumer.Vertex a = corners.get(0);
        RecordingVertexConsumer.Vertex b = corners.get(1);
        RecordingVertexConsumer.Vertex c = corners.get(2);
        float det = (b.z() - c.z()) * (a.x() - c.x()) + (c.x() - b.x()) * (a.z() - c.z());
        float wa = ((b.z() - c.z()) * (x - c.x()) + (c.x() - b.x()) * (z - c.z())) / det;
        float wb = ((c.z() - a.z()) * (x - c.x()) + (a.x() - c.x()) * (z - c.z())) / det;
        float wc = 1f - wa - wb;
        float edgeTolerance = -1e-6f;
        if (wa < edgeTolerance || wb < edgeTolerance || wc < edgeTolerance) {
            return null;
        }
        return wa * lifted(a, origin, gameTime) + wb * lifted(b, origin, gameTime) + wc * lifted(c, origin, gameTime);
    }

    /** goo_fluid_surface.vsh's lift of one vertex. */
    private static float lifted(RecordingVertexConsumer.Vertex vertex, float origin, float gameTime) {
        float amplitude = vertex.uv1U() / (float) RenderContext.AMPLITUDE_UNITS_PER_BLOCK;
        return vertex.y() + amplitude * ripple(vertex.x() + origin, vertex.z() + origin, gameTime);
    }

    /** goo_fluid_surface.vsh's ripple. */
    private static float ripple(float x, float z, float gameTime) {
        double primary = Math.sin((x * 0.8 + z * 0.6) * PRIMARY_WAVENUMBER
            + gameTime * TAU * PRIMARY_CYCLES_PER_DAY);
        double secondary = Math.sin((x * -0.5 + z * 0.87) * SECONDARY_WAVENUMBER
            - gameTime * TAU * SECONDARY_CYCLES_PER_DAY);
        return (float) (0.5 * (primary + secondary));
    }

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }
}
