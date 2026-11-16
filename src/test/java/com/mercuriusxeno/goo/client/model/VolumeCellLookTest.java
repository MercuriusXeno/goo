package com.mercuriusxeno.goo.client.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the volume cells' look as their shipped block state and model files
 * and their tint state it: every height and every drip draws the cell's goo
 * type's own two-toned fluid sprite on the translucent layer under the shell
 * tint, the palm blob shell's look, in place of a slime texture.
 */
class VolumeCellLookTest {

    private static final String BLOCKSTATE = "/assets/goo/blockstates/%s.json";
    private static final String MODEL = "/assets/goo/models/%s.json";
    private static final String TYPE_JSON = "/data/goo/goo/goo_type/%s.json";
    private static final int SHELL_TINT_INDEX = 0;
    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /**
     * A volume cell's block id beside the binding its block registers with
     * and the two tones its goo type draws in.
     *
     * @param blockId   the block's id path
     * @param binding   the goo type the cell holds
     * @param fluid     the goo type's two-toned fluid sprite
     * @param highlight the goo type's highlight RGB
     */
    record Cell(String blockId, CellBinding binding, String fluid, int highlight) {
    }

    static Stream<Cell> cells() {
        return Stream.of(new Cell("slime_coat", CellBinding.SLIME, "goo:fluid/weird_fluid", 0x5FAE4C),
                new Cell("kinetic_slick", CellBinding.SLICK, "goo:fluid/kinetic_fluid", 0x3C6CFF),
                new Cell("blaze_molten", CellBinding.MOLTEN, "goo:fluid/blaze_fluid", 0xFF750F),
                new Cell("deep_host", CellBinding.HOST, "goo:fluid/deep_fluid", 0x16303E),
                new Cell("frost_rime", CellBinding.RIME, "goo:fluid/frost_fluid", 0xBCE7F5));
    }

    @Nested
    class Models {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.model.VolumeCellLookTest#cells")
        void everyHeightDrawsTheGooTypesFluidUnderTheShellTint(Cell cell) {
            String fluid = fluidStillOf(cell.binding().gooType());
            assertEquals(cell.fluid(), fluid);
            for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
                List<String> shells = partsAt(cell.blockId(), layers, false);
                assertFalse(shells.isEmpty(), cell.blockId() + " draws nothing at " + layers + " layers");
                for (String model : shells) {
                    assertDrawsGoo(model, fluid);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.model.VolumeCellLookTest#cells")
        void everyDripDrawsTheGooTypesFluidUnderTheShellTint(Cell cell) {
            String fluid = fluidStillOf(cell.binding().gooType());
            for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
                for (String model : partsAt(cell.blockId(), layers, true)) {
                    assertDrawsGoo(model, fluid);
                }
            }
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.model.VolumeCellLookTest#cells")
        void everyHeightDrawsThroughTheSlimeCoreModel(Cell cell) {
            for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
                assertEquals(List.of(SlimeCoreModel.TYPE.toString()), bodyTypesAt(cell.blockId(), layers),
                        cell.blockId() + " at " + layers + " layers");
            }
        }

        /**
         * A whole block's shell top stands on the block's face, where a core
         * running up the column meets it, so it culls under the cell above; a
         * lower top stands inside its block and always draws.
         */
        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.model.VolumeCellLookTest#cells")
        void onlyAWholeBlocksShellTopCullsAgainstTheCellAbove(Cell cell) {
            for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
                for (String model : partsAt(cell.blockId(), layers, false)) {
                    String expected = layers == VolumeCellBlock.MAX_LAYERS ? "up" : null;
                    JsonObject top = upFaceOf(model);
                    assertEquals(expected, top.has("cullface") ? top.get("cullface").getAsString() : null,
                            model + "'s top");
                }
            }
        }

        @Test
        void slimeDripsOnEverySideAtEveryHeight() {
            for (int layers = 1; layers <= VolumeCellBlock.MAX_LAYERS; layers++) {
                assertEquals(4, partsAt("slime_coat", layers, true).size(), "drips at " + layers + " layers");
            }
        }
    }

    @Nested
    class Tint {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.model.VolumeCellLookTest#cells")
        void shellTakesTheGooTypesHighlightAtTheShellAlpha(Cell cell) {
            ResourceKey<GooTypeDefinition> type = cell.binding().gooType();
            VolumeCellTint tint = new VolumeCellTint(type, key -> GooColors.highlight(definitionOf(key)));
            assertEquals(ARGB.color(VolumeCellTint.SHELL_ALPHA, cell.highlight()), tint.color(mock(BlockState.class)));
        }
    }

    /**
     * Asserts every face a model draws samples the goo type's fluid sprite
     * under the shell tint on the translucent layer.
     */
    private static void assertDrawsGoo(String model, String fluid) {
        ResolvedModel resolved = resolve(model);
        assertFalse(resolved.faces().isEmpty(), model + " draws no face");
        for (JsonObject face : resolved.faces()) {
            String texture = face.get("texture").getAsString();
            assertEquals(fluid, resolved.texture(texture), model + " samples another texture");
            assertEquals(ChunkSectionLayer.TRANSLUCENT, bakedLayerOf(resolved.material(texture)),
                    model + " bakes onto another layer than the translucent one");
            assertNotNull(face.get("tintindex"), model + " draws a face under no tint");
            assertEquals(SHELL_TINT_INDEX, face.get("tintindex").getAsInt(), model + " takes another tint");
        }
        assertEquals(fluid, resolved.texture("#particle"), model + " breaks into another texture");
    }

