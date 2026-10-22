package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Groups pane's writes to the model: a group built from item picks and a whole tag lands in
 * _groups as the loader reads it, an absent member is reported, and a group's convention writes one base
 * line and one count-over-standard-plus-trait line per member that differs
 * (decisions workbench-authors-base-values-whole, template-first-then-defaults-by-convention).
 */
class GroupConventionTest {

    private static final String EMPTY_SECTIONS = """
            {
                "_constants": {},
                "_groups": {}
            }
            """.replace("\n", "\r\n");

    private static final Map<String, List<String>> TAGS = Map.of(
            "minecraft:logs", List.of("minecraft:oak_log", "minecraft:birch_log"));

    private static final Set<String> INVENTORY = Set.of("minecraft:apple", "minecraft:bread", "minecraft:oak_log",
            "minecraft:birch_log");

    private static JsonArray groupArray(String text, String group) {
        return JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("_groups").getAsJsonArray(group);
    }

    /**
     * Item picks and a tag's members land in the group's array, which the loader reads back as the same set.
     */
    @Test
    void groupFromPicksAndTagLandsAsTheLoaderReadsIt() {
        List<String> members = GroupConvention.expand(
                List.of("minecraft:apple", "minecraft:bread", "goo:widget", "#minecraft:logs"), TAGS);
        BaseValuesModel model = GroupConvention.withGroup(BaseValuesModel.parse(EMPTY_SECTIONS), "snacks", members);
        JsonArray array = groupArray(model.serialize(), "snacks");
        assertEquals(List.of("apple", "bread", "goo:widget", "oak_log", "birch_log"),
                array.asList().stream().map(JsonElement::getAsString).toList());
        Set<Identifier> expected = new LinkedHashSet<>();
        members.forEach(id -> expected.add(Identifier.parse(id)));
        assertEquals(expected, GooBaseValuesPreview.groupMembers(array));
    }

    /**
     * A member the inventory does not hold is reported.
     */
    @Test
    void memberOutsideTheInventoryIsReported() {
        List<String> members = GroupConvention.expand(
                List.of("minecraft:apple", "minecraft:bread", "goo:widget", "#minecraft:logs"), TAGS);
        BaseValuesModel model = GroupConvention.withGroup(BaseValuesModel.parse(EMPTY_SECTIONS), "snacks", members);
        assertEquals(List.of("goo:widget"), GroupConvention.missing(model, "snacks", INVENTORY));
    }

    /**
     * The base line values every member, and the one member with a count and a trait is valued at the count
     * over the base plus its trait.
     */
    @Test
    void conventionWritesBaseAndTraitLines() throws IOException {
        List<String> eggs = List.of("minecraft:horse_spawn_egg", "minecraft:mule_spawn_egg",
                "minecraft:camel_spawn_egg", "minecraft:donkey_spawn_egg");
        BaseValuesModel model = BaseValuesModel.parse(EMPTY_SECTIONS)
                .withEntry("_constants", "spawn_egg", "{\"zoo\": 4}");
        model = GroupConvention.withGroup(model, "eggs", eggs);
        model = GroupConvention.withBase(model, "eggs", "$spawn_egg");
        model = GroupConvention.withTweak(model, "eggs", "minecraft:donkey_spawn_egg", 5, "donkey_trait",
                "{\"hex\": 1}");
        String text = model.serialize();
        assertTrue(text.contains("\"#eggs\": \"$spawn_egg\""), text);
        assertTrue(text.contains("\"donkey_trait\": {\"hex\": 1}"), text);
        assertTrue(text.contains("\"donkey_spawn_egg\": \"5($spawn_egg + $donkey_trait)\""), text);

        Map<Identifier, GooValue> values = GooBaseValuesPreview.baseValues(text);
        for (String egg : eggs.subList(0, 3)) {
            GooValue value = values.get(Identifier.parse(egg));
            assertEquals(GooFraction.of(4), value.exact(GooTypes.ZOO), egg);
            assertEquals(GooFraction.ZERO, value.exact(GooTypes.HEX), egg);
        }
        GooValue donkey = values.get(Identifier.parse("minecraft:donkey_spawn_egg"));
        assertEquals(GooFraction.of(20), donkey.exact(GooTypes.ZOO));
        assertEquals(GooFraction.of(5), donkey.exact(GooTypes.HEX));
    }

    /**
     * The pane reads a member's count and trait back from its line, so an open group shows what the file holds.
     */
    @Test
    void tweakReadsBackFromTheFile() {
        BaseValuesModel model = BaseValuesModel.parse(EMPTY_SECTIONS)
                .withEntry("_constants", "spawn_egg", "{\"zoo\": 4}");
        model = GroupConvention.withGroup(model, "eggs", List.of("minecraft:mule_spawn_egg",
                "minecraft:donkey_spawn_egg"));
        model = GroupConvention.withBase(model, "eggs", "$spawn_egg");
        model = GroupConvention.withTweak(model, "eggs", "minecraft:donkey_spawn_egg", 5, "donkey_trait",
                "{\"hex\": 1}");
        GroupConvention.Group eggs = GroupConvention.describe(model).getFirst();
        assertEquals("$spawn_egg", eggs.base());
        assertEquals(List.of(new GroupConvention.Tweak("minecraft:donkey_spawn_egg", 5, "donkey_trait",
                "{\"hex\": 1}")), eggs.tweaks());
    }
}
