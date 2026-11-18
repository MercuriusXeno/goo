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
 * (decision dart-flutes-draw-as-fins), each rooted on the body and drawn on both faces, the body closed at its
 * tail and the sprite lying one sprite a block on every face; checked over a table of directions.
 */
class TravelFormGeometryTest {

    private static final float TOLERANCE = 1e-4f;
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final int LIGHT = 0x00F000F0;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.75f, 0.875f);
    /** The shortest edge a stretch is read across, past the rounding of a point where a cone closes. */
    private static final float STRETCH_EDGE_LEAST = 1e-3f;
    /** How far an edge's texture may stray from its length, as a share of it: the cone's slant against its axis. */
    private static final float STRETCH_WITHIN = 0.05f;

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
        }
    }

    // The body's radius at a place along the shaft: the shaft's behind the cone's base, the cone narrowing ahead of it.
    private static float bodyRadiusAt(float axial) {
        float radius = TravelForm.DART.frontRadius();
        return axial <= 0f ? radius : radius * (1f - axial / TravelForm.DART.frontLength());
    }

    private static List<RecordingVertexConsumer.Vertex> emitted(Vector3f dir) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        TravelFormGeometry.emit(new RenderContext(new PoseStack().last(), consumer, LIGHT),
                TravelForm.DART, dir, 1f, UV);
        return consumer.vertices();
    }

    private static Vector3f at(RecordingVertexConsumer.Vertex v) {
        return new Vector3f(v.x(), v.y(), v.z());
    }

    // dart-flutes-draw-as-fins: each fin grows out of the body, not out of its axis inside it
    @ParameterizedTest
    @MethodSource("directions")
    void eachFinRootsOnTheBodysSurface(Vector3f dir) {
        for (TravelFormGeometry.Fin fin : TravelFormGeometry.fins(TravelForm.DART, dir, 1f)) {
            for (Vector3f root : List.of(fin.rootLead(), fin.rootTrail())) {
                assertEquals(bodyRadiusAt(root.dot(dir)), radialOf(root, dir).length(), TOLERANCE,
                        "a fin's root should stand on the body's surface at " + root.dot(dir));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void theBodyIsClosedAtItsTail(Vector3f dir) {
        float tail = -TravelForm.DART.fins().sweep();
        List<RecordingVertexConsumer.Vertex> vertices = emitted(dir);
        boolean closed = false;
        for (int quad = 0; quad + 3 < vertices.size(); quad += 4) {
            List<RecordingVertexConsumer.Vertex> face = vertices.subList(quad, quad + 4);
            boolean atTheTail = face.stream().allMatch(v -> Math.abs(at(v).dot(dir) - tail) < TOLERANCE);
            boolean facingBack = new Vector3f(face.getFirst().nx(), face.getFirst().ny(), face.getFirst().nz())
                    .dot(dir) < -1 + TOLERANCE;
            closed |= atTheTail && facingBack;
        }
        assertTrue(closed, "a face should close the body at its tail, facing back");
        assertTrue(vertices.stream().allMatch(v -> at(v).dot(dir) > tail - TOLERANCE),
                "nothing should stand behind the tail");
    }

    @ParameterizedTest
    @MethodSource("directions")
    void eachFinShowsFromBothFaces(Vector3f dir) {
        List<RecordingVertexConsumer.Vertex> vertices = emitted(dir);
        for (TravelFormGeometry.Fin fin : TravelFormGeometry.fins(TravelForm.DART, dir, 1f)) {
            List<Vector3f> normals = vertices.stream()
                    .filter(v -> at(v).distance(fin.tipTrail()) < TOLERANCE)
                    .map(v -> new Vector3f(v.nx(), v.ny(), v.nz())).toList();
            assertTrue(normals.stream().anyMatch(n -> normals.stream().anyMatch(m -> n.dot(m) < -1 + TOLERANCE)),
                    "a fin should draw on both its faces, normals " + normals);
        }
    }

    // The sprite lies on every face at one sprite a block, so no face stretches it.
    @ParameterizedTest
    @MethodSource("directions")
    void theTextureLiesOneSpriteABlockOnEveryFace(Vector3f dir) {
        float spanU = UV.u1() - UV.u0();
        float spanV = UV.v1() - UV.v0();
        List<RecordingVertexConsumer.Vertex> vertices = emitted(dir);
        for (int quad = 0; quad + 3 < vertices.size(); quad += 4) {
            for (int corner = 0; corner < 4; corner++) {
                RecordingVertexConsumer.Vertex from = vertices.get(quad + corner);
                RecordingVertexConsumer.Vertex to = vertices.get(quad + (corner + 1) % 4);
                float edge = at(from).distance(at(to));
                if (edge > STRETCH_EDGE_LEAST) {
                    float texture = (float) Math.hypot((to.u() - from.u()) / spanU, (to.v() - from.v()) / spanV);
                    assertEquals(edge, texture, edge * STRETCH_WITHIN, "an edge " + edge
                            + " blocks long should take " + edge + " sprites of texture, took " + texture);
                }
            }
        }
    }
}