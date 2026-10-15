package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests that a goo ball of two types submits one unit sphere carrying both
 * type bands, at scale 1 rather than a shell per type (decision one-pass-blends-every-type).
 */
class GooBallTest {

    private static final float RADIUS_TOLERANCE = 1e-5f;

    @Test
    void twoTypesSubmitOneUnitSphereCarryingBoth() {
        GooContents goo = new GooContents(Map.of(GooTypes.BLAZE, 300, GooTypes.FROST, 700));
        List<List<TypeBand>> submitted = new ArrayList<>();
        RecordingVertexConsumer recorder = new RecordingVertexConsumer();

        GooBall.submitSphere((bands, emitter) -> {
            submitted.add(bands);
            emitter.accept(new RenderContext(new PoseStack().last(), recorder, 0));
        }, goo);

        assertEquals(List.of(TypeBands.over(goo)), submitted);
        assertFalse(recorder.vertices().isEmpty());
        for (RecordingVertexConsumer.Vertex vertex : recorder.vertices()) {
            float radius = (float) Math.sqrt(vertex.x() * vertex.x() + vertex.y() * vertex.y() + vertex.z() * vertex.z());
            assertEquals(1f, radius, RADIUS_TOLERANCE, "a vertex stands off the unit sphere at " + vertex);
        }
    }
}
