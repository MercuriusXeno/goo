package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every face of every crystal shard with a usable normal casts its own
 * reflection ray every frame, from that frame's camera, and takes the color
 * its own ray finds.
 */
class CrystalCloudVisualTest {

    private static final int VISIBLE = 40;
    private static final int FRAMES = 3;
    private static final int QUAD_VERTICES = 4;
    private static final int ALPHA = 200;
    /** The sky-blue a face too thin to carry a normal takes without casting a ray. */
    private static final int SKY_FALLBACK = ARGB.color(ALPHA, 0x87CEEB);

    /** A probe that records every ray cast and answers a color keyed to its direction. */
    private static final class RecordingProbe implements CrystalCloudVisual.BlockColorProbe {
        private final List<Vec3> directions = new ArrayList<>();

        @Override
        public int colorAlong(Vec3 origin, Vec3 direction) {
            directions.add(direction);
            return colorFor(direction);
        }

        static int colorFor(Vec3 direction) {
            return ARGB.color(0,
                    (int) ((direction.x + 1) * 60),
                    (int) ((direction.y + 1) * 60),
                    (int) ((direction.z + 1) * 60));
        }
    }

    private static CrystalCloudVisual.CloudDraw draw(Vec3 camPos, RecordingProbe probe) {
        return new CrystalCloudVisual.CloudDraw(VISIBLE, ALPHA, 100f, 2f,
                new Vec3(10, 64, 10), camPos, probe);
    }

    private static List<RecordingVertexConsumer.Vertex> emitFrame(CrystalCloudVisual.CloudDraw draw) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        CrystalCloudVisual.emitCloud(new FlatQuadContext(new PoseStack().last(), consumer), draw);
        return consumer.vertices();
    }

    @Test
    void everyFaceCastsItsOwnRayEveryFrame() {
        RecordingProbe probe = new RecordingProbe();
        int reflectingFaces = 0;

        for (int frame = 0; frame < FRAMES; frame++) {
            List<RecordingVertexConsumer.Vertex> vertices = emitFrame(draw(new Vec3(0.5, 70, 0.5), probe));
            for (int face = 0; face < vertices.size(); face += QUAD_VERTICES) {
                if (vertices.get(face).color() != SKY_FALLBACK) {
                    reflectingFaces++;
                }
            }
        }

        assertTrue(reflectingFaces >= FRAMES * VISIBLE);
        assertEquals(reflectingFaces, probe.directions.size());
    }

    @Test
    void theFacesOfOneShardTakeTheirOwnReflections() {
        RecordingProbe probe = new RecordingProbe();

        List<RecordingVertexConsumer.Vertex> vertices = emitFrame(draw(new Vec3(0.5, 70, 0.5), probe));

        Set<Integer> firstShardColors = new HashSet<>();
        for (int face = 0; face < 3; face++) {
            firstShardColors.add(vertices.get(face * QUAD_VERTICES).color());
        }
        assertTrue(firstShardColors.size() > 1, "the first shard's faces share one color");
    }

    @Test
    void movingTheCameraBetweenFramesMovesTheReflections() {
        RecordingProbe before = new RecordingProbe();
        RecordingProbe after = new RecordingProbe();

        emitFrame(draw(new Vec3(0.5, 70, 0.5), before));
        emitFrame(draw(new Vec3(3.5, 70, 0.5), after));

        assertEquals(before.directions.size(), after.directions.size());
        assertTrue(!before.directions.get(0).equals(after.directions.get(0)));
    }
}
