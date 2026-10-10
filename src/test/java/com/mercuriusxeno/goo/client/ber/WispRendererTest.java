package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispRenderer: a wisp draws as a cube, and neighboring wisps float at their
 * own phases and paces, never in step.
 * decision radiant-wisps-where-light-is-low
 */
class WispRendererTest {

    private static final long PACKED = 0x0000_1234_5678_9ABCL;
    private static final float TOLERANCE = 1e-5f;
    private static final int NEIGHBORS = 8;

    @Test
    void aWispIsACube() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        WispRenderer.emitCube(new PoseStack().last(), consumer, 0.5f, 0xFFFFFFFF);
        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
        assertEquals(24, vertices.size());
        for (RecordingVertexConsumer.Vertex v : vertices) {
            assertEquals(0.5f, Math.abs(v.x()), TOLERANCE);
            assertEquals(0.5f, Math.abs(v.y()), TOLERANCE);
            assertEquals(0.5f, Math.abs(v.z()), TOLERANCE);
        }
    }

    @Test
    void neighboringWispsTakeTheirOwnPhaseAndPace() {
        Set<Float> phases = new HashSet<>();
        Set<Float> paces = new HashSet<>();
        for (long step = 0; step < NEIGHBORS; step++) {
            long seed = WispRenderer.seedOf(PACKED + step);
            phases.add(WispRenderer.phase(seed));
            paces.add(WispRenderer.pace(seed));
        }
        assertEquals(NEIGHBORS, phases.size());
        assertEquals(NEIGHBORS, paces.size());
    }

    @Test
    void aPaceStaysWithinItsBounds() {
        for (long step = 0; step < NEIGHBORS; step++) {
            float pace = WispRenderer.pace(WispRenderer.seedOf(PACKED + step));
            assertTrue(pace >= WispRenderer.SLOWEST_BOB && pace <= WispRenderer.SLOWEST_BOB + WispRenderer.BOB_SPREAD);
        }
    }

    @Test
    void aFreshWispFadesInFromNothingOverTheFadeIn() {
        assertEquals(0f, WispRenderer.fadeIn(0f), TOLERANCE);
        assertEquals(1f, WispRenderer.fadeIn(WispRenderer.FADE_IN_TICKS), TOLERANCE);
        assertEquals(1f, WispRenderer.fadeIn(WispRenderer.FADE_IN_TICKS * 3f), TOLERANCE);
        float before = 0f;
        for (int tick = 1; tick <= WispRenderer.FADE_IN_TICKS; tick++) {
            float share = WispRenderer.fadeIn(tick);
            assertTrue(share > before, "The fade-in should rise every tick, not on tick " + tick);
            before = share;
        }
        assertEquals(0.5f, WispRenderer.fadeIn(WispRenderer.FADE_IN_TICKS / 2f), TOLERANCE);
    }

    @Test
    void twoNeighborsFloatOutOfStep() {
        long first = WispRenderer.seedOf(PACKED);
        long second = WispRenderer.seedOf(PACKED + 1);
        assertNotEquals(WispRenderer.bob(first, 100f), WispRenderer.bob(second, 100f));
    }
}
