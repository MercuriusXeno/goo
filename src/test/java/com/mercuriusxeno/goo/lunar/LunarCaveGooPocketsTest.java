package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's cave bands try more astral goo pockets a chunk than its surface does: the deposit pockets' count a chunk
 * stands over the surface pockets' one chunk in their rarity, shared among the most goo types any surface biome rolls;
 * and every lunar biome names the deposit pockets.
 * decision frost-astral-and-xeno-goo-spawn-periodically-on-lunar
 */
class LunarCaveGooPocketsTest {

    private static final String DEPOSIT_GOO_POCKETS = "goo:deposit_goo_pockets";
    private static final String PLACED = "/data/goo/worldgen/placed_feature/";

    @Test
    void caveBandsTryMoreAstralPocketsThanTheSurface() throws IOException {
        double cave = modifierValue(PLACED + "deposit_goo_pockets.json", "minecraft:count", "count");
        double surfaceShare = Stream.of(LunarGooPockets.MARIA, LunarGooPockets.HIGHLANDS,
                        LunarGooPockets.SHADOWED_BASINS, LunarGooPockets.ELDRITCH_GROUND)
                .mapToDouble(biome -> 1.0 / LunarGooPockets.gooTypesFor(biome).size()).max().orElse(0.0);
        double surface = surfaceShare / modifierValue(PLACED + "goo_pockets.json", "minecraft:rarity_filter", "chance");
        assertTrue(cave > surface, "The cave bands should try more astral pockets a chunk than the surface's " + surface
                + ", try " + cave);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lunar_maria", "lunar_highlands", "lunar_shadowed_basins", "lunar_eldritch_ground",
        "lunar_warrens"})
    void eachLunarBiomeNamesTheDepositPockets(String biome) throws IOException {
        List<String> named = new ArrayList<>();
        for (JsonElement step : jsonAt("/data/goo/worldgen/biome/" + biome + ".json").getAsJsonArray("features")) {
            for (JsonElement feature : (JsonArray) step) {
                named.add(feature.getAsString());
            }
        }
        assertTrue(named.contains(DEPOSIT_GOO_POCKETS), biome + " should name " + DEPOSIT_GOO_POCKETS + ", names "
                + named);
    }

    private static double modifierValue(String path, String type, String field) throws IOException {
        for (JsonElement placement : jsonAt(path).getAsJsonArray("placement")) {
            JsonObject modifier = placement.getAsJsonObject();
            if (type.equals(modifier.get("type").getAsString())) {
                return modifier.get(field).getAsDouble();
            }
        }
        return 0.0;
    }

    private static JsonObject jsonAt(String path) throws IOException {
        try (InputStream stream = LunarCaveGooPocketsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