    /**
     * The models a block state applies at a height: its body, or its drips.
     */
    private static List<String> partsAt(String blockId, int layers, boolean drips) {
        List<String> models = new ArrayList<>();
        for (JsonElement part : read(BLOCKSTATE.formatted(blockId)).getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            boolean drip = when.keySet().stream().anyMatch(key -> !"layers".equals(key));
            if (String.valueOf(layers).equals(when.get("layers").getAsString()) && drip == drips) {
                JsonObject apply = part.getAsJsonObject().getAsJsonObject("apply");
                JsonObject variant = apply.has("shell") ? apply.getAsJsonObject("shell") : apply;
                models.add(variant.get("model").getAsString());
            }
        }
        return models;
    }

    /**
     * The model types a block state's bodies draw through at a height.
     */
    private static List<String> bodyTypesAt(String blockId, int layers) {
        List<String> types = new ArrayList<>();
        for (JsonElement part : read(BLOCKSTATE.formatted(blockId)).getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            if (when.keySet().equals(java.util.Set.of("layers"))
                    && String.valueOf(layers).equals(when.get("layers").getAsString())) {
                JsonObject apply = part.getAsJsonObject().getAsJsonObject("apply");
                types.add(apply.has("type") ? apply.get("type").getAsString() : "minecraft:variant");
            }
        }
        return types;
    }

    /**
     * The chunk layer a face drawn with a material bakes onto, through the
     * game's own face baking, on a fully opaque sprite as the goo fluid
     * sprites are: the layer decides whether the face draws see-through.
     */
    private static ChunkSectionLayer bakedLayerOf(Material material) {
        SpriteContents opaque = mock(SpriteContents.class);
        when(opaque.computeTransparency(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(Transparency.NONE);
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.contents()).thenReturn(opaque);
        when(sprite.atlasLocation()).thenReturn(BLOCK_ATLAS);
        ModelBaker baker = mock(ModelBaker.class);
        when(baker.interner()).thenReturn(new ModelBaker.Interner() {
            @Override
            public Vector3fc vector(Vector3fc vector) {
                return vector;
            }

            @Override
            public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo info) {
                return info;
            }
        });
        CuboidFace face = new CuboidFace(null, SHELL_TINT_INDEX, "#goo", null, Quadrant.R0, ExtraFaceData.DEFAULT,
                new MutableObject<>());
        return FaceBakery.bakeQuad(baker, new Vector3f(0, 0, 0), new Vector3f(16, 8, 16), face,
                new Material.Baked(sprite, material.forceTranslucent()), Direction.UP, BlockModelRotation.IDENTITY,
                null, true, 0).materialInfo().layer();
    }

    /**
     * The up face of the nearest elements a model or its parents carry.
     */
    private static JsonObject upFaceOf(String model) {
        String next = model;
        while (next != null) {
            JsonObject json = read(MODEL.formatted(next.substring(next.indexOf(':') + 1)));
            if (json.has("elements")) {
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                    if (faces.has("up")) {
                        return faces.getAsJsonObject("up");
                    }
                }
                break;
            }
            next = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        throw new AssertionError(model + " draws no top");
    }

    /**
     * A model with its parents folded in: their textures merged, the
     * nearest elements kept.
     */
    private static ResolvedModel resolve(String model) {
        Map<String, JsonElement> textures = new HashMap<>();
        List<JsonObject> faces = new ArrayList<>();
        String next = model;
        while (next != null) {
            JsonObject json = read(MODEL.formatted(next.substring(next.indexOf(':') + 1)));
            if (json.has("textures")) {
                json.getAsJsonObject("textures").entrySet()
                        .forEach(entry -> textures.putIfAbsent(entry.getKey(), entry.getValue()));
            }
            if (faces.isEmpty() && json.has("elements")) {
                json.getAsJsonArray("elements").forEach(element -> element.getAsJsonObject()
                        .getAsJsonObject("faces").entrySet()
                        .forEach(face -> faces.add(face.getValue().getAsJsonObject())));
            }
            next = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        return new ResolvedModel(textures, faces);
    }

    private record ResolvedModel(Map<String, JsonElement> textures, List<JsonObject> faces) {

        /**
         * The material a texture reference resolves to, read as the game reads a model's texture entry.
         */
        Material material(String reference) {
            JsonElement value = new JsonPrimitive(reference);
            while (value != null && value.isJsonPrimitive() && value.getAsString().startsWith("#")) {
                value = textures.get(value.getAsString().substring(1));
            }
            assertNotNull(value, "unresolved texture " + reference);
            return Material.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow();
        }

        String texture(String reference) {
            return material(reference).sprite().toString();
        }
    }

    private static String fluidStillOf(ResourceKey<GooTypeDefinition> type) {
        return definitionOf(type).textures().fluidStill().orElseThrow().toString();
    }

    private static GooTypeDefinition definitionOf(ResourceKey<GooTypeDefinition> type) {
        return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, read(TYPE_JSON.formatted(GooTypes.id(type))))
                .getOrThrow();
    }

    private static JsonObject read(String path) {
        try (InputStream in = VolumeCellLookTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
