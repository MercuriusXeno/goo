package com.mercuriusxeno.goo.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reads the dark goo types' goo_fluid_types entries, goo_type wheel channels and sprites through the classpath,
 * and converts their colors to HSL.
 */
final class DarkGooResources {

    static final List<String> DARK_TYPES = List.of("nether", "void", "sculk", "astral");
    static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String TEXTURES = "/assets/goo/textures/";

    private DarkGooResources() {
    }

    static String fluidTypesJson() throws IOException {
        try (InputStream in = DarkGooResources.class.getResourceAsStream(FLUID_TYPES_JSON)) {
            assertNotNull(in, "missing on the classpath: " + FLUID_TYPES_JSON);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static JsonObject fluidEntry(String type) throws IOException {
        for (var entry : JsonParser.parseString(fluidTypesJson()).getAsJsonArray()) {
            JsonObject fluid = entry.getAsJsonObject();
            if (type.equals(fluid.get("id").getAsString())) {
                return fluid;
            }
        }
        throw new AssertionError(type + " has no goo_fluid_types entry");
    }

    static FluidTextureGenerator.GooFluidType fluidType(String type) throws IOException {
        return FluidTextureGenerator.parseFluidTypes(fluidTypesJson()).stream()
                .filter(parsed -> type.equals(parsed.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " has no goo_fluid_types entry"));
    }

    static String wheel(String type) throws IOException {
        String path = TYPE_JSON_FORMAT.formatted(type);
        try (InputStream in = DarkGooResources.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().get("wheel").getAsString();
            }
        }
    }

    static BufferedImage texture(String path) throws IOException {
        try (InputStream in = DarkGooResources.class.getResourceAsStream(TEXTURES + path)) {
            assertNotNull(in, "missing on the classpath: " + TEXTURES + path);
            return ImageIO.read(in);
        }
    }

    /**
     * HSL of an RGB color: hue in degrees, saturation and lightness from 0 to 1.
     */
    static float[] hsl(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float lightness = (max + min) / 2f;
        float chroma = max - min;
        if (chroma == 0f) {
            return new float[]{0f, 0f, lightness};
        }
        float saturation = chroma / (1f - Math.abs(2f * lightness - 1f));
        float hue;
        if (max == red) {
            hue = 60f * (((green - blue) / chroma) % 6f);
        } else if (max == green) {
            hue = 60f * ((blue - red) / chroma + 2f);
        } else {
            hue = 60f * ((red - green) / chroma + 4f);
        }
        return new float[]{(hue + 360f) % 360f, saturation, lightness};
    }

    static float[] hsl(String hex) {
        return hsl(Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16));
    }

    static float hueGap(float first, float second) {
        float gap = Math.abs(first - second) % 360f;
        return Math.min(gap, 360f - gap);
    }
}
