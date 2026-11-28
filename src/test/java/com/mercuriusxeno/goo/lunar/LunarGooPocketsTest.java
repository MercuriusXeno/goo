package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule choosing a lunar goo pocket's goo type answers astral in every lunar surface biome, frost in the shadowed
 * basins alone, xeno on eldritch ground alone, crystal in the highlands alone and metal in maria alone; and each
 * surface biome names the goo pockets, spaced apart.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
 */
class LunarGooPocketsTest {

    private static final int ROLLS = 1000;
    private static final long SEED = 1838L;
    private static final String GOO_POCKETS = "goo:goo_pockets";

    /**
     * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
     */
    @Test
    void mariaAnswersAstralAndMetal() {
        assertEquals(Set.of(GooTypes.ASTRAL, GooTypes.METAL), rolledTypes(LunarGooPockets.MARIA));
    }

    /**
     * decision crystal-and-metal-goo-spawn-in-the-highlands-and-maria
     */
    @Test
    void highlandsAnswerAstralAndCrystal() {
        assertEquals(Set.of(GooTypes.ASTRAL, GooTypes.CRYSTAL), rolledTypes(LunarGooPockets.HIGHLANDS));
    }

    @Test
    void shadowedBasinsAnswerAstralAndFrost() {
        assertEquals(Set.of(GooTypes.ASTRAL, GooTypes.FROST), rolledTypes(LunarGooPockets.SHADOWED_BASINS));
    }

    @Test
    void eldritchGroundAnswersAstralAndXeno() {
        assertEquals(Set.of(GooTypes.ASTRAL, GooTypes.XENO), rolledTypes(LunarGooPockets.ELDRITCH_GROUND));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground"})
    void eachSurfaceBiomeNamesTheGooPockets(String biome) throws IOException {
        List<String> named = new ArrayList<>();
        for (JsonElement step : jsonAt("/data/goo/worldgen/biome/" + biome + ".json").getAsJsonArray("features")) {
            for (JsonElement feature : (JsonArray) step) {
                named.add(feature.getAsString());
            }
        }
        assertTrue(named.contains(GOO_POCKETS), biome + " should name " + GOO_POCKETS + ", names " + named);
    }

    @Test
    void gooPocketsAreSpacedApart() throws IOException {
        int chance = 0;
        for (JsonElement placement : jsonAt("/data/goo/worldgen/placed_feature/goo_pockets.json")
                .getAsJsonArray("placement")) {
            JsonObject modifier = placement.getAsJsonObject();
            if ("minecraft:rarity_filter".equals(modifier.get("type").getAsString())) {
                chance = modifier.get("chance").getAsInt();
            }
        }
        assertTrue(chance > 1, GOO_POCKETS + " should place one chunk in more than one, a rarity_filter chance over 1,"
                + " reads " + chance);
    }

    private static Set<ResourceKey<GooTypeDefinition>> rolledTypes(ResourceKey<Biome> biome) {
        RandomSource random = RandomSource.create(SEED);
        Set<ResourceKey<GooTypeDefinition>> types = new HashSet<>();
        for (int roll = 0; roll < ROLLS; roll++) {
            LunarGooPockets.gooTypeFor(biome, random).ifPresent(types::add);
        }
        return types;
    }

    private static JsonObject jsonAt(String path) throws IOException {
        try (InputStream stream = LunarGooPocketsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
