package com.mercuriusxeno.goo.workbench;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the workbench's model of base_values.json on the shipped file: a parse and serialize hands the
 * file back byte for byte, and an edit to one entry's text changes that line alone
 * (decision workbench-authors-base-values-whole).
 */
class BaseValuesModelTest {

    // The last curated base_values.json, frozen: the shipped file is rebuilt from nothing, so tests needing a
    // realistic file read this copy rather than whatever ships.
    private static final String SAMPLE = "/workbench/sample_base_values.json";

    static byte[] sampleBytes() throws IOException {
        try (InputStream stream = BaseValuesModelTest.class.getResourceAsStream(SAMPLE)) {
            return stream.readAllBytes();
        }
    }

    /**
     * Every top-level section comes back in file order, and the serialized text is the file.
     */
    @Test
    void shippedFileRoundTripsUnchanged() throws IOException {
        byte[] shipped = sampleBytes();
        BaseValuesModel model = BaseValuesModel.parse(new String(shipped, StandardCharsets.UTF_8));
        assertArrayEquals(shipped, model.serialize().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rewriting one constant's text changes the line that constant stands on and no other.
     */
    @Test
    void oneEditChangesOneLine() throws IOException {
        String shipped = new String(sampleBytes(), StandardCharsets.UTF_8);
        BaseValuesModel model = BaseValuesModel.parse(shipped);
        BaseValuesModel edited = model.withEntryText("_constants", "low", "\"5 $base\"");
        List<String> before = List.of(shipped.split("\r\n", -1));
        List<String> after = List.of(edited.serialize().split("\r\n", -1));
        assertEquals(before.size(), after.size());
        List<String> changed = new ArrayList<>();
        for (int line = 0; line < before.size(); line++) {
            if (!before.get(line).equals(after.get(line))) {
                changed.add(after.get(line));
            }
        }
        assertEquals(List.of("        \"low\": \"5 $base\","), changed);
    }

    /**
     * Renaming a constant renames its key and every reference to it, its dotted type reads included,
     * and leaves a longer name sharing its prefix alone.
     */
    @Test
    void renameRewritesEveryReference() throws IOException {
        BaseValuesModel renamed = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8))
                .renameConstant("iron_nugget", "iron_bit");
        String text = renamed.serialize();
        assertFalse(text.contains("$iron_nugget"));
        assertTrue(text.contains("\"iron_ingot\": \"9 $iron_bit\""));
        assertTrue(text.contains("\"gold_nugget\": {\"metal\": \"4 $iron_bit.metal\""));
        assertTrue(text.contains("        \"iron_bit\": {\"metal\": \"4 $copper_nugget.metal\" },"));
    }

