package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystal cloud casts at most one reflection ray per visible sliver per
 * client tick however many frames draw it, through a probe that stands in
 * for the level's clip (decision render-context-is-the-one-emitter).
 */
class CrystalCloudVisualTest {

    private static final int SLIVERS = 256;
    private static final int VISIBLE = 40;
    private static final int FRAMES_PER_TICK = 3;
    private static final long TICK = 100L;
    private static final int PROBED_RGB = 0x204060;

    /** A probe that counts the rays cast and answers one color. */
    private static final class CountingProbe implements ReflectionSampler.BlockColorProbe {
        private int casts;

        @Override
        public int colorAlong(Vec3 origin, Vec3 direction) {
            casts++;
            return PROBED_RGB;
        }
    }

    private static CrystalCloudVisual.CloudDraw draw(long tick, float time, CountingProbe probe) {
        return new CrystalCloudVisual.CloudDraw(VISIBLE, 200, time, 2f,
                new Vec3(10, 64, 10), new Vec3(0.5, 70, 0.5), tick, probe);
    }

    private static RecordingVertexConsumer emitFrame(CrystalCloudVisual.CloudDraw draw, ReflectionSampler sampler) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        CrystalCloudVisual.emitCloud(new FlatQuadContext(new PoseStack().last(), consumer), draw, sampler);
        return consumer;
    }

    @Test
    void threeFramesInOneTickCastOneRayPerVisibleSliver() {
        CountingProbe probe = new CountingProbe();
        ReflectionSampler sampler = new ReflectionSampler(SLIVERS);

        for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
            emitFrame(draw(TICK, TICK + frame / (float) FRAMES_PER_TICK, probe), sampler);
        }

        assertEquals(VISIBLE, probe.casts);
    }

    @Test
    void theNextTickSamplesEverySliverAgain() {
        CountingProbe probe = new CountingProbe();
        ReflectionSampler sampler = new ReflectionSampler(SLIVERS);

        emitFrame(draw(TICK, TICK, probe), sampler);
        emitFrame(draw(TICK + 1, TICK + 1, probe), sampler);

        assertEquals(2 * VISIBLE, probe.casts);
    }

    @Test
    void everyFaceTakesTheSampledColorAtTheDrawAlpha() {
        CountingProbe probe = new CountingProbe();

        RecordingVertexConsumer consumer = emitFrame(draw(TICK, TICK, probe), new ReflectionSampler(SLIVERS));

        assertTrue(consumer.vertices().size() >= VISIBLE * 3 * 4);
        for (RecordingVertexConsumer.Vertex vertex : consumer.vertices()) {
            assertEquals(200, ARGB.alpha(vertex.color()));
            assertEquals(0x29, ARGB.red(vertex.color()));
            assertEquals(0x53, ARGB.green(vertex.color()));
            assertEquals(0x7C, ARGB.blue(vertex.color()));
        }
    }
}
