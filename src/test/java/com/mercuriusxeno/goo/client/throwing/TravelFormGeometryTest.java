package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ability.ConeGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dart travel form's tail draws flat fins, evenly spaced around the shaft
 * and each lying in a plane holding the shaft's axis, and no rear cone
 * (decision dart-flutes-draw-as-fins); checked over a table of directions.
 */
class TravelFormGeometryTest {

    private static final float TOLERANCE = 1e-4f;
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final int LIGHT = 0x00F000F0;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.75f, 0.875f);

    static Stream<Vector3f> directions() {
        return Stream.of(
                new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f),
                new Vector3f(0f, -1f, 0f),
                new Vector3f(0f, 0f, 1f),
                new Vector3f(0.6f, 0.48f, 0.64f),
                new Vector3f(-0.36f, -0.48f, 0.8f));
    }

    private static List<Vector3f> corners(TravelFormGeometry.Fin fin) {
        return List.of(fin.rootLead(), fin.rootTrail(), fin.tipTrail(), fin.tipLead());
    }

    private static Vector3f radialOf(Vector3f point, Vector3f dir) {
        return new Vector3f(point).sub(new Vector3f(dir).mul(point.dot(dir)));
    }

    @ParameterizedTest
    @MethodSource("directions")
    void dartTailHoldsAtLeastThreeFins(Vector3f dir) {
        assertTrue(TravelFormGeometry.fins(TravelForm.DART, dir, 1f).size() >= 3);
    }

    @ParameterizedTest
    @MethodSource("directions")
    void finsStandEvenlySpacedAroundTheShaft(Vector3f dir) {
        List<TravelFormGeometry.Fin> fins = TravelFormGeometry.fins(TravelForm.DART, dir, 1f);
        float[] basis = ConeGeometry.computeBasis(dir.x, dir.y, dir.z);
        Vector3f perp = new Vector3f(basis[ConeGeometry.PERP_X], basis[ConeGeometry.PERP_Y], basis[ConeGeometry.PERP_Z]);
        Vector3f cross = new Vector3f(basis[ConeGeometry.CROSS_X], basis[ConeGeometry.CROSS_Y],
                basis[ConeGeometry.CROSS_Z]);
        List<Float> angles = new ArrayList<>();
        for (TravelFormGeometry.Fin fin : fins) {
            Vector3f radial = radialOf(fin.tipTrail(), dir);
            angles.add((float) Math.atan2(radial.dot(cross), radial.dot(perp)));
        }

        assertTrue(fins.size() >= 3);
        float step = TWO_PI / fins.size();
        for (int i = 0; i < angles.size(); i++) {
            float gap = angles.get((i + 1) % angles.size()) - angles.get(i);
            float wrapped = (float) ((gap % TWO_PI + TWO_PI) % TWO_PI);
            assertEquals(step, wrapped, TOLERANCE, "gap after fin " + i);
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void eachFinLiesInOnePlaneHoldingTheShaftAxis(Vector3f dir) {
        List<TravelFormGeometry.Fin> fins = TravelFormGeometry.fins(TravelForm.DART, dir, 1f);

        assertTrue(fins.size() >= 3);
        for (TravelFormGeometry.Fin fin : fins) {
            Vector3f radial = radialOf(fin.tipTrail(), dir);
            assertTrue(radial.length() > TOLERANCE, "the fin stands out from the shaft");
            Vector3f planeNormal = new Vector3f(dir).cross(radial).normalize();
            for (Vector3f corner : corners(fin)) {
                assertEquals(0f, corner.dot(planeNormal), TOLERANCE, "corner off the fin's plane");
            }
            assertTrue(fin.rootLead().dot(dir) - fin.rootTrail().dot(dir) > TOLERANCE,
                    "the root lies along the shaft");
            assertEquals(0f, radialOf(fin.rootLead(), dir).length(), TOLERANCE, "root on the axis");
            assertEquals(0f, radialOf(fin.rootTrail(), dir).length(), TOLERANCE, "root on the axis");
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void dartEmitsTheFrontConeAndFinsAndNoRearCone(Vector3f dir) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        TravelFormGeometry.emit(new RenderContext(new PoseStack().last(), consumer, LIGHT),
                TravelForm.DART, dir, 1f, UV);
        List<TravelFormGeometry.Fin> fins = TravelFormGeometry.fins(TravelForm.DART, dir, 1f);
        List<Vector3f> finCorners = fins.stream().flatMap(fin -> corners(fin).stream()).toList();

        assertTrue(fins.size() >= 3);
        assertEquals(TravelFormGeometry.FRONT_SIDES * 4 + fins.size() * 4, consumer.vertices().size());
        for (RecordingVertexConsumer.Vertex v : consumer.vertices()) {
            Vector3f at = new Vector3f(v.x(), v.y(), v.z());
            if (at.dot(dir) < -TOLERANCE) {
                assertTrue(finCorners.stream().anyMatch(corner -> corner.distance(at) < TOLERANCE),
                        "a vertex behind the shaft that no fin holds: " + at);
            }
        }
    }
}
