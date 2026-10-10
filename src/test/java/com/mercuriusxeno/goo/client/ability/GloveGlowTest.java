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
 * out once it ends, and draws as a disc bright at the hand and clear at its rim.
 * decision radiant-wisps-where-light-is-low
 */
class GloveGlowTest {

    private static final float RADIUS = 0.2f;
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
    void theDiscIsBrightAtTheHandAndClearAtItsRim() {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        int center = ARGB.color(110, 0xFFE07A);
        GloveGlow.emitDisc(new PoseStack().last(), consumer, RADIUS, center);
        List<RecordingVertexConsumer.Vertex> vertices = consumer.vertices();
        assertTrue(vertices.size() > 0);
        for (RecordingVertexConsumer.Vertex v : vertices) {
            float reach = (float) Math.hypot(v.x(), v.y());
            assertEquals(0f, v.z(), TOLERANCE);
            if (reach < TOLERANCE) {
                assertEquals(center, v.color());
            } else {
                assertEquals(RADIUS, reach, TOLERANCE);
                assertEquals(0, ARGB.alpha(v.color()));
            }
        }
    }
}
