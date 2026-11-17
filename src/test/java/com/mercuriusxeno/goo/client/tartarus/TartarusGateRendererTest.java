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
 * rift its vertices outline (decision tartarus-draws-its-own-portal-with-the-nether-portal-texture).
 */
class TartarusGateRendererTest {

    private static final Vec3 FEET = new Vec3(3.5, 64, 8.5);
    private static final GooRenderUtil.UvRect PORTAL_UV = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.3125f, 0.5625f);
    private static final GooRenderUtil.UvRect RIM_UV = new GooRenderUtil.UvRect(0.5f, 0.5f, 0.5625f, 0.5625f);
    private static final float EXACT = 1.0E-4f;
    /** The rift's head room past two blocks: its flicker and its swell, and a cell of rounding. */
    private static final double TALLEST = 2.15 * 1.15 + 0.08 + 1.0 / 64 + EXACT;

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
    class Rift {

        @Test
        void aFullyOpenRiftStandsOnTheFeetAndRisesPastTwoBlocksWithinAHalfBlockEachSide() {
            List<RecordingVertexConsumer.Vertex> vertices = membrane(submit(drawn(1, 0, ConjuredDoor.Phase.ENVELOPING)));

            assertFalse(vertices.isEmpty(), "the rift emitted its membrane");
            double highest = vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElseThrow();
            double lowest = vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).min().orElseThrow();
            assertEquals(0, lowest, EXACT, "standing on the feet");
            assertTrue(highest > 2 && highest <= TALLEST, "rising past two blocks: " + highest);
            for (RecordingVertexConsumer.Vertex vertex : vertices) {
                assertTrue(Math.abs(vertex.x()) <= 0.5 + 0.06 + EXACT, "within a half block and its flicker: "
                        + vertex.x());
            }
        }

        @Test
        void itsMembraneSamplesTheNetherPortalSpriteAndItsRimTheHotSprite() {
            List<RecordingVertexConsumer.Vertex> vertices = submit(drawn(1, 0, ConjuredDoor.Phase.ENVELOPING));

            assertTrue(vertices.stream().anyMatch(v -> within(PORTAL_UV, v)), "membrane on the portal sprite");
            assertTrue(vertices.stream().anyMatch(v -> within(RIM_UV, v)), "rim on the hot sprite");
            assertTrue(vertices.stream().allMatch(v -> within(PORTAL_UV, v) || within(RIM_UV, v)), "nothing else");
        }

        /** The artifact draft drew the rift's edges on a grid of 64 cells a block, four to each texel. */
        @Test
        void everyEdgeStandsOnTheDraftsQuarterTexelGrid() {
            for (double time : new double[] {0, 3.3, 7.9}) {
                for (RecordingVertexConsumer.Vertex vertex : submit(drawn(1, 0, ConjuredDoor.Phase.ENVELOPING), time)) {
                    assertEquals(0, offGrid(vertex.x()), EXACT, "x on the grid at tick " + time + ": " + vertex.x());
                    assertEquals(0, offGrid(vertex.y()), EXACT, "y on the grid at tick " + time + ": " + vertex.y());
                }
            }
        }

        /** The artifact draft's rim is flat hot orange, not a texture. */
        @Test
        void theRimIsFlatHotOrange() {
            List<RecordingVertexConsumer.Vertex> rim = submit(drawn(1, 0, ConjuredDoor.Phase.ENVELOPING), 0).stream()
                    .filter(v -> within(RIM_UV, v)).toList();

            assertFalse(rim.isEmpty(), "a rim drawn");
            assertTrue(rim.stream().anyMatch(v -> ((v.color() >> 16) & 0xFF) == 255 && (v.color() & 0xFF) == 50),
                    "flat hot orange, red full and blue low, over a white texel: " + rim.getFirst().color());
        }

        @Test
        void aRiftNotYetOpenDrawsNothing() {
            assertTrue(submit(drawn(0, 0, ConjuredDoor.Phase.GROWING)).isEmpty());
        }

        @Test
        void aClosingRiftSinksLowerThanAnOpenOne() {
            double open = tallest(membrane(submit(drawn(1, 0, ConjuredDoor.Phase.RELEASING))));
            double sinking = tallest(membrane(submit(drawn(0.4, 0, ConjuredDoor.Phase.RELEASING))));

            assertTrue(sinking < open, sinking + " sinks below " + open);
        }
    }

    private static boolean within(GooRenderUtil.UvRect uv, RecordingVertexConsumer.Vertex vertex) {
        return vertex.u() >= uv.u0() - EXACT && vertex.u() <= uv.u1() + EXACT && vertex.v() >= uv.v0() - EXACT
                && vertex.v() <= uv.v1() + EXACT;
    }

    private static List<RecordingVertexConsumer.Vertex> membrane(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().filter(v -> within(PORTAL_UV, v)).toList();
    }

    private static double tallest(List<RecordingVertexConsumer.Vertex> vertices) {
        return vertices.stream().mapToDouble(RecordingVertexConsumer.Vertex::y).max().orElse(0);
    }

    private static ClientConjuredDoors.Drawn drawn(double unzipped, double widened, ConjuredDoor.Phase phase) {
        return new ClientConjuredDoors.Drawn(FEET, 0f, unzipped, widened, GateLook.NETHER_PORTAL, phase);
    }

    private static double offGrid(double blocks) {
        double cells = blocks * 64;
        return Math.abs(cells - Math.rint(cells)) / 64;
    }

    private static List<RecordingVertexConsumer.Vertex> submit(ClientConjuredDoors.Drawn door) {
        return submit(door, 0);
    }

    /** Submits one rift seen from its own feet and records the vertices it emitted. */
    private static List<RecordingVertexConsumer.Vertex> submit(ClientConjuredDoors.Drawn door, double time) {
        RecordingVertexConsumer vertices = new RecordingVertexConsumer();
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);
        doAnswer(call -> {
            PoseStack at = call.getArgument(0);
            SubmitNodeCollector.CustomGeometryRenderer renderer = call.getArgument(2);
            renderer.render(at.last(), vertices);
            return null;
        }).when(collector).submitCustomGeometry(any(PoseStack.class), any(RenderType.class), any());

        TartarusGateRenderer.submitWith(collector, new PoseStack(), door, FEET, time, PORTAL_UV, RIM_UV);
        return vertices.vertices();
    }
}
