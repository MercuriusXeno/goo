package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shield travel form stands as a heater plate square to its direction: as wide as its plate across the
 * shoulders, narrowing to a quarter of that at the foot, as tall as its plate, and nothing at no morph.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 */
class ShieldPlateGeometryTest {

    private static final float TOLERANCE = 1e-4f;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
    private static final TravelForm.Plate PLATE = TravelForm.SHIELD.plate();

    @Test
    void theShouldersSpanThePlatesWidthAndTheFootAQuarterOfIt() {
        float[][] outline = TravelFormGeometry.plateOutline(PLATE, 1f);
        assertEquals(PLATE.width(), outline[1][0] - outline[0][0], TOLERANCE);
        assertEquals(PLATE.width() / 4, outline[3][0] - outline[4][0], TOLERANCE);
        assertEquals(PLATE.height(), outline[0][1] - outline[3][1], TOLERANCE);
    }

    @Test
    void thePlateStandsSquareToItsDirection() {
        RecordingVertexConsumer recorded = new RecordingVertexConsumer();
        Vector3f along = new Vector3f(0f, 0f, 1f);
        TravelFormGeometry.emit(new RenderContext(new PoseStack().last(), recorded, 0), TravelForm.SHIELD, along, 1f,
                UV);
        assertTrue(recorded.vertices().stream().allMatch(v -> Math.abs(v.z()) <= PLATE.depth() / 2 + TOLERANCE),
                "a vertex stands off the plate along its direction");
        assertTrue(!recorded.vertices().isEmpty(), "the plate drew nothing");
    }

    @Test
    void noMorphDrawsNothing() {
        RecordingVertexConsumer recorded = new RecordingVertexConsumer();
        TravelFormGeometry.emit(new RenderContext(new PoseStack().last(), recorded, 0), TravelForm.SHIELD,
                new Vector3f(0f, 0f, 1f), 0f, UV);
        assertTrue(recorded.vertices().isEmpty(), "a plate at no morph drew");
    }
}
