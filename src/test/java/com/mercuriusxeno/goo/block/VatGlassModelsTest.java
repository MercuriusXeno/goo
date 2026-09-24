package com.mercuriusxeno.goo.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the vat glass models against vat_body.png under decision
 * vat-glass-sprite-full-height and connected-segments-sample-strip-one-to-one:
 * the solo world models draw the uv the item model draws, the connected models
 * sample the connected strip one texel per row, and no side face samples a
 * transparent row at its edges.
 */
class VatGlassModelsTest {

    private static final String MODELS = "/assets/goo/models/block/";
    private static final String GLASS_TEXTURE = "/assets/goo/textures/block/vat_body.png";
    private static final int TEXELS_PER_UV = 4;
    private static final double CONNECTED_FIRST_COLUMN = 14;
    private static final double CONNECTED_END_COLUMN = 28;
    private static final int CONNECTED_END_ROW = 36;
    private static final int[] TOP_ROWS = {0, 14};
    private static final int[] MIDDLE_ROWS = {14, 30};
    private static final int[] BOTTOM_ROWS = {22, 36};
    private static final int HIGHLIGHT_CHANNEL_FLOOR = 235;
    private static final List<String> SIDES = List.of("north", "south", "east", "west");
    private static final Map<String, String> INNER_ELEMENT_WALL = Map.of(
            "inner_north", "north", "inner_south", "south", "inner_west", "west", "inner_east", "east");

    @Test
    void soloBody_sideFacesMatchItemModel() throws Exception {
        JsonObject itemFaces = elementNamed(loadModel("vat.json"), "vat_body").getAsJsonObject("faces");
        JsonObject bodyFaces = elementNamed(loadModel("vat_body.json"), "vat_body").getAsJsonObject("faces");
        for (String side : SIDES) {
            assertEquals(uvOf(itemFaces, side), uvOf(bodyFaces, side), "vat_body " + side);
        }
    }

    @Test
    void soloInner_inwardFacesMatchItemModelOnTheSameWall() throws Exception {
        JsonObject itemFaces = elementNamed(loadModel("vat.json"), "vat_body").getAsJsonObject("faces");
        for (JsonElement element : loadModel("vat_inner.json").getAsJsonArray("elements")) {
            JsonObject wall = element.getAsJsonObject();
            String wallSide = INNER_ELEMENT_WALL.get(wall.get("name").getAsString());
            assertNotNull(wallSide, "unknown inner element " + wall.get("name"));
            JsonObject faces = wall.getAsJsonObject("faces");
            assertEquals(1, faces.size(), "one inward face per wall");
            String inwardSide = faces.keySet().iterator().next();
            assertEquals(uvOf(itemFaces, wallSide), uvOf(faces, inwardSide), "vat_inner " + wallSide + " wall");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"vat_body.json", "vat_inner.json",
            "vat_body_top.json", "vat_body_middle.json", "vat_body_bottom.json",
            "vat_inner_top.json", "vat_inner_middle.json", "vat_inner_bottom.json"})
    void sideFaces_edgeRowsAreOpaque(String modelFile) throws Exception {
        BufferedImage glass = loadGlassTexture();
        for (JsonElement element : loadModel(modelFile).getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (String side : SIDES) {
                if (faces.has(side)) {
                    assertEdgeRowsOpaque(glass, uvOf(faces, side), modelFile + " " + side);
                }
            }
        }
    }

    /**
     * Each connected model samples the connected strip at one texel per row,
     * over its variant's rows, mirrored as vat.json mirrors the wall's face.
     */
    @ParameterizedTest
    @ValueSource(strings = {"vat_body_top.json", "vat_body_middle.json", "vat_body_bottom.json",
            "vat_inner_top.json", "vat_inner_middle.json", "vat_inner_bottom.json"})
    void connectedModel_samplesItsVariantRowsOneToOne(String modelFile) throws Exception {
        int[] rows = connectedRowsOf(modelFile);
        JsonObject itemFaces = elementNamed(loadModel("vat.json"), "vat_body").getAsJsonObject("faces");
        for (JsonElement element : loadModel(modelFile).getAsJsonArray("elements")) {
            JsonObject part = element.getAsJsonObject();
            JsonObject faces = part.getAsJsonObject("faces");
            double faceHeight = part.getAsJsonArray("to").get(1).getAsDouble()
                    - part.getAsJsonArray("from").get(1).getAsDouble();
            for (String side : SIDES) {
                if (!faces.has(side)) {
                    continue;
                }
                String wallSide = INNER_ELEMENT_WALL.getOrDefault(part.get("name").getAsString(), side);
                String label = modelFile + " " + side;
                double[] texels = texelsOf(uvOf(faces, side));
                boolean itemMirrored = isMirrored(texelsOf(uvOf(itemFaces, wallSide)));
                assertEquals(itemMirrored, isMirrored(texels), label + " mirror");
                assertEquals(CONNECTED_FIRST_COLUMN, Math.min(texels[0], texels[2]), label + " u start");
                assertEquals(CONNECTED_END_COLUMN, Math.max(texels[0], texels[2]), label + " u end");
                assertEquals(rows[0], texels[1], label + " first row");
                assertEquals(rows[1], texels[3], label + " end row");
                assertEquals(faceHeight, texels[3] - texels[1], label + " rows per face texel");
            }
        }
    }

