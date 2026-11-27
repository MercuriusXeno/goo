package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's landmark and eldritch loot tables, read from the shipped JSON on the classpath: what each rolls, what it
 * leaves out, and which of its entries are its rarest.
 * decision each-lunar-landmark-holds-its-own-prize
 * decision eldritch-drop-exorite-ingredients-xeno-goo-and-rare-infinity
 */
class LunarLootTablesTest {

    private static final String GOO_ITEM = "goo:goo";
    private static final String GOO_TYPE = "goo:goo_type";

    /**
     * One entry of a table: the item it names, a goo entry named by its goo type, and its share of its pool's rolls.
     */
    record Entry(String item, double chance) {
    }

    private static JsonObject table(String path) throws IOException {
        try (InputStream stream = LunarLootTablesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    /** Each entry of every pool, a goo entry named by its goo type, its chance its weight over its pool's. */
    static List<Entry> entriesOf(JsonObject table) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            List<JsonObject> poolEntries = new ArrayList<>();
            pool.getAsJsonObject().getAsJsonArray("entries").forEach(entry -> poolEntries.add(entry.getAsJsonObject()));
            double total = poolEntries.stream().mapToInt(LunarLootTablesTest::weightOf).sum();
            for (JsonObject entry : poolEntries) {
                if (entry.has("name")) {
                    entries.add(new Entry(itemOf(entry), weightOf(entry) / total));
                }
            }
        }
        return entries;
    }

    private static int weightOf(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsInt() : 1;
    }

    private static String itemOf(JsonObject entry) {
        String name = entry.get("name").getAsString();
        if (!GOO_ITEM.equals(name)) {
            return name;
        }
        JsonElement functions = entry.has("functions") ? entry.get("functions") : entry.get("modifier");
        for (JsonElement function : functions.isJsonArray() ? functions.getAsJsonArray().asList()
                : List.of(functions)) {
            JsonObject components = function.getAsJsonObject().getAsJsonObject("components");
            if (components != null && components.has(GOO_TYPE)) {
                return components.get(GOO_TYPE).getAsString();
            }
        }
        return name;
    }

    /** The items a table names, each once. */
    static Set<String> itemsOf(List<Entry> entries) {
        return entries.stream().map(Entry::item).collect(Collectors.toSet());
    }

    /** Each item's chance summed over the pools naming it. */
    static Map<String, Double> chancesOf(List<Entry> entries) {
        return entries.stream().collect(Collectors.groupingBy(Entry::item, Collectors.summingDouble(Entry::chance)));
    }

    /** The items holding the least chances, rarest first. */
    static List<String> rarest(List<Entry> entries, int count) {
        return chancesOf(entries).entrySet().stream().sorted(Map.Entry.comparingByValue())
                .limit(count).map(Map.Entry::getKey).toList();
    }

    /** The eldritch nest's cache. */
    @Nested
    class NestCache {
        private static final String NEST = "/data/goo/loot_table/chests/lunar_nest.json";

        @Test
        void cacheRollsXenoGooEchoShardsAndNetheriteScrap() throws IOException {
            Set<String> items = itemsOf(entriesOf(table(NEST)));
            assertTrue(items.containsAll(Set.of("goo:xeno", "minecraft:echo_shard", "minecraft:netherite_scrap")),
                    NEST + " should roll xeno goo, echo shards and netherite scrap, rolls " + items);
        }
    }

    /** The ruin's chests. */
    @Nested
    class RuinChests {
        private static final String RUIN = "/data/goo/loot_table/chests/lunar_ruin.json";
        private static final String MOONROCK = "goo:moonrock";

        @Test
        void chestsRollAstralCrystalAncientDebrisAndMoonrock() throws IOException {
            Set<String> items = itemsOf(entriesOf(table(RUIN)));
            assertTrue(items.containsAll(Set.of("goo:astral_crystal", "minecraft:ancient_debris", MOONROCK)),
                    RUIN + " should roll astral crystal, ancient debris and Moonrock, rolls " + items);
        }

        @Test
        void chestsRollNoOpulenceAndNoMundaneLunarBlock() throws IOException {
            Set<String> items = itemsOf(entriesOf(table(RUIN)));
            List<String> barred = items.stream().filter(item -> item.contains("diamond") || item.contains("gold")
                    || Set.of("goo:meteoric_iron", "goo:impact_glass", "goo:crater_ice", "goo:breccia")
                            .contains(item)).toList();
            assertEquals(List.of(), barred, RUIN + " should roll no diamond, gold or mundane lunar block");
        }

        @Test
        void moonrockIsTheRarestEntry() throws IOException {
            List<Entry> entries = entriesOf(table(RUIN));
            Map<String, Double> chances = chancesOf(entries);
            Double moonrock = chances.get(MOONROCK);
            assertNotNull(moonrock, RUIN + " should roll Moonrock, rolls " + chances);
            assertTrue(chances.entrySet().stream().filter(chance -> !MOONROCK.equals(chance.getKey()))
                    .allMatch(chance -> chance.getValue() > moonrock),
                    RUIN + " should roll Moonrock under every other entry, rolls " + chances);
        }
    }

    /** The eldritch monstrosity's drops. */
    @Nested
    class MonstrosityDrops {
        private static final String MONSTROSITY = "/data/goo/loot_table/entities/eldritch_monstrosity.json";

        @Test
        void monstrosityDropsExoriteIngredientsXenoInfinityAndMoonrock() throws IOException {
            Set<String> items = itemsOf(entriesOf(table(MONSTROSITY)));
            assertTrue(items.containsAll(Set.of("minecraft:echo_shard", "minecraft:netherite_scrap", "goo:xeno",
                    "goo:infinity", "goo:moonrock")), MONSTROSITY + " should drop echo shards, netherite scrap, xeno "
                    + "goo, infinity goo and Moonrock, drops " + items);
        }

        @Test
        void infinityAndMoonrockAreTheTwoRarest() throws IOException {
            List<Entry> entries = entriesOf(table(MONSTROSITY));
            assertEquals(Set.of("goo:infinity", "goo:moonrock"), Set.copyOf(rarest(entries, 2)),
                    MONSTROSITY + " should drop infinity goo and Moonrock rarest, drops " + chancesOf(entries));
        }
    }
}
