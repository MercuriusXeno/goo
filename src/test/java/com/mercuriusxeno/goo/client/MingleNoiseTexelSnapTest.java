package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mingle field samples at texel resolution: one value per sixteenth-block
 * texel, and crucible_dissolve.fsh reads it through mingle_noise.glsl's snap
 * (decision mingle-noise-snaps-to-texels). Shaders are read through the classpath.
 */
class MingleNoiseTexelSnapTest {

    private static final String SHADERS = "/assets/goo/shaders/core/";
    /** crucible_dissolve.fsh's DISSOLVE_CELLS_PER_BLOCK. */
    private static final float DISSOLVE_CELLS_PER_BLOCK = 4f;

    private static String shader(String name) throws IOException {
        try (InputStream in = MingleNoiseTexelSnapTest.class.getResourceAsStream(SHADERS + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Nested
    class FieldPerTexel {

        @Test
        void twoPointsInsideOneTexelSampleOneValue() {
            float first = MingleNoiseModel.texelField(3.005f, 64.005f, -7.005f, DISSOLVE_CELLS_PER_BLOCK);
            float second = MingleNoiseModel.texelField(3.035f, 64.035f, -7.035f, DISSOLVE_CELLS_PER_BLOCK);

            assertEquals(first, second);
        }

        @Test
        void neighboringTexelsSampleDistinctValues() {
            float before = MingleNoiseModel.texelField(3.06f, 64.005f, -7.005f, DISSOLVE_CELLS_PER_BLOCK);
            float after = MingleNoiseModel.texelField(3.065f, 64.005f, -7.005f, DISSOLVE_CELLS_PER_BLOCK);

            assertNotEquals(before, after);
        }

        @Test
        void aPointSnapsToItsTexelsMiddle() {
            assertEquals(3.03125f, MingleNoiseModel.texelSnap(3.01f, MingleNoiseModel.WORLD_TEXELS));
            assertEquals(-7.03125f, MingleNoiseModel.texelSnap(-7.01f, MingleNoiseModel.WORLD_TEXELS));
        }
    }

    @Nested
    class CrucibleDissolveShader {

        @Test
        void mingleNoiseDefinesTheSnapAtSixteenTexelsPerBlock() throws IOException {
            String noise = shader("mingle_noise.glsl");

            assertTrue(noise.contains("const float MINGLE_WORLD_TEXELS = 16.0;"), "the world texel");
            assertTrue(noise.contains("vec3 mingleTexelSnap(vec3 p, float texelsPerUnit) {\n"
                + "    return (floor(p * texelsPerUnit) + 0.5) / texelsPerUnit;"), "the snap");
        }

        @Test
        void theDissolveSnapsItsPositionBeforeTheFieldAndThePick() throws IOException {
            String dissolve = shader("crucible_dissolve.fsh");
            String snap = "vec3 texelPos = mingleTexelSnap(dissolveWorldPos, MINGLE_WORLD_TEXELS);";
            int snapAt = dissolve.indexOf(snap);
            int fieldAt = dissolve.indexOf("mingleField(texelPos * DISSOLVE_CELLS_PER_BLOCK");
            int pickAt = dissolve.indexOf("mingleOpacity(texelPos * PICK_SCALE");

            assertTrue(snapAt >= 0, "the snap");
            assertTrue(fieldAt > snapAt, "the field samples the snapped position");
            assertTrue(pickAt > snapAt, "the type pick samples the snapped position");
            assertEquals(1, dissolve.split("dissolveWorldPos", -1).length - 2,
                "dissolveWorldPos is read only by the snap");
        }
    }
}
