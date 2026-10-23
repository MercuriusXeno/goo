package com.mercuriusxeno.goo.tools.workbench;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    private static final String SHIPPED = "/data/goo/goo_values/base_values.json";

    static byte[] shippedBytes() throws IOException {
        try (InputStream stream = BaseValuesModelTest.class.getResourceAsStream(SHIPPED)) {
            return stream.readAllBytes();
        }
    }

    /**
     * Every top-level section comes back in file order, and the serialized text is the file.
     */
    @Test
    void shippedFileRoundTripsUnchanged() throws IOException {
        byte[] shipped = shippedBytes();
        BaseValuesModel model = BaseValuesModel.parse(new String(shipped, StandardCharsets.UTF_8));
        assertArrayEquals(shipped, model.serialize().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rewriting one constant's text changes the line that constant stands on and no other.
     */
    @Test
    void oneEditChangesOneLine() throws IOException {
        String shipped = new String(shippedBytes(), StandardCharsets.UTF_8);
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
        BaseValuesModel renamed = BaseValuesModel.parse(new String(shippedBytes(), StandardCharsets.UTF_8))
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
        BaseValuesModel renamed = BaseValuesModel.parse(new String(shippedBytes(), StandardCharsets.UTF_8))
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
        BaseValuesModel model = BaseValuesModel.parse(new String(shippedBytes(), StandardCharsets.UTF_8));
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
        BaseValuesModel model = BaseValuesModel.parse(new String(shippedBytes(), StandardCharsets.UTF_8));
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
}
