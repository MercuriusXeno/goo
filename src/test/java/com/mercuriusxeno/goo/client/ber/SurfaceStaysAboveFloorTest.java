package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aSurfaceGridAndItsDownwardTwinStayAboveTheFloorOfABoxThinnerThanTheirRipple(boolean agitated) {
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(new PoseStack().last(), recorder, 0);
        float floor = VatBlockEntityRenderer.BASE_FLOOR;
        CuboidBounds thin = new CuboidBounds(0.125f, 0.875f, 0.125f, 0.875f, floor, floor + THIN_FILL);
        GooRenderUtil.UvRect uv = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);

        ctx.liquidSurfaceGrid(thin, uv, amplitude(agitated));
        ctx.liquidSurfaceGridDown(thin, uv, amplitude(agitated));

        assertEveryLowestHeightAbove(recorder.vertices(), floor);
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

    private static void assertEveryLowestHeightAbove(List<RecordingVertexConsumer.Vertex> vertices, float floor) {
        assertFalse(vertices.isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : vertices) {
            float lowest = vertex.y() - (float) vertex.uv1U() / RenderContext.AMPLITUDE_UNITS_PER_BLOCK;
            assertTrue(lowest > floor, "vertex " + vertex + " ripples down to " + lowest + ", floor " + floor);
        }
    }
}
