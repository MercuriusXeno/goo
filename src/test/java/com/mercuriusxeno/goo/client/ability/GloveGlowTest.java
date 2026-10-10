package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GloveGlow: the glow around the glove fades in while a glow hold runs and
 * out once it ends, and draws as two shells around the held goo, the inner
 * one brighter.
 * decision radiant-wisps-where-light-is-low
 */
class GloveGlowTest {

    private static final float GOO_HALF = 2.5f / 16f;
    private static final float TOLERANCE = 1e-5f;

    @Test
    void theGlowFadesInWhileHeldAndOutOnceLetGo() {
        int faded = 0;
        for (int tick = 1; tick <= GloveGlow.FADE_TICKS; tick++) {
            faded = GloveGlow.nextFade(faded, true);
            assertEquals(tick, faded);
        }
        assertEquals(GloveGlow.FADE_TICKS, GloveGlow.nextFade(faded, true), "The glow should hold at full");
        for (int tick = 1; tick <= GloveGlow.FADE_TICKS; tick++) {
            faded = GloveGlow.nextFade(faded, false);
        }
        assertEquals(0, faded);
        assertEquals(0, GloveGlow.nextFade(faded, false), "The glow should stay hidden");
    }

    @Test
    void theGlowIsTwoShellsAroundTheGooBrighterWithin() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GloveGlow.emitShells(new PoseStack().last(), consumer, GOO_HALF, 1f, 0f);
        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
        assertEquals(48, vertices.size());
        float outer = Math.abs(vertices.get(0).x());
        float inner = Math.abs(vertices.get(24).x());
        assertEquals(GOO_HALF * GloveGlow.OUTER_SCALE, outer, TOLERANCE);
        assertEquals(GOO_HALF * GloveGlow.INNER_SCALE, inner, TOLERANCE);
        assertTrue(inner > GOO_HALF, "The inner shell should stand clear of the goo");
        assertTrue(ARGB.alpha(vertices.get(24).color()) > ARGB.alpha(vertices.get(0).color()),
                "The inner shell should glow brighter than the outer");
    }

    @Test
    void noGlowDrawsClear() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        GloveGlow.emitShells(new PoseStack().last(), consumer, GOO_HALF, 0f, 0f);
        assertTrue(consumer.vertices().stream().allMatch(v -> ARGB.alpha(v.color()) == 0));
    }
}
