package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drink field shader marches the same field DrinkField states and reads
 * the block DrinkUpload packs: its constants equal the Java's and its uniform
 * arrays are as long as the upload's caps (decision
 * unmake-waves-dissolve-by-crucible-cost). Read through the classpath.
 */
class DrinkShaderTest {

    private static final String FRAGMENT = "/assets/goo/shaders/core/drink_field.fsh";
    private static final String VERTEX = "/assets/goo/shaders/core/drink_field.vsh";
    private static final double DELTA = 1e-9;
    private static final int IVEC4 = 4;

    private static String source(String path) throws IOException {
        try (InputStream in = DrinkShaderTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static double constant(String source, String name) {
        Matcher matcher = Pattern.compile("const (?:float|int) " + name + " = ([-0-9.e]+);").matcher(source);
        assertTrue(matcher.find(), name);
        return Double.parseDouble(matcher.group(1));
    }

    private static int arrayLength(String source, String name) {
        Matcher matcher = Pattern.compile("i?vec4 " + name + "\\[(\\d+)\\];").matcher(source);
        assertTrue(matcher.find(), name);
        return Integer.parseInt(matcher.group(1));
    }

    @Test
    void theShadersFieldConstantsAreTheJavasAndItReadsTheUploadsBlock() throws IOException {
        String fragment = source(FRAGMENT);

        assertEquals(DrinkField.DEPTH, constant(fragment, "DEPTH"), DELTA);
        assertEquals(DrinkField.REACH, constant(fragment, "REACH"), DELTA);
        assertEquals(DrinkField.FULL_RADIUS, constant(fragment, "FULL_RADIUS"), DELTA);
        assertEquals(DrinkStream.THINNEST, constant(fragment, "THINNEST"), DELTA);
        assertEquals(DrinkRenderer.GOO_REACH, (float) constant(fragment, "GOO_REACH"), (float) DELTA);
        assertEquals(DrinkUpload.RUN_START, constant(fragment, "RUN_START"), DELTA);
        assertEquals(DrinkUpload.BOX_BASE, constant(fragment, "BOX_BASE"), DELTA);
        assertEquals(DrinkUpload.REGION, constant(fragment, "REGION_SPAN"), DELTA);
        assertEquals(DrinkUpload.STREAM_VEC4S, constant(fragment, "STREAM_VEC4S"), DELTA);
        assertEquals(DrinkUpload.COAT_SLOT, constant(fragment, "COAT_SLOT"), DELTA);
        assertEquals(DrinkUpload.TINT_SLOT, constant(fragment, "TINT_SLOT"), DELTA);
        assertEquals(DrinkUpload.SPRITE_SLOT, constant(fragment, "SPRITE_SLOT"), DELTA);
        assertEquals(DrinkUpload.SIDE_SLOT, constant(fragment, "SIDE_SLOT"), DELTA);
        assertEquals(DrinkUpload.ACROSS_SLOT, constant(fragment, "ACROSS_SLOT"), DELTA);
        assertEquals(DrinkUpload.LAYER_TINT_SLOT, constant(fragment, "LAYER_TINT_SLOT"), DELTA);
        assertEquals(DrinkUpload.LAYER_SPRITE_SLOT, constant(fragment, "LAYER_SPRITE_SLOT"), DELTA);
        assertEquals(DrinkUpload.LAYER_SHARE_SLOT, constant(fragment, "LAYER_SHARE_SLOT"), DELTA);
        assertEquals(DrinkUpload.MOST_STREAMS * DrinkUpload.STREAM_VEC4S, arrayLength(fragment, "Streams"));
        assertEquals(DrinkUpload.MOST_ENTRIES / IVEC4, arrayLength(fragment, "Table"));
        assertEquals(2 * DrinkUpload.MOST_BOXES, arrayLength(fragment, "Boxes"));
        assertEquals(2 * DrinkUpload.MOST_RINGS, arrayLength(fragment, "Rings"));
        assertTrue(fragment.contains("gl_FragDepth ="), "the hit writes its depth");
    }

    @Test
    void theVertexShaderHandsTheFragmentTheRayToMarch() throws IOException {
        String vertex = source(VERTEX);

        assertTrue(vertex.contains("out vec3 rayPoint;"));
        assertTrue(vertex.contains("rayPoint = Position;"));
    }
}
