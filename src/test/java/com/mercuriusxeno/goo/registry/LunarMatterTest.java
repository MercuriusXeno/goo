package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's matter, anorthosite, regolith, the iron core and molten iron, registers under the mod's id with the
 * textures its models name, and the shipped base values answer astral and xeno for each, iron for the core and the
 * molten iron.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision astral-matter-values-carry-the-dimensions-types
 */
class LunarMatterTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final int SPRITE_SIZE = 16;

    private static Set<Identifier> registeredIds(DeferredRegister<?> register) {
        return register.getEntries().stream().map(DeferredHolder::getId).collect(Collectors.toSet());
    }

    private static Identifier goo(String path) {
        return Identifier.fromNamespaceAndPath("goo", path);
    }

    private static InputStream resource(String path) {
        InputStream stream = LunarMatterTest.class.getResourceAsStream(path);
        assertNotNull(stream, path + " is not on the classpath");
        return stream;
    }

    private static String resourceText(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject resourceJson(String path) throws IOException {
        return JsonParser.parseString(resourceText(path)).getAsJsonObject();
    }

    private static BufferedImage sprite(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path + " is no image");
            return image;
        }
    }

    @Nested
    class Registrations {

        @Test
        void lunarBlocksRegisterUnderTheModId() {
            assertTrue(registeredIds(GooBlocks.BLOCKS).containsAll(List.of(
                    goo("anorthosite"), goo("regolith"), goo("iron_core"), goo("molten_iron"))),
                    String.valueOf(registeredIds(GooBlocks.BLOCKS)));
        }

        @Test
        void moltenIronRegistersItsSourceItsFlowingAndItsFluidType() {
            assertTrue(registeredIds(GooFluids.FLUIDS).containsAll(List.of(
                    goo("molten_iron"), goo("molten_iron_flowing"))), String.valueOf(registeredIds(GooFluids.FLUIDS)));
            assertTrue(registeredIds(GooFluidTypes.FLUID_TYPES).contains(goo("molten_iron")),
                    String.valueOf(registeredIds(GooFluidTypes.FLUID_TYPES)));
        }

        @Test
        void lunarBlocksAndTheMoltenIronBucketRegisterAsItems() {
            assertTrue(registeredIds(GooItems.ITEMS).containsAll(List.of(
                    goo("anorthosite"), goo("regolith"), goo("iron_core"), goo("molten_iron_bucket"))),
                    String.valueOf(registeredIds(GooItems.ITEMS)));
        }
    }

    @Nested
    class Textures {

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core"})
        void eachLunarBlockDrawsATextureOnTheClasspath(String block) throws IOException {
            List<String> models = modelsOf(block);
            assertEquals("goo:block/" + block, models.getFirst());
            for (String model : models) {
                String texture = resourceJson("/assets/goo/models/block/" + model.substring("goo:block/".length())
                        + ".json").getAsJsonObject("textures").get("all").getAsString();
                BufferedImage image = sprite("/assets/goo/textures/" + texture.substring("goo:".length()) + ".png");
                assertEquals(SPRITE_SIZE, image.getWidth(), texture);
                assertEquals(SPRITE_SIZE, image.getHeight(), texture);
            }
        }

        /**
         * Anorthosite and regolith each pick one of four shared-edge variants per block, unmirrored and unturned.
         * decision lunar-repaints-are-picked-from-drafts-on-a-page
         */
        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith"})
        void groundBlocksPickAmongFourVariantsUnturned(String block) throws IOException {
            JsonElement variants = resourceJson("/assets/goo/blockstates/" + block + ".json")
                    .getAsJsonObject("variants").get("");
            assertEquals(List.of("goo:block/" + block, "goo:block/" + block + "_2", "goo:block/" + block + "_3",
                    "goo:block/" + block + "_4"), modelsOf(block));
            for (JsonElement variant : variants.getAsJsonArray()) {
                assertEquals(Set.of("model"), variant.getAsJsonObject().keySet(), variant.toString());
            }
        }

        private static List<String> modelsOf(String block) throws IOException {
            JsonElement variants = resourceJson("/assets/goo/blockstates/" + block + ".json")
                    .getAsJsonObject("variants").get("");
            List<JsonElement> listed = new ArrayList<>();
            if (variants.isJsonArray()) {
                variants.getAsJsonArray().forEach(listed::add);
            } else {
                listed.add(variants);
            }
            return listed.stream().map(v -> v.getAsJsonObject().get("model").getAsString()).toList();
        }

        @Test
        void moltenIronDrawsAnAnimatedFluidSpriteOnTheClasspath() throws IOException {
            BufferedImage image = sprite("/assets/goo/textures/fluid/molten_iron.png");
            assertEquals(SPRITE_SIZE, image.getWidth());
            assertEquals(0, image.getHeight() % SPRITE_SIZE, "frames stack in 16 pixel rows");
            assertTrue(image.getHeight() > SPRITE_SIZE, "the sprite holds more than one frame");
            assertTrue(resourceJson("/assets/goo/textures/fluid/molten_iron.png.mcmeta").has("animation"));
        }

        /** Flowing molten iron draws its own sprite, twice a still frame's width as lava's flow sprite is. */
        @Test
        void flowingMoltenIronDrawsItsOwnAnimatedSprite() throws IOException {
            BufferedImage image = sprite("/assets/goo/textures/fluid/molten_iron_flow.png");
            assertEquals(2 * SPRITE_SIZE, image.getWidth());
            assertTrue(resourceJson("/assets/goo/textures/fluid/molten_iron_flow.png.mcmeta").has("animation"));
        }

        @Test
        void moltenIronBucketDrawsTheMoltenIronFluid() throws IOException {
            JsonObject model = resourceJson("/assets/goo/items/molten_iron_bucket.json").getAsJsonObject("model");
            assertEquals("goo:molten_iron", model.get("fluid").getAsString());
        }
    }

    @Nested
    class Values {

        private static Map<Identifier, GooValue> shippedBaseValues() throws IOException {
            return GooBaseValuesPreview.load(resourceText(BASE_VALUES)).baseValues();
        }

        private static void assertCarries(String item, ResourceKey<GooTypeDefinition> type) throws IOException {
            GooValue value = shippedBaseValues().get(goo(item));
            assertNotNull(value, "base_values.json values no goo:" + item);
            assertTrue(value.exact(type).isPositive(), "goo:" + item + " carries no " + type.identifier() + ": " + value);
        }

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core", "molten_iron_bucket"})
        void eachLunarMatterCarriesAstral(String item) throws IOException {
            assertCarries(item, GooTypes.ASTRAL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core", "molten_iron_bucket"})
        void eachLunarMatterCarriesXeno(String item) throws IOException {
            assertCarries(item, GooTypes.XENO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "iron_core", "molten_iron_bucket"})
        void theStoneTheCoreAndTheMoltenIronCarryIron(String item) throws IOException {
            assertCarries(item, GooTypes.METAL);
        }

        /** Regolith is glass dust, so it carries crystal. */
        @Test
        void regolithCarriesCrystal() throws IOException {
            assertCarries("regolith", GooTypes.CRYSTAL);
        }
    }
}
