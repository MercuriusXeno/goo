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
 * Lunar's matter, anorthosite, regolith, the iron core, molten iron, meteoric iron, impact glass, breccia and
 * crater ice and native iron, registers under the mod's id with the textures its models name, and the shipped base values answer astral and xeno for each, iron
 * for the stone, the core, the molten iron and the meteoric iron, crystal for the regolith and the impact glass.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 * decision astral-matter-values-carry-the-dimensions-types
 * decision native-iron-lodes-are-worth-whole-iron-blocks
 * decision astral-crystal-is-lunars-scarce-rich-block
 */
class LunarMatterTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final int SPRITE_SIZE = 16;
    private static final int IRON_BLOCK_NUGGETS = 81;
    private static final int ASTRAL_CRYSTAL_MULTIPLE = 8;

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
                    goo("anorthosite"), goo("regolith"), goo("iron_core"), goo("molten_iron"), goo("meteoric_iron"),
                    goo("impact_glass"), goo("breccia"), goo("crater_ice"), goo("native_iron"),
                    goo("astral_crystal"))),
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
                    goo("anorthosite"), goo("regolith"), goo("iron_core"), goo("molten_iron_bucket"),
                    goo("meteoric_iron"), goo("impact_glass"), goo("breccia"),
                    goo("crater_ice"), goo("native_iron"), goo("astral_crystal"))),
                    String.valueOf(registeredIds(GooItems.ITEMS)));
        }
    }

    /** Native iron and astral crystal each drop themselves when mined. */
    @ParameterizedTest
    @ValueSource(strings = {"native_iron", "astral_crystal"})
    void lunarFindsDropThemselves(String block) throws IOException {
        JsonObject entry = resourceJson("/data/goo/loot_table/blocks/" + block + ".json").getAsJsonArray("pools").get(0)
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("goo:" + block, entry.get("name").getAsString());
    }

    @Nested
    class Textures {

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core", "meteoric_iron", "impact_glass", "breccia",
            "crater_ice", "native_iron"})
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
         * Each picked lunar block, ground and landform, picks one of four variants per block, unmirrored and unturned.
         * decision lunar-repaints-are-picked-from-drafts-on-a-page
         */
        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "meteoric_iron", "impact_glass", "breccia", "crater_ice"})
        void pickedBlocksPickAmongFourVariantsUnturned(String block) throws IOException {
            JsonElement variants = resourceJson("/assets/goo/blockstates/" + block + ".json")
                    .getAsJsonObject("variants").get("");
            assertEquals(List.of("goo:block/" + block, "goo:block/" + block + "_2", "goo:block/" + block + "_3",
                    "goo:block/" + block + "_4"), modelsOf(block));
            for (JsonElement variant : variants.getAsJsonArray()) {
                assertEquals(Set.of("model"), variant.getAsJsonObject().keySet(), variant.toString());
            }
        }

        /**
         * Native iron picks one of six variants per block, unmirrored and unturned, the operator having asked for
         * more variants than four.
         * decision lunar-repaints-are-picked-from-drafts-on-a-page
         */
        @Test
        void nativeIronPicksAmongSixVariantsUnturned() throws IOException {
            assertEquals(LunarPaletteTest.variantsOf("native_iron", 6).stream().map(sprite -> "goo:block/" + sprite)
                    .toList(), modelsOf("native_iron"));
            JsonElement variants = resourceJson("/assets/goo/blockstates/native_iron.json")
                    .getAsJsonObject("variants").get("");
            for (JsonElement variant : variants.getAsJsonArray()) {
                assertEquals(Set.of("model"), variant.getAsJsonObject().keySet(), variant.toString());
            }
        }

        /**
         * Crater ice draws translucent at vanilla ice's alpha, 190, the operator having ruled it too opaque; a model
         * naming no render type draws solid whatever its sprite's alpha.
         */
        @Test
        void craterIceDrawsTranslucentAtVanillaIcesAlpha() throws IOException {
            for (String model : modelsOf("crater_ice")) {
                String name = model.substring("goo:block/".length());
                JsonObject json = resourceJson("/assets/goo/models/block/" + name + ".json");
                assertTrue(json.has("render_type"), name + " names no render type");
                assertEquals("minecraft:translucent", json.get("render_type").getAsString(), name);
                BufferedImage image = sprite("/assets/goo/textures/block/" + name + ".png");
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        assertEquals(190, image.getRGB(x, y) >>> 24, name + " alpha at " + x + "," + y);
                    }
                }
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
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core", "molten_iron_bucket", "meteoric_iron",
            "impact_glass", "breccia", "crater_ice", "native_iron"})
        void eachLunarMatterCarriesAstral(String item) throws IOException {
            assertCarries(item, GooTypes.ASTRAL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "regolith", "iron_core", "molten_iron_bucket", "meteoric_iron",
            "impact_glass", "breccia", "crater_ice", "native_iron"})
        void eachLunarMatterCarriesXeno(String item) throws IOException {
            assertCarries(item, GooTypes.XENO);
        }

        @ParameterizedTest
        @ValueSource(strings = {"anorthosite", "iron_core", "molten_iron_bucket", "meteoric_iron"})
        void theStoneTheCoreAndTheMoltenIronCarryIron(String item) throws IOException {
            assertCarries(item, GooTypes.METAL);
        }

        /**
         * A native iron lode pays whole iron blocks, so one block of it carries at least an iron block's metal, the
         * metal of the 81 nuggets an iron block crafts from.
         * decision native-iron-lodes-are-worth-whole-iron-blocks
         */
        @Test
        void nativeIronCarriesAtLeastAnIronBlocksMetal() throws IOException {
            Map<Identifier, GooValue> values = shippedBaseValues();
            GooValue nativeIron = values.get(goo("native_iron"));
            GooValue nugget = values.get(Identifier.withDefaultNamespace("iron_nugget"));
            assertNotNull(nativeIron, "base_values.json values no goo:native_iron");
            assertNotNull(nugget, "base_values.json values no iron_nugget");
            GooValue ironBlock = nugget.multiply(IRON_BLOCK_NUGGETS);
            assertTrue(nativeIron.exact(GooTypes.METAL).compareTo(ironBlock.exact(GooTypes.METAL)) >= 0,
                    "goo:native_iron " + nativeIron + " carries less metal than iron_block " + ironBlock);
        }

        /**
         * Astral crystal is lunar's scarce rich block: one carries at least eight times anorthosite's astral and xeno.
         * decision astral-crystal-is-lunars-scarce-rich-block
         */
        @Test
        void astralCrystalCarriesEightTimesAnorthositesAstralAndXeno() throws IOException {
            Map<Identifier, GooValue> values = shippedBaseValues();
            GooValue crystal = values.get(goo("astral_crystal"));
            GooValue anorthosite = values.get(goo("anorthosite"));
            assertNotNull(crystal, "base_values.json values no goo:astral_crystal");
            GooValue floor = anorthosite.multiply(ASTRAL_CRYSTAL_MULTIPLE);
            for (ResourceKey<GooTypeDefinition> type : List.of(GooTypes.ASTRAL, GooTypes.XENO)) {
                assertTrue(crystal.exact(type).compareTo(floor.exact(type)) >= 0,
                        "goo:astral_crystal " + crystal + " carries under eight anorthosite's " + type.identifier());
            }
        }

        /** Regolith is glass dust and impact glass is the regolith melted, so both carry crystal. */
        @ParameterizedTest
        @ValueSource(strings = {"regolith", "impact_glass"})
        void theGlassMatterCarriesCrystal(String item) throws IOException {
            assertCarries(item, GooTypes.CRYSTAL);
        }
    }
}
