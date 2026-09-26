package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that no vat or crucible surface vertex can ripple down to its
 * reservoir's floor at a fill thinner than the ripple (decision
 * diagnose-then-fix-undulation-floor). goo_fluid_surface.vsh lifts a vertex
 * by its amplitude times a ripple in [-1, 1], so its lowest height is its
 * emitted Y minus its decoded amplitude. Before the fix the resting case
 * failed: a vat interior vertex at 0.131 blocks carried the full 0.012-block
 * amplitude, reaching 0.119, under the 0.125 base floor.
 */
class SurfaceStaysAboveFloorTest {

    /** A goo height above the floor thinner than the resting ripple amplitude. */
    private static final float THIN_FILL = RenderContext.RESTING_RIPPLE_AMPLITUDE / 2f;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void vatSurfaceStaysAboveTheBaseFloor(boolean agitated) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        VatRenderState state = mock(VatRenderState.class);
        state.stackSize = 1;
        float localHeight = VatBlockEntityRenderer.CAP_CEILING - VatBlockEntityRenderer.BASE_FLOOR;
        state.fillFraction = THIN_FILL / localHeight;
        state.rippleAmplitude = amplitude(agitated);
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();

        VatFluidRenderer.renderFluid(new RenderContext(new PoseStack().last(), recorder, 0), sprite(), state, 0f);

        assertEveryLowestHeightAbove(surfaceGridVertices(recorder.vertices()), VatBlockEntityRenderer.BASE_FLOOR);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void crucibleSurfaceStaysAboveTheBasinFloor(boolean agitated) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0, 0xFFFFFFFF);

        CrucibleBlockEntityRenderer.emitLiquidSurface(ctx,
            CrucibleBasin.footprintForVolume(CrucibleBasin.SPREAD_VOLUME), CrucibleBasin.FLOOR_Y + THIN_FILL,
            new GooRenderUtil.UvRect(0f, 0f, 1f, 1f), amplitude(agitated));

        assertEveryLowestHeightAbove(recorder.vertices(), CrucibleBasin.FLOOR_Y);
    }

    private static float amplitude(boolean agitated) {
        return RenderContext.RESTING_RIPPLE_AMPLITUDE + (agitated ? SurfaceAgitation.AGITATION_CEILING : 0f);
    }

    /** The vat emits its top grid and downward twin first, then its four side faces. */
    private static List<RecordingVertexConsumer.Vertex> surfaceGridVertices(List<RecordingVertexConsumer.Vertex> all) {
        int gridVertices = RenderContext.SURFACE_GRID_CELLS * RenderContext.SURFACE_GRID_CELLS * 4;
        return all.subList(0, 2 * gridVertices);
    }

    private static void assertEveryLowestHeightAbove(List<RecordingVertexConsumer.Vertex> vertices, float floor) {
        assertFalse(vertices.isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            float lowest = vertex.y() - (float) vertex.uv1U() / RenderContext.AMPLITUDE_UNITS_PER_BLOCK;
            assertTrue(lowest > floor, "vertex " + vertex + " ripples down to " + lowest + ", floor " + floor);
        }
    }

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU0()).thenReturn(0f);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV0()).thenReturn(0f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }
}
