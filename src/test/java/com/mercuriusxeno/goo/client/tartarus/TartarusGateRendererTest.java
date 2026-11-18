package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.ability.zone.ConjuredDoor;
import com.mercuriusxeno.goo.ability.zone.GateLook;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import com.mercuriusxeno.goo.client.zone.ConjuredDoorRenderer;
import com.mercuriusxeno.goo.client.zone.GateDrawing;
import com.mercuriusxeno.goo.client.zone.StarfieldDoorDrawing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Covers Tartarus' own portal drawing: which renderer a Tartarus gate draws through, the texture it names and the
 * texel doorway its vertices outline (decision tartarus-draws-its-own-portal-with-the-nether-portal-texture).
 */
class TartarusGateRendererTest {

    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final GooRenderUtil.UvRect PORTAL_UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.3125f, 0.5625f);
    private static final GooRenderUtil.UvRect FLAT_UV = new GooRenderUtil.UvRect(0.5f, 0.5f, 0.5625f, 0.5625f);
    private static final float EXACT = 1.0E-4f;

    @Nested
    class Dispatch {

        @Test
        void aTartarusPortalDrawsThroughTartarusOwnRenderer() {
            GateDrawing drawing = ConjuredDoorRenderer.drawingFor(GateLook.NETHER_PORTAL);

            assertInstanceOf(TartarusGateRenderer.class, drawing);
            assertFalse(drawing instanceof StarfieldDoorDrawing, "not the End and Zone gate's drawing");
        }

        @Test
        void zoneAndEndKeepTheStarfieldDoorDrawing() {
            assertInstanceOf(StarfieldDoorDrawing.class, ConjuredDoorRenderer.drawingFor(GateLook.STARFIELD));
        }

        @Test
        void theTextureItNamesIsVanillasNetherPortal() {
            assertEquals(Identifier.withDefaultNamespace("block/nether_portal"), TartarusGateRenderer.TEXTURE);
        }
    }

    @Nested
    class Doorway {

        @Test
        void aWholeDoorwayFillsExactlyTheVanillaPortalsOneByTwo() {
            List<RecordingVertexConsumer.Vertex> doorway = membrane(submit(drawn(1, ConjuredDoor.Phase.ENVELOPING)));

            assertFalse(doorway.isEmpty(), "the doorway drew");
            assertEquals(-0.5, doorway.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).min().orElseThrow(),
                    EXACT);
            assertEquals(0.5, doorway.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).max().orElseThrow(),
                    EXACT);
            assertEquals(0, doorway.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow(), EXACT);
            assertEquals(2, doorway.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow(), EXACT);
        }

        @Test
        void everyEdgeStandsOnTheTexelGrid() {
            for (double time : new double[] {0, 3.3, 7.9}) {
                for (RecordingVertexConsumer.Vertex vertex : submit(drawn(0.5, ConjuredDoor.Phase.GROWING), time)) {
                    assertEquals(0, offGrid(vertex.x()), EXACT, "x on the grid at tick " + time + ": " + vertex.x());
                    assertEquals(0, offGrid(vertex.y()), EXACT, "y on the grid at tick " + time + ": " + vertex.y());
                }
            }
        }

        @Test
        void itDrawsOnlyThePortalSpriteAndFlatMotes() {
            List<RecordingVertexConsumer.Vertex> vertices = submit(drawn(1, ConjuredDoor.Phase.ENVELOPING));

            assertTrue(vertices.stream().allMatch(v -> within(PORTAL_UV, v) || within(FLAT_UV, v)), "nothing else");
            assertTrue(membrane(vertices).stream().allMatch(v -> v.color() == 0xFFFFFFFF), "the texture untinted");
        }

        @Test
        void aDoorwayNotYetAppearingDrawsNothing() {
            assertTrue(submit(drawn(0, ConjuredDoor.Phase.GROWING)).isEmpty());
        }

        @Test
        void aGoingDoorwayCoversLessThanAWholeOne() {
            double whole = frontArea(membrane(submit(drawn(1, ConjuredDoor.Phase.RELEASING))));
            double going = frontArea(membrane(submit(drawn(0.4, ConjuredDoor.Phase.RELEASING))));

            assertEquals(2, whole, EXACT, "the whole one-by-two");
            assertTrue(going < whole, going + " below " + whole);
        }
    }

    private static boolean within(GooRenderUtil.UvRect uv, RecordingVertexConsumer.Vertex vertex) {
        return vertex.u() >= uv.u0() - EXACT && vertex.u() <= uv.u1() + EXACT && vertex.v() >= uv.v0() - EXACT
                && vertex.v() <= uv.v1() + EXACT;
    }

    private static List<RecordingVertexConsumer.Vertex> membrane(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().filter(v -> within(PORTAL_UV, v)).toList();
    }

    /** The area the quads' front faces cover: each quad's first four of its eight vertices. */
    private static double frontArea(List<RecordingVertexConsumer.Vertex> vertices) {
        double area = 0;
        for (int quad = 0; quad + 8 <= vertices.size(); quad += 8) {
            List<RecordingVertexConsumer.Vertex> front = vertices.subList(quad, quad + 4);
            double wide = front.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).max().orElseThrow()
                    - front.stream().mapToDouble(RecordingVertexConsumer.Vertex::x).min().orElseThrow();
            double tall = front.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow()
                    - front.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow();
            area += wide * tall;
        }
        return area;
    }

    private static double offGrid(double blocks) {
        double texels = blocks * 16;
        return Math.abs(texels - Math.rint(texels)) / 16;
    }

    private static ClientConjuredDoors.Drawn drawn(double unzipped, ConjuredDoor.Phase phase) {
        return new ClientConjuredDoors.Drawn(FEET, 0f, unzipped, 0, GateLook.NETHER_PORTAL, phase);
    }

    private static List<RecordingVertexConsumer.Vertex> submit(ClientConjuredDoors.Drawn door) {
        return submit(door, 0);
    }

    /** Submits one doorway seen from its own feet and records the vertices it emitted. */
    private static List<RecordingVertexConsumer.Vertex> submit(ClientConjuredDoors.Drawn door, double time) {
        RecordingVertexConsumer vertices = new RecordingVertexConsumer();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            PoseStack at = call.getArgument(0);
            SubmitNodeCollector.CustomGeometryRenderer renderer = call.getArgument(2);
            renderer.render(at.last(), vertices);
            return null;
        }).when(collector).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        TartarusGateRenderer.submitWith(collector, new PoseStack(), door, FEET, time, PORTAL_UV, FLAT_UV);
        return vertices.vertices();
    }
}