    /**
     * The middle rows hold plain glass alone, and the highlight lies wholly
     * inside the top rows, so tiled middles never cut the highlight off.
     */
    @Test
    void connectedStrip_highlightLiesWhollyInTopRows() throws Exception {
        BufferedImage glass = loadGlassTexture();
        for (int y = MIDDLE_ROWS[0]; y < MIDDLE_ROWS[1]; y++) {
            for (int x = (int) CONNECTED_FIRST_COLUMN; x < CONNECTED_END_COLUMN; x++) {
                assertTrue(alphaAt(glass, x, y) > 0, "middle row " + y + " clear at x " + x);
                assertTrue(!isHighlight(glass, x, y), "middle row " + y + " highlight at x " + x);
            }
        }
        for (int y = 1; y < CONNECTED_END_ROW; y++) {
            for (int x = (int) CONNECTED_FIRST_COLUMN; x < CONNECTED_END_COLUMN; x++) {
                assertTrue(!isHighlight(glass, x, y) || y < TOP_ROWS[1],
                        "highlight at x " + x + " row " + y + " outside the top rows");
            }
        }
    }

    private static int[] connectedRowsOf(String modelFile) {
        if (modelFile.endsWith("_top.json")) {
            return TOP_ROWS;
        }
        if (modelFile.endsWith("_middle.json")) {
            return MIDDLE_ROWS;
        }
        return BOTTOM_ROWS;
    }

    private static boolean isMirrored(double[] texels) {
        return texels[0] > texels[2];
    }

    private static boolean isHighlight(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        return ((rgb >> 16) & 0xFF) > HIGHLIGHT_CHANNEL_FLOOR
                && ((rgb >> 8) & 0xFF) > HIGHLIGHT_CHANNEL_FLOOR
                && (rgb & 0xFF) > HIGHLIGHT_CHANNEL_FLOOR;
    }

    private static double[] texelsOf(JsonArray uv) {
        double[] texels = new double[4];
        for (int i = 0; i < 4; i++) {
            texels[i] = uv.get(i).getAsDouble() * TEXELS_PER_UV;
        }
        return texels;
    }

    private static void assertEdgeRowsOpaque(BufferedImage glass, JsonArray uv, String label) {
        double[] texels = texelsOf(uv);
        int firstColumn = (int) Math.floor(Math.min(texels[0], texels[2]));
        int endColumn = (int) Math.ceil(Math.max(texels[0], texels[2]));
        int firstRow = (int) Math.floor(Math.min(texels[1], texels[3]));
        int lastRow = (int) Math.ceil(Math.max(texels[1], texels[3])) - 1;
        for (int x = firstColumn; x < endColumn; x++) {
            assertTrue(alphaAt(glass, x, firstRow) > 0, label + " top row " + firstRow + " clear at x " + x);
            assertTrue(alphaAt(glass, x, lastRow) > 0, label + " bottom row " + lastRow + " clear at x " + x);
        }
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static JsonArray uvOf(JsonObject faces, String side) {
        assertTrue(faces.has(side), "missing face " + side);
        return faces.getAsJsonObject(side).getAsJsonArray("uv");
    }

    private static JsonObject elementNamed(JsonObject model, String name) {
        for (JsonElement element : model.getAsJsonArray("elements")) {
            if (name.equals(element.getAsJsonObject().get("name").getAsString())) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("no element named " + name);
    }

    private static JsonObject loadModel(String file) throws Exception {
        try (InputStream in = VatGlassModelsTest.class.getResourceAsStream(MODELS + file)) {
            assertNotNull(in, "Model missing on classpath: " + file);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static BufferedImage loadGlassTexture() throws Exception {
        try (InputStream in = VatGlassModelsTest.class.getResourceAsStream(GLASS_TEXTURE)) {
            assertNotNull(in, "Texture missing on classpath: " + GLASS_TEXTURE);
            return ImageIO.read(in);
        }
    }
}
