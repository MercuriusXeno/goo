package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private static final List<String> RAW_MARKS = List.of("{", "}", "\"", "minecraft:");

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
     * Poppy's backwards derivation draws the one output it works back from less one parenthesized sum of the other
     * inputs, "stew - (bowl + mushroom + mushroom)" (decision backwards-derivation-subtracts-a-parenthesized-sum).
     */
    @Test
    void backwardsDerivationSubtractsAParenthesizedSum() {
        Function<String, String> names = id -> switch (id) {
            case "minecraft:suspicious_stew" -> "Suspicious Stew";
            case "minecraft:bowl" -> "Bowl";
            default -> "Mushroom";
        };
        List<ExpressionLines.Run> runs = ExpressionLines.shownRuns("minecraft:suspicious_stew - minecraft:bowl"
                + " - minecraft:red_mushroom - minecraft:brown_mushroom", names);
        assertEquals(List.of("Suspicious Stew", "-", "(", "Bowl", "+", "Mushroom", "+", "Mushroom", ")"),
                runs.stream().map(ExpressionLines.Run::text).toList());
    }

    /**
     * On an icon line, an operator's text top stands at the item sprite's top plus half of 16 less the font's line
     * height, so its glyph centers on the sprite (decision operators-center-on-the-sprite).
     */
    @Test
    void operatorCentersOnTheSpriteBesideIt() {
        DrawnFrame frame = new DrawnFrame();
        Font font = DrawnFrame.font();
        ExpressionLines.draw(Lettering.full(font), ExpressionLines.shownRuns("anvil - bucket", NAMES), 0, 40, 400,
                new ExpressionLines.Painter(frame.graphics, mock(ItemIcons.class), frame.targets, 0xFFFFFFFF,
                        Palette.LINK, (ref, x, y, width) -> { }));
        org.mockito.ArgumentCaptor<Integer> spriteTops = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(frame.graphics, org.mockito.Mockito.atLeastOnce()).item(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), spriteTops.capture());
        int spriteTop = spriteTops.getValue();
        assertEquals(spriteTop + (16 - font.lineHeight + 1) / 2, frame.find("-").y());
        assertEquals(spriteTop + (16 - font.lineHeight + 1) / 2, frame.find("Bucket").y());
    }

    /**
     * A number following a goo type's icon letters at the amount role, as every goo amount beside a type icon does,
     * while a number standing alone keeps the lettering it was drawn with (decision
     * one-lettering-primitive-names-three-text-roles).
     */
    @Test
    void numberBesideATypeIconLettersAtTheAmountRole() {
        DrawnFrame frame = new DrawnFrame();
        List<ExpressionLines.Run> runs = List.of(run(ExpressionLines.Kind.NUMBER, "5"),
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "metal"), run(ExpressionLines.Kind.NUMBER, "3"));
        ExpressionLines.draw(Lettering.full(DrawnFrame.font()), runs, new ExpressionLines.Span(0, 40, 400,
                ExpressionLines.lineHeight(runs)), new ExpressionLines.Painter(frame.graphics, mock(ItemIcons.class),
                frame.targets, 0xFFFFFFFF, Palette.LINK, (ref, x, y, width) -> { }));
        assertEquals(Lettering.AMOUNT_SCALE, frame.placed("3").scale(), frame.placements().toString());
        assertEquals(1f, frame.placed("5").scale(), frame.placements().toString());
    }

    /**
     * One subtracted term stands alone, no parentheses around it.
     */
    @Test
    void singleSubtractedTermDrawsNoParentheses() {
        List<ExpressionLines.Run> runs = ExpressionLines.shownRuns("anvil - bucket", NAMES);
        assertEquals(List.of("Anvil", "-", "Bucket"), runs.stream().map(ExpressionLines.Run::text).toList());
    }

    private static final String BEETROOT = "{\"jelly\": \"$food.jelly + $saturation.jelly * 12\", "
            + "\"leaf\": \"$dye.leaf - ($food.jelly + $saturation.jelly * 12)\"}";

    private static ExpressionLines.Run type(String type) {
        return new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", type);
    }

    private static ExpressionLines.Run link(String prefab) {
        // Decision diagnose-and-correct-prefabs-drawn-without-their-dollar: a prefab draws after its $.
        return new ExpressionLines.Run(ExpressionLines.Kind.LINK, "$" + prefab, prefab);
    }

    private static ExpressionLines.Run symbol(String symbol) {
        return run(ExpressionLines.Kind.OPERATOR, symbol);
    }

    // A per-type prefab's member reads as its link, 's, then the member's icon.
    private static List<ExpressionLines.Run> possessive(String prefab, String member) {
        return List.of(link(prefab), run(ExpressionLines.Kind.SUFFIX, "'s"), type(member));
    }

    /**
     * Beetroot reads each type row as its icon then a colon, its terms inline after it, each per-type prefab as its
     * link, 's and its member's icon, so it reads {jelly}: food's {jelly} + saturation's {jelly} x 12 and
     * {leaf}: dye's {leaf} - (food's {jelly} + saturation's {jelly} x 12) (decisions
     * read-view-reads-type-equals-possessive-terms, builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    @Test
    void beetrootReadsTypeColonThenPossessiveTermsInline() {
        ExpressionTerms.Value beetroot = ExpressionTerms.parseValue(JsonParser.parseString(BEETROOT)).orElseThrow();
        List<ExpressionLines.Run> inner = new java.util.ArrayList<>(possessive("food", "jelly"));
        inner.add(symbol("+"));
        inner.addAll(possessive("saturation", "jelly"));
        inner.add(symbol("x"));
        inner.add(run(ExpressionLines.Kind.NUMBER, "12"));
        List<ExpressionLines.Run> expected = new java.util.ArrayList<>(List.of(type("jelly"), symbol(":")));
        expected.addAll(inner);
        expected.addAll(List.of(run(ExpressionLines.Kind.BREAK, ""), type("leaf"), symbol(":")));
        expected.addAll(possessive("dye", "leaf"));
        expected.addAll(List.of(symbol("-"), symbol("(")));
        expected.addAll(inner);
        expected.add(symbol(")"));
        List<ExpressionLines.Run> runs = ExpressionLines.runs(beetroot, NAMES);
        assertEquals(expected, runs);
        assertCurated(runs);
    }

    /**
     * A scalar prefab reads as its link alone, with no 's.
     */
    @Test
    void scalarPrefabReadsAsABareLink() {
        assertEquals(List.of(link("stick"), symbol("+"), run(ExpressionLines.Kind.NUMBER, "2")),
                ExpressionLines.shownRuns("$stick + 2", NAMES));
    }

    /**
     * A per-type prefab's 's draws flush against its link, no gap between (decision
     * possessive-sits-flush-against-its-word-button).
     */
    @Test
    void possessiveDrawsFlushAgainstItsLink() {
        DrawnFrame frame = drawn("{\"jelly\": \"$food.jelly\"}", 400);
        DrawnFrame.Text food = frame.find("$food");
        DrawnFrame.Text possessive = frame.find("'s");
        assertEquals(food.x() + DrawnFrame.font().width(food.text()), possessive.x(), frame.drawn().toString());
    }

    private static DrawnFrame drawn(String shown, int width) {
        DrawnFrame frame = new DrawnFrame();
        ExpressionLines.draw(Lettering.full(DrawnFrame.font()), ExpressionLines.shownRuns(shown, NAMES), 10, 20, width,
                new ExpressionLines.Painter(frame.graphics, null, frame.targets, 0xFFFFFFFF, Palette.LINK,
                        (variable, x, y, width1) -> { }));
        return frame;
    }

    /**
     * The symbols draw in the symbol color, not the text color, and each type row's first term draws on the type's
     * line right after its colon (decision builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    @Test
    void symbolsDrawInTheirOwnColorAndTermsInlineAfterTheColon() {
        DrawnFrame frame = drawn(BEETROOT, 400);
        for (String symbol : List.of(":", "+", "x", "-", "(", ")")) {
            assertEquals(Palette.GOLD, frame.find(symbol).color(), symbol);
        }
        DrawnFrame.Text colon = frame.findFirst(":");
        DrawnFrame.Text food = frame.findFirst("$food");
        assertEquals(colon.y(), food.y(), frame.drawn().toString());
        assertTrue(food.x() > colon.x(), food + " after " + colon);
        assertEquals(frame.find(":").y(), frame.find("$dye").y(), frame.drawn().toString());
        assertTrue(frame.find("$dye").y() > food.y(), frame.drawn().toString());
        assertEquals(0, frame.count("="), frame.texts().toString());
    }

    /**
     * At a narrow width a type row's continuation wraps one indent in under the type; a parenthetical that does not
     * fit the rest of the line goes with its join to a line of its own; a wrap inside it indents again, and the
     * indent returns past its closing paren (decision builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    @Test
    void narrowRowsWrapUnderTheIndentationRules() {
        int left = 10;
        int oneIndent = left + ControlFlow.INDENT;
        int twoIndents = oneIndent + ControlFlow.INDENT;
        DrawnFrame jelly = drawn("{\"jelly\": \"$food.jelly + $saturation.jelly * 12\"}", 120);
        DrawnFrame.Text food = jelly.find("$food");
        DrawnFrame.Text saturation = jelly.find("$saturation");
        assertEquals(food.y() + ExpressionLines.ICON_LINE_HEIGHT, saturation.y(), jelly.drawn().toString());
        assertEquals(oneIndent, saturation.x(), jelly.drawn().toString());

        DrawnFrame leaf = drawn("{\"leaf\": \"$dye.leaf - ($food.jelly + $saturation.jelly * 12)"
                + " + $saturation.jelly\"}", 120);
        DrawnFrame.Text dye = leaf.find("$dye");
        DrawnFrame.Text minus = leaf.find("-");
        DrawnFrame.Text open = leaf.find("(");
        assertEquals(List.of(dye.y() + ExpressionLines.ICON_LINE_HEIGHT, oneIndent), List.of(minus.y(), minus.x()),
                leaf.drawn().toString());
        assertEquals(minus.y(), open.y(), leaf.drawn().toString());
        DrawnFrame.Text inner = leaf.findFirst("$saturation");
        assertEquals(List.of(minus.y() + ExpressionLines.ICON_LINE_HEIGHT, twoIndents), List.of(inner.y(), inner.x()),
                leaf.drawn().toString());
        DrawnFrame.Text after = leaf.find("$saturation");
        assertTrue(after.y() > leaf.find(")").y(), leaf.drawn().toString());
        assertEquals(oneIndent, after.x(), leaf.drawn().toString());
    }

    /**
     * A tree constant's resolved value draws each type as its icon beside its amount, an amount run drawn small and
     * mixed (decision groups-value-draws-as-every-other-panes-value).
     */
    @Test
    void resolvedTreeDrawsTypeIconsBesideAmounts() {
        List<ExpressionLines.Run> runs = ExpressionLines.resolvedRuns("blaze 24, rock 3/2");
        assertEquals(List.of(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "blaze"),
                run(ExpressionLines.Kind.AMOUNT, "24"),
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, "", "rock"),
                run(ExpressionLines.Kind.AMOUNT, "3/2")), runs);
        assertCurated(runs);
    }

    /**
     * A prefab link draws a one-pixel underline in the link color under its text while shift is held, and none while
     * shift is up (decision holding-shift-underlines-every-link).
     */
    @Test
    void linkUnderlinesWhileShiftIsHeldAndNotWhileUp() {
        List<ExpressionLines.Run> runs = List.of(run(ExpressionLines.Kind.NUMBER, "2"),
                new ExpressionLines.Run(ExpressionLines.Kind.LINK, "$wood", "wood"));
        DrawnFrame held = new DrawnFrame().holdingShift();
        paint(held, runs);
        assertTrue(held.underlined("$wood"));
        assertFalse(held.underlined("2"));
        DrawnFrame up = new DrawnFrame();
        paint(up, runs);
        assertFalse(up.underlined("$wood"));
    }

    private static void paint(DrawnFrame frame, List<ExpressionLines.Run> runs) {
        ExpressionLines.draw(Lettering.full(DrawnFrame.font()), runs, 10, 20, 300,
                new ExpressionLines.Painter(frame.graphics, null,
                frame.targets, 0xFFFFFFFF, Palette.LINK, (variable, x, y, width) -> { }));
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
                new ExpressionLines.Run(ExpressionLines.Kind.LINK, "$iron_block", "iron_block")), runs);
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
