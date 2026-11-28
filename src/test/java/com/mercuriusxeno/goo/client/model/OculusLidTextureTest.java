package com.mercuriusxeno.goo.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The texels the oculus's lid row models sample are shaded hide, holding several colors rather than one solid color.
 * decision oculus-eyelid-is-shaded-hide-with-a-seam
 */
class OculusLidTextureTest {

    private static final String MODEL_DIR = "/assets/goo/models/block/";
    private static final String TEXTURE = "/assets/goo/textures/block/oculus_lid.png";
    private static final int FEWEST_COLORS = 4;

    @Test
    void theLidRowsSampleShadedHide() throws IOException {
        BufferedImage lid = image(TEXTURE);
        Set<Integer> colors = new HashSet<>();
        for (int row = 0; row < OculusModels.LID_ROWS; row++) {
            JsonObject model = json(MODEL_DIR + OculusModels.lidRowName(row) + ".json");
            for (JsonElement element : model.getAsJsonArray("elements")) {
                for (JsonElement face : element.getAsJsonObject().getAsJsonObject("faces").asMap().values()) {
                    addSampled(lid, face.getAsJsonObject().getAsJsonArray("uv"), colors);
                }
            }
        }
        assertTrue(colors.size() >= FEWEST_COLORS,
                "The lid rows should sample at least " + FEWEST_COLORS + " colors, they sample " + colors.size());
    }

    private static void addSampled(BufferedImage image, JsonArray uv, Set<Integer> colors) {
        int scale = image.getWidth() / 16;
        int left = (int) Math.min(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * scale;
        int right = (int) Math.max(uv.get(0).getAsDouble(), uv.get(2).getAsDouble()) * scale;
        int top = (int) Math.min(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * scale;
        int bottom = (int) Math.max(uv.get(1).getAsDouble(), uv.get(3).getAsDouble()) * scale;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                colors.add(image.getRGB(x, y));
            }
        }
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream in = OculusLidTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return ImageIO.read(in);
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = OculusLidTextureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
