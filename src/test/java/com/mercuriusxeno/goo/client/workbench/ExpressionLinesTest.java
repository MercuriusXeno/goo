package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * Tests the curated runs a value draws as, with no Screen: goo types as icons, items as icons and names, prefabs as
 * name links, and no brace, quote, $ or namespaced id (decisions curated-view-shows-no-brace-quote-or-raw-id,
 * goo-types-draw-as-icons-named-on-hover).
 */
class ExpressionLinesTest {
    private static final Function<String, String> NAMES = id -> switch (id) {
        case "anvil" -> "Anvil";
        case "bucket" -> "Bucket";
        default -> "Unnamed";
    };
    private static final List<String> RAW_MARKS = List.of("{", "}", "\"", "$", "minecraft:");

    private static ExpressionLines.Run run(ExpressionLines.Kind kind, String text) {
        return new ExpressionLines.Run(kind, text, null);
    }

    private static void assertCurated(List<ExpressionLines.Run> runs) {
        for (ExpressionLines.Run run : runs) {
            for (String mark : RAW_MARKS) {
                assertFalse(run.text().contains(mark), run + " draws " + mark);
            }
        }
    }

    /**
     * A TYPE run measures at its texture's own size, one texel per pixel, and its icon centers in the icon line, for
     * an 11 and a 12 icon alike (decision goo-type-icons-draw-native-everywhere).
     */
    @Test
    void typeRunMeasuresAndCentersAtItsTextureSize() {
        Font font = mock(Font.class);
        List<ExpressionLines.Run> metal = List.of(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "metal"));
        for (int size : new int[] {11, 12}) {
            assertEquals(size, ExpressionLines.width(font, metal, type -> size));
            assertEquals(40 + (ExpressionLines.ICON_LINE_HEIGHT - size) / 2, ExpressionLines.typeIconTop(40, size));
        }
    }

    /**
     * Kelp's per-type object draws a row per type: the type's icon, then its sum with the prefab as a link and its
     * type as an icon.
     */
    @Test
    void perTypeObjectDrawsARowPerTypeIcon() {
        ExpressionTerms.Value kelp = ExpressionTerms.parseValue(JsonParser.parseString(
                "{\"jelly\": \"3 $food.jelly / 2\", \"leaf\": \"3 $food.jelly / 2\"}")).orElseThrow();
        List<ExpressionLines.Run> runs = ExpressionLines.runs(kelp, NAMES);
        List<ExpressionLines.Run> row = List.of(
                new ExpressionLines.Run(ExpressionLines.Kind.NUMBER, "3", null),
                new ExpressionLines.Run(ExpressionLines.Kind.LINK, "food", "food"),
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "jelly"),
                run(ExpressionLines.Kind.OPERATOR, "/"),
                run(ExpressionLines.Kind.NUMBER, "2"));
        List<ExpressionLines.Run> expected = new java.util.ArrayList<>();
        expected.add(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "jelly"));
        expected.addAll(row);
        expected.add(run(ExpressionLines.Kind.BREAK, ""));
        expected.add(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "leaf"));
        expected.addAll(row);
        assertEquals(expected, runs);
        assertCurated(runs);
    }

    /**
     * A tree constant's resolved value draws each type as its icon beside its amount.
     */
    @Test
    void resolvedTreeDrawsTypeIconsBesideAmounts() {
        List<ExpressionLines.Run> runs = ExpressionLines.resolvedRuns("blaze 24, rock 3/2");
        assertEquals(List.of(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "blaze"),
                run(ExpressionLines.Kind.NUMBER, "24"),
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "rock"),
                run(ExpressionLines.Kind.NUMBER, "3/2")), runs);
        assertCurated(runs);
    }

    /**
     * An expression draws an item as its icon and name, an operator as text and a prefab as its name link.
     */
    @Test
    void expressionDrawsItemsAsNamesAndPrefabsAsLinks() {
        ExpressionTerms.Value anvil = ExpressionTerms.parseShown("anvil - $iron_block").orElseThrow();
        List<ExpressionLines.Run> runs = ExpressionLines.runs(anvil, NAMES);
        assertEquals(List.of(new ExpressionLines.Run(ExpressionLines.Kind.ITEM, "Anvil", "anvil"),
                run(ExpressionLines.Kind.OPERATOR, "-"),
                new ExpressionLines.Run(ExpressionLines.Kind.LINK, "iron_block", "iron_block")), runs);
        assertCurated(runs);
    }

    /**
     * A namespaced item draws its name, and an item's type suffix draws as the type's icon.
     */
    @Test
    void namespacedItemTypeDrawsNameAndTypeIcon() {
        ExpressionTerms.Value bucket = ExpressionTerms.parseShown("2 minecraft:bucket.metal").orElseThrow();
        List<ExpressionLines.Run> runs = ExpressionLines.runs(bucket,
                id -> "minecraft:bucket".equals(id) ? "Bucket" : "Unnamed");
        assertEquals(List.of(run(ExpressionLines.Kind.NUMBER, "2"),
                new ExpressionLines.Run(ExpressionLines.Kind.ITEM, "Bucket", "minecraft:bucket"),
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "metal")), runs);
        assertCurated(runs);
    }
}
