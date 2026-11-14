package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast's charge draws its motes as wisps, a core cube inside a larger halo
 * cube in Unstable's green, the wisps around the blast zone larger than the
 * blob's, which fade in close around the held blob
 * (decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob).
 */
class ChargeWispsTest {

    /** A cube's six faces of four corners. */
    private static final int CUBE_VERTICES = 24;
    private static final Vec3 ROOTED = new Vec3(10.5, 64, -3.5);
    private static final Vec3 BLOB = new Vec3(0.4, 65.3, 0.2);
    private static final long BEGAN = 5_000L;
    private static final int FULL_CHARGE = 100;
    private static final double CLOSE_TO_THE_BLOB = 0.6;
    /** A packed color's red, green and blue, its alpha masked off. */
    private static final int RGB = 0xFFFFFF;

    private static List<RecordingVertexConsumer.Vertex> drawn(float haloHalf) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        ChargeMotes.emitWisp(new PoseStack().last(), consumer, haloHalf, 1f);
        return consumer.vertices();
    }

    private static float halfOf(List<RecordingVertexConsumer.Vertex> cube) {
        return (float) cube.stream().mapToDouble(vertex -> Math.abs(vertex.x())).max().orElse(0);
    }

    private static void assertAHaloAroundACore(float haloHalf) {
        List<RecordingVertexConsumer.Vertex> vertices = drawn(haloHalf);
        assertEquals(2 * CUBE_VERTICES, vertices.size(), "a wisp should draw two cubes");
        List<RecordingVertexConsumer.Vertex> halo = vertices.subList(0, CUBE_VERTICES);
        List<RecordingVertexConsumer.Vertex> core = vertices.subList(CUBE_VERTICES, 2 * CUBE_VERTICES);
        assertTrue(halo.stream().allMatch(vertex -> (vertex.color() & RGB) == ChargeMotes.UNSTABLE_GREEN),
                "the halo should be Unstable's green");
        assertTrue(halfOf(halo) > halfOf(core), "the halo should be larger than the core");
    }

    @Test
    void aShellWispIsACoreInsideAGreenHalo() {
        assertAHaloAroundACore(ChargeMotes.SHELL_HALO_HALF);
    }

    @Test
    void aBlobWispIsACoreInsideAGreenHalo() {
        assertAHaloAroundACore(ChargeMotes.BLOB_HALO_HALF);
    }

    @Test
    void theShellsWispsAreLargerThanTheBlobs() {
        assertTrue(ChargeMotes.SHELL_HALO_HALF > ChargeMotes.BLOB_HALO_HALF);
    }

    @Test
    void theBlobsWispsFadeInCloseAroundTheBlob() {
        RandomSource random = RandomSource.create(11L);
        for (int tick = 0; tick < FULL_CHARGE; tick++) {
            for (ChargeMotes.Mote mote : ChargeMotes.spawn(BEGAN + tick, ROOTED, 2 + tick / 20.0, BLOB, random)) {
                if (mote.to().equals(BLOB)) {
                    assertTrue(mote.from().distanceTo(BLOB) < CLOSE_TO_THE_BLOB,
                            "a blob wisp faded in " + mote.from().distanceTo(BLOB) + " blocks from the blob");
                }
            }
        }
    }
}