    /**
     * A constant's usage list names each constant and entry reading it.
     */
    @Test
    void usagesNameEveryReader() throws IOException {
        BaseValuesModel renamed = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8))
                .renameConstant("iron_nugget", "iron_bit");
        assertEquals(List.of("iron_ingot", "gold_nugget", "chainmail_boots", "chainmail_helmet",
                "chainmail_chestplate", "chainmail_leggings"), renamed.usagesOf("iron_bit"));
    }

    /**
     * Deleting a read constant removes it and every reader whole, cascading through each removed constant's
     * readers, and leaves no reference to anything removed (decision
     * deleting-a-variable-inlines-its-expression-into-each-reader).
     */
    @Test
    void deleteRemovesTheConstantAndItsReaderCascade() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8));
        List<String> removed = model.deletionCascade("iron_nugget").stream().map(BaseValuesModel.Reader::id)
                .toList();
        BaseValuesModel deleted = model.deleteConstant("iron_nugget");
        BaseValuesModel.Member constants = deleted.member("_constants");
        for (String key : List.of("iron_nugget", "iron_ingot", "gold_nugget", "gold_ingot")) {
            assertTrue(constants.entries().stream().noneMatch(entry -> key.equals(entry.key())), key);
            assertTrue(removed.contains(key) || "iron_nugget".equals(key), key);
        }
        for (String key : List.of("chainmail_boots", "chainmail_helmet", "chainmail_chestplate",
                "chainmail_leggings")) {
            assertEquals(null, deleted.member(key), key);
        }
        String text = deleted.serialize();
        for (String key : removed) {
            assertFalse(java.util.regex.Pattern.compile("\\$" + key + "(?![A-Za-z0-9_])").matcher(text).find(),
                    key);
        }
        assertTrue(text.contains("\"copper_nugget\""), "an unread constant stays");
    }

    /**
     * A reader reaching the deleted constant through two paths is removed once, and readers referencing each
     * other in a cycle end the cascade.
     */
    @Test
    void deleteVisitsEachReaderOnce() {
        BaseValuesModel twoPaths = BaseValuesModel.parse("""
                {
                    "_constants": {
                        "base": 1,
                        "x": "$base",
                        "y": "$base + $x"
                    },
                    "stick": "$y"
                }
                """);
        assertEquals(List.of("x", "y", "stick"), twoPaths.deletionCascade("base").stream()
                .map(BaseValuesModel.Reader::id).toList());
        BaseValuesModel cycle = BaseValuesModel.parse("""
                {
                    "_constants": {
                        "a": "$b",
                        "b": "$a"
                    }
                }
                """);
        assertTrue(cycle.deleteConstant("a").member("_constants").entries().isEmpty());
    }

    /**
     * Each reader carries its kind: a constant where it stands in _constants, a group where its key is a #group
     * or tag, an item otherwise (decision used-by-draws-item-images-and-name-chips).
     */
    @Test
    void readersCarryTheirKind() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8));
        assertEquals(List.of(
                new BaseValuesModel.Reader("iron_ingot", BaseValuesModel.ReaderKind.CONSTANT),
                new BaseValuesModel.Reader("gold_nugget", BaseValuesModel.ReaderKind.CONSTANT),
                new BaseValuesModel.Reader("chainmail_boots", BaseValuesModel.ReaderKind.ITEM),
                new BaseValuesModel.Reader("chainmail_helmet", BaseValuesModel.ReaderKind.ITEM),
                new BaseValuesModel.Reader("chainmail_chestplate", BaseValuesModel.ReaderKind.ITEM),
                new BaseValuesModel.Reader("chainmail_leggings", BaseValuesModel.ReaderKind.ITEM)),
                model.readersOf("iron_nugget"));
        assertTrue(model.readersOf("mundane_block").contains(
                new BaseValuesModel.Reader("#mundane_blocks", BaseValuesModel.ReaderKind.GROUP)));
    }

    /**
     * $copper_nugget is read by constants alone, so its terminus is the items at the end of each constant chain
     * (decision used-by-draws-the-terminus-items-alone).
     */
    @Test
    void terminusFollowsConstantReadersToTheirItems() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8));
        assertTrue(model.readersOf("copper_nugget").stream()
                .allMatch(reader -> reader.kind() == BaseValuesModel.ReaderKind.CONSTANT));
        List<String> terminus = model.terminusOf("copper_nugget", Map.of());
        assertTrue(terminus.containsAll(List.of("raw_copper", "copper_horse_armor", "copper_golem_statue",
                "chainmail_boots")), terminus.toString());
        assertNoKeyOfASection(model, terminus);
    }

    /**
     * $mundane_block is read by the #mundane_blocks entry, so its terminus holds that group's members, and
     * $wool by the #wool tag, so its terminus holds the tag's members from the inventory.
     */
    @Test
    void terminusExpandsGroupAndTagReaders() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8));
        List<String> terminus = model.terminusOf("mundane_block", Map.of());
        assertTrue(terminus.containsAll(groupMembers(model, "mundane_blocks")), terminus.toString());
        assertNoKeyOfASection(model, terminus);
        List<String> wool = model.terminusOf("wool", Map.of("minecraft:wool",
                List.of("minecraft:white_wool", "minecraft:red_wool")));
        assertEquals(List.of("minecraft:white_wool", "minecraft:red_wool"), wool);
    }

    /**
     * $waxed is read by the waxed post-conversion, which the #waxed_* assignments stack, so its terminus holds
     * those target groups' members beside the honeycomb that reads it directly.
     */
    @Test
    void terminusFollowsAProcessToTheItemsItConverts() throws IOException {
        BaseValuesModel model = BaseValuesModel.parse(new String(sampleBytes(), StandardCharsets.UTF_8));
        List<String> terminus = model.terminusOf("waxed", Map.of());
        assertTrue(terminus.contains("honeycomb"), terminus.toString());
        for (String group : List.of("waxed_copper", "waxed_exposed", "waxed_weathered", "waxed_oxidized")) {
            assertTrue(terminus.containsAll(groupMembers(model, group)), group);
        }
        assertNoKeyOfASection(model, terminus);
    }

    private static List<String> groupMembers(BaseValuesModel model, String group) {
        return model.member("_groups").entries().stream().filter(entry -> group.equals(entry.key())).findFirst()
                .map(entry -> com.google.gson.JsonParser.parseString(entry.text()).getAsJsonArray().asList()
                        .stream().map(com.google.gson.JsonElement::getAsString).toList())
                .orElseThrow();
    }

    // An item can share a constant's name, as "gold_nugget": "$gold_nugget" does, so an id passes where the file
    // holds it as a top-level item entry or a group member, and never as a #group or a process key.
    private static void assertNoKeyOfASection(BaseValuesModel model, List<String> terminus) {
        assertFalse(terminus.isEmpty());
        java.util.Set<String> items = new java.util.HashSet<>();
        model.members().stream().filter(member -> member.kind() == BaseValuesModel.Kind.VALUE)
                .map(BaseValuesModel.Member::key).filter(key -> !key.startsWith("#")).forEach(items::add);
        model.member("_groups").entries().forEach(group -> items.addAll(groupMembers(model, group.key())));
        java.util.Set<String> processes = new java.util.HashSet<>();
        for (String section : List.of("_conversions", "_post_conversions")) {
            model.member(section).entries().forEach(entry -> processes.add(entry.key()));
        }
        for (String id : terminus) {
            assertTrue(items.contains(id), id + " is no item entry or group member");
            assertFalse(id.startsWith("#") || processes.contains(id), id + " is a group or process key");
        }
    }
}
