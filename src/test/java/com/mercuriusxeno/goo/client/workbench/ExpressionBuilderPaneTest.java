package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the expression builder through its hover hints' static seams and through frames it draws onto mocked graphics
 * with a mocked font, no Screen.
 */
class ExpressionBuilderPaneTest {
    private static final List<BuilderGrammar.Prefab> PREFABS = List.of(new BuilderGrammar.Prefab("base", false),
            new BuilderGrammar.Prefab("waxed", true), new BuilderGrammar.Prefab("edited", true));
    private static final GroupsPane.Bounds AREA = new GroupsPane.Bounds(0, 0, 590, 390);

    /**
     * An open builder, each frame it draws and each string it writes.
     */
    static final class Builder {
        final Font font = DrawnFrame.font();
        final List<String> written = new ArrayList<>();
        final ExpressionBuilderPane pane;
        final ManagerActions actions = mock(ManagerActions.class);

        Builder(List<BuilderGrammar.Prefab> prefabs) {
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(any())).thenAnswer(call -> call.getArgument(0));
            pane = new ExpressionBuilderPane(actions, icons, List::of, () -> prefabs);
            pane.init(font);
            pane.show(true);
        }

        static Builder item(String shown) {
            Builder builder = new Builder(PREFABS);
            builder.pane.open(shown, builder.written::add, () -> { });
            return builder;
        }

        static Builder prefab(List<BuilderGrammar.Prefab> prefabs, String shown) {
            Builder builder = new Builder(prefabs);
            builder.pane.openPrefab("edited", shown, builder.written::add, () -> { });
            return builder;
        }

        DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, font, AREA, 0, frame.targets);
            pane.drawOverlays(frame.graphics, font, frame.targets);
            return frame;
        }

        /**
         * Types a number into the number box standing open and presses Enter.
         *
         * @param digits the number typed
         */
        void commitNumber(String digits) {
            ArgumentCaptor<AbstractWidget> focused = ArgumentCaptor.forClass(AbstractWidget.class);
            verify(actions, atLeastOnce()).focus(focused.capture());
            ((EditBox) focused.getValue()).setValue(digits);
            assertTrue(pane.keyPressed(GLFW.GLFW_KEY_ENTER), "no number box stood open");
        }
    }

    // The click targets of each side of the ratio drawn last: the text before the colon and the text after it.
    private static List<DrawnFrame.Text> ratioSides(DrawnFrame frame) {
        List<DrawnFrame.Text> drawn = frame.drawn();
        int colon = drawn.stream().map(DrawnFrame.Text::text).toList().lastIndexOf(":");
        assertTrue(colon > 0, frame.texts().toString());
        return List.of(drawn.get(colon - 1), drawn.get(colon + 1));
    }

    private static void clickAt(DrawnFrame frame, DrawnFrame.Text text) {
        assertTrue(frame.targets.click(text.x() + 1, text.y() + 1), text + " took no click");
    }

    private static String dropdown(String label) {
        return ControlFlow.dropdownLabel(label);
    }

    /**
     * Inside the builder, a right click on a prefab reference runs nothing, a shift + left click on it opens the
     * Prefabs tab on that prefab, and a plain left click opens the dropdown that replaces it (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void referenceFollowsOnShiftClickAndOpensItsReplacementOnPlainClick() {
        Builder builder = Builder.item("$waxed");
        DrawnFrame closed = builder.frame();
        assertFalse(closed.texts().contains("edited"), closed.texts().toString());
        assertFalse(closed.rightClick("waxed"));
        verify(builder.actions, never()).openVariable(any());
        DrawnFrame unmoved = builder.frame();
        assertFalse(unmoved.texts().contains("edited"), unmoved.texts().toString());
        unmoved.shiftClick("waxed");
        verify(builder.actions).openVariable("waxed");
        assertFalse(builder.frame().texts().contains("edited"));
        builder.frame().click("waxed");
        List<String> opened = builder.frame().texts();
        assertTrue(opened.contains("edited"), opened.toString());
    }

    /**
     * Over beetroot's leaf value, the builder offers no "+ sum of terms" and draws no "the sum of:"; its parenthetical
     * draws as a "(" line, its two terms one indent step past the "(", and a ")" line under them at the "(" x
     * (decision sum-of-terms-leaves-the-builder).
     */
    @Test
    void parentheticalDrawsBetweenParenLinesWithItsTermsIndented() {
        DrawnFrame frame = Builder.item("$dye.leaf - ($food.jelly + $saturation.jelly * 12)").frame();
        assertFalse(frame.texts().contains("+ sum of terms") || frame.texts().contains("the sum of:"),
                frame.texts().toString());
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        DrawnFrame.Text food = frame.find("food");
        DrawnFrame.Text saturation = frame.find("saturation");
        assertTrue(open.y() < food.y() && food.y() < saturation.y() && saturation.y() < close.y(),
                frame.drawn().toString());
        assertEquals(open.x(), close.x(), frame.drawn().toString());
        // A term's button frame starts one indent step past the "(", its text inset by the button's padding.
        assertEquals(open.x() + ControlFlow.INDENT + ButtonPainter.PADDING, food.x(), frame.drawn().toString());
        DrawnFrame.Text plus = frame.drawn().stream().filter(text -> text.y() == saturation.y())
                .min(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).orElseThrow();
        assertEquals(food.x(), plus.x(), frame.drawn().toString());
    }

    /**
     * The edit view reads the kind and a [+] on its first line, each type row's head [type] = one indent in with its X
     * at the right end, each term a line a second indent in reading its join, operand, ratio and [()] [^] [v] [x],
     * and each sum closing on a [+/-] dropdown; Cancel then Done stand at the right end of the last line (decision
     * builder-is-kind-plus-then-rows-of-terms-with-term-operators).
     */
    @Nested
    class TermRows {
        private static final String BEETROOT = "{\"jelly\": \"$food.jelly + $saturation.jelly * 12\", "
                + "\"leaf\": \"$dye.leaf - ($food.jelly + $saturation.jelly * 12)\"}";
        private static final String SUM_ADD = dropdown("+/-");

        // The texts drawn on one builder line, left to right; the builder's lines stand a line height apart from 0.
        private static List<DrawnFrame.Text> line(DrawnFrame frame, int index) {
            return frame.drawn().stream().filter(text -> text.y() / ControlFlow.LINE == index)
                    .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).toList();
        }

        private static List<String> lineTexts(DrawnFrame frame, int index) {
            return line(frame, index).stream().map(DrawnFrame.Text::text).toList();
        }

        // The x of each goo type icon drawn on one builder line.
        private static List<Integer> iconXs(DrawnFrame frame, int index) {
            ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, org.mockito.Mockito.atLeast(0)).blit(any(), any(), xs.capture(), ys.capture(),
                    org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(), anyInt(),
                    anyInt(), anyInt(), anyInt(), anyInt());
            List<Integer> onLine = new ArrayList<>();
            for (int at = 0; at < xs.getAllValues().size(); at++) {
                if (ys.getAllValues().get(at) / ControlFlow.LINE == index) {
                    onLine.add(xs.getAllValues().get(at));
                }
            }
            return onLine;
        }

        @Test
        void beetrootsJellyRowReadsKindPlusTheRowHeadThenATermALine() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            assertEquals(List.of(dropdown("by type"), "+"), lineTexts(frame, 0), frame.drawn().toString());
            assertEquals(List.of("=", "X"), lineTexts(frame, 1), frame.drawn().toString());
            assertEquals(List.of(ControlFlow.INDENT + ButtonPainter.PADDING), iconXs(frame, 1));
            DrawnFrame.Text rowX = line(frame, 1).getLast();
            assertEquals(AREA.right() - ButtonPainter.width(DrawnFrame.font(), "X") + ButtonPainter.PADDING,
                    rowX.x());
            assertEquals(List.of("food", "'s", "1", ":", "1", "()", "v", "X"), lineTexts(frame, 2),
                    frame.drawn().toString());
            DrawnFrame.Text food = line(frame, 2).getFirst();
            assertEquals(2 * ControlFlow.INDENT + ButtonPainter.PADDING, food.x());
            int jelly = iconXs(frame, 2).getFirst();
            assertTrue(line(frame, 2).get(1).x() < jelly && jelly < line(frame, 2).get(2).x(),
                    "jelly between 's and the ratio at " + jelly);
            assertEquals("+", lineTexts(frame, 3).getFirst(), frame.drawn().toString());
            assertTrue(lineTexts(frame, 3).contains("^"), frame.drawn().toString());
            assertEquals(List.of(SUM_ADD), lineTexts(frame, 4), frame.drawn().toString());
        }

        // Clicks the picker's cell for a goo type, found by its hover across the screen.
        private static void pickType(DrawnFrame frame, String type) {
            String key = GooTypeIcons.hoverKey(type);
            for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y += 2) {
                for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x += 2) {
                    if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                        assertTrue(frame.targets.click(x, y));
                        return;
                    }
                }
            }
            throw new AssertionError(type + " not in the picker: " + frame.targets.hoverKeys());
        }

        @Test
        void kindLinesPlusAppendsATypeRowAtTheEnd() {
            Builder builder = Builder.item(BEETROOT);
            DrawnFrame before = builder.frame();
            long rows = before.texts().stream().filter("="::equals).count();
            DrawnFrame.Text plus = line(before, 0).get(1);
            clickAt(before, plus);
            pickType(builder.frame(), "metal");
            DrawnFrame after = builder.frame();
            assertEquals(rows + 1, after.texts().stream().filter("="::equals).count(), after.texts().toString());
            assertTrue(builder.written.getLast().endsWith("\"metal\": \"1\"}"), builder.written.toString());
        }

        // Leaf's parenthetical is subtracted, so neither it nor dye ahead of it draws a move that would land it first
        // and drop its minus.
        @Test
        void parentheticalHeadReadsJoinParenAndXWithItsTermsAFurtherIndentIn() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            DrawnFrame.Text open = frame.find("(");
            int head = open.y() / ControlFlow.LINE;
            assertEquals(List.of("-", "(", "X"), lineTexts(frame, head), frame.drawn().toString());
            assertEquals(List.of("dye", "'s", "()", "X"), lineTexts(frame, head - 1).stream()
                    .filter(text -> !text.equals("1") && !text.equals(":")).toList(), frame.drawn().toString());
            DrawnFrame.Text food = line(frame, head + 1).getFirst();
            assertEquals("food", food.text(), frame.drawn().toString());
            assertEquals(open.x() + ControlFlow.INDENT + ButtonPainter.PADDING, food.x());
            assertEquals(open.x(), frame.find(")").x(), frame.drawn().toString());
            assertTrue(frame.find(")").y() > frame.find("saturation").y(), frame.drawn().toString());
        }

        @Test
        void sumDropdownOffersPlusAndMinusAndAddsATermJoinedByTheChoice() {
            Builder builder = Builder.prefab(PREFABS, "$base");
            DrawnFrame closed = builder.frame();
            closed.click(SUM_ADD);
            DrawnFrame open = builder.frame();
            List<String> texts = open.texts();
            assertEquals(List.of("+", "-"), texts.subList(texts.lastIndexOf(SUM_ADD) + 1, texts.size()).stream()
                    .filter(List.of("+", "-")::contains).toList(), texts.toString());
            open.click("-");
            builder.frame().click("base");
            assertEquals("$base - $base", builder.written.getLast());
        }

        // Decision amount-modes-name-what-the-amount-comes-from: a number term names its amount's source, so a type
        // row's amount takes a prefab's quantity in place of the number.
        @Test
        void numberTermTakesAPrefabsQuantityThroughItsAmountMode() {
            Builder builder = Builder.prefab(PREFABS, "{\"metal\": \"5\"}");
            builder.frame().click(dropdown("number"));
            builder.frame().click("prefab");
            builder.frame().click("base");
            assertEquals("{\"metal\": \"$base\"}", builder.written.getLast());
        }

        @Test
        void cancelThenDoneStandAtTheLastLinesRightEnd() {
            Builder builder = Builder.item("$waxed");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).getFirst());
            builder.commitNumber("3");
            DrawnFrame changed = builder.frame();
            DrawnFrame.Text cancel = changed.find("✗ Cancel");
            DrawnFrame.Text done = changed.find("✓ Done");
            Font font = DrawnFrame.font();
            int cancelRight = cancel.x() - ButtonPainter.PADDING + ButtonPainter.width(font, "✗ Cancel");
            assertTrue(cancelRight < done.x() - ButtonPainter.PADDING, cancel + " left of " + done);
            assertEquals(AREA.right(), done.x() - ButtonPainter.PADDING + ButtonPainter.width(font, "✓ Done"));
            assertEquals(done.y(), cancel.y());
            assertEquals(changed.find(SUM_ADD).y() / ControlFlow.LINE, done.y() / ControlFlow.LINE);
            assertFalse(changed.texts().contains("Value"), changed.texts().toString());
        }
    }

    /**
     * Every builder, Items, Groups and Prefabs alike, reads its kind first, an empty value defaulting to by type with
     * a bare + beside it adding a goo type (decisions every-value-builder-reads-value-kind-on-one-line-defaulting-by-type,
     * builder-is-kind-plus-then-rows-of-terms-with-term-operators).
     */
    @Nested
    class ValueLine {
        private static final List<String> RETIRED = List.of("Kind:", "Kind", "pick one", dropdown("pick one"),
                "No value yet: pick a kind to give it one.", "Pick the first goo type:", "+ goo type", "amount from",
                "from other items", dropdown("from other items"));

        private static void assertEmptyReadsByTypeAndPlus(Builder builder) {
            DrawnFrame frame = builder.frame();
            List<String> texts = frame.texts();
            assertEquals(List.of(dropdown("by type"), "+"), texts.subList(0, 2), texts.toString());
            assertEquals(frame.find("+").y(), frame.find(dropdown("by type")).y(), "the kind and + share a line");
            assertTrue(texts.stream().noneMatch(RETIRED::contains), texts.toString());
        }

        @Test
        void emptyItemValueReadsByTypeAndPlus() {
            assertEmptyReadsByTypeAndPlus(Builder.item(""));
        }

        @Test
        void emptyPrefabValueReadsByTypeAndPlus() {
            assertEmptyReadsByTypeAndPlus(Builder.prefab(PREFABS, ""));
        }

        /**
         * A group's value opens the same builder an item's does.
         */
        @Test
        void emptyGroupValueReadsByTypeAndPlus() {
            Builder group = new Builder(PREFABS);
            group.pane.open("", group.written::add, () -> { });
            assertEmptyReadsByTypeAndPlus(group);
        }

        @Test
        void barePlusAddsAGooType() {
            Builder builder = Builder.item("");
            builder.frame().click("+");
            assertTrue(builder.pane.listOpen(), "the goo type picker opens");
        }

        @Test
        void itemKindReadsRelative() {
            List<String> relative = Builder.item("anvil").frame().texts();
            assertEquals(dropdown("relative"), relative.getFirst(), relative.toString());
        }
    }

    /**
     * A goo type icon draws inside a button border, and a click inside that border opens the goo type picker
     * (decision goo-types-in-builders-draw-as-buttons).
     */
    @Test
    void gooTypeDrawsAsAButtonThatOpensThePicker() {
        Builder builder = Builder.item("{\"metal\": \"5\"}");
        DrawnFrame frame = builder.frame();
        int lineTop = frame.find("=").y() - 5;
        int typeLeft = AREA.left() + ControlFlow.INDENT;
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), anyInt(), anyInt(),
                eq(ButtonPainter.BORDER));
        int border = -1;
        for (int index = 0; index < lefts.getAllValues().size(); index++) {
            if (lefts.getAllValues().get(index) == typeLeft && tops.getAllValues().get(index) == lineTop) {
                border = index;
            }
        }
        assertTrue(border >= 0, "no border at the type's place " + lefts.getAllValues() + " " + tops.getAllValues());
        assertTrue(frame.targets.click(typeLeft + 1, lineTop + 1));
        assertTrue(builder.pane.listOpen(), "the goo type picker opens");
    }

    /**
     * No builder control explains itself on hover; an X's tooltip reads the one word Delete (the operator's ruling).
     */
    @Test
    void onlyAnXCarriesATooltipAndItReadsDelete() {
        for (String shown : List.of("$base + ($waxed - $edited)", "{\"leaf\": \"4\"}", "")) {
            Builder builder = Builder.item(shown);
            List<String> hints = builder.frame().targets.hoverKeys().stream()
                    .filter(key -> BuilderOverlays.tooltipFor(key).isPresent()).distinct().toList();
            assertTrue(hints.stream().allMatch(BuilderOverlays.hintKey("Delete")::equals), shown + ": " + hints);
        }
    }

    /**
     * The Kind line draws one dropdown reading the chosen kind, its options listed under it on click (decision
     * exclusive-choice-sets-draw-as-dropdowns).
     */
    @Nested
    class KindDropdown {

        @Test
        void kindLineDrawsOneButtonReadingTheChosenKind() {
            List<String> texts = Builder.item("anvil").frame().texts();
            assertTrue(texts.contains(dropdown("relative")), texts.toString());
            assertFalse(texts.contains("by type") || texts.contains(dropdown("by type")), texts.toString());
        }

        @Test
        void clickingTheKindListsEveryKindUnderIt() {
            Builder builder = Builder.item("anvil");
            DrawnFrame closed = builder.frame();
            DrawnFrame.Text button = closed.find(dropdown("relative"));
            closed.click(dropdown("relative"));
            DrawnFrame open = builder.frame();
            DrawnFrame.Text perType = open.find("by type");
            DrawnFrame.Text items = open.find("relative");
            assertTrue(perType.y() > button.y() && items.y() > perType.y(), open.drawn().toString());
            assertTrue(builder.pane.listOpen());
        }

        @Test
        void pickingPerGooTypeSwitchesTheBuilderPerType() {
            Builder builder = Builder.item("anvil");
            builder.frame().click(dropdown("relative"));
            builder.frame().click("by type");
            DrawnFrame after = builder.frame();
            assertTrue(after.texts().contains("+"), after.texts().toString());
            assertTrue(after.texts().contains(dropdown("by type")), after.texts().toString());
            assertFalse(builder.pane.listOpen());
        }

        @Test
        void clickOutsideTheListClosesItAndChangesNothing() {
            Builder builder = Builder.item("anvil");
            builder.frame().click(dropdown("relative"));
            DrawnFrame open = builder.frame();
            assertTrue(open.targets.click(DrawnFrame.SCREEN_WIDTH - 1, DrawnFrame.SCREEN_HEIGHT - 1));
            assertFalse(builder.pane.listOpen());
            assertEquals(List.of(), builder.written);
            assertFalse(builder.frame().texts().contains("by type"));
        }
    }

    /**
     * Every reference term, sum, goo type row and multi-term whole value draws its ratio 1 : 1 at rest, either side
     * set first (decision scale-shows-the-ratio-at-once).
     */
    @Nested
    class RatioAtRest {

        // The sides of every ratio drawn, the text before each colon and the text after it, in drawn order.
        private static List<List<DrawnFrame.Text>> ratios(DrawnFrame frame) {
            List<DrawnFrame.Text> drawn = frame.drawn();
            List<List<DrawnFrame.Text>> ratios = new ArrayList<>();
            for (int index = 1; index < drawn.size() - 1; index++) {
                if (drawn.get(index).text().equals(":")) {
                    ratios.add(List.of(drawn.get(index - 1), drawn.get(index + 1)));
                }
            }
            return ratios;
        }

        // The ratio's sides read as the digits given, each a click target.
        private static void assertRatioReads(DrawnFrame frame, List<DrawnFrame.Text> sides, List<String> digits) {
            assertEquals(digits, sides.stream().map(DrawnFrame.Text::text).toList(), frame.texts().toString());
            sides.forEach(side -> assertTrue(frame.targets.takesClick(side.x() + 1, side.y() + 1),
                    side + " is no control"));
        }

        private static void assertOneToOneAtRest(DrawnFrame frame) {
            assertRatioReads(frame, ratioSides(frame), List.of("1", "1"));
        }

        @Test
        void freshReferenceTermDrawsOneToOne() {
            assertOneToOneAtRest(Builder.item("$waxed").frame());
        }

        @Test
        void freshSumDrawsOneToOne() {
            DrawnFrame frame = Builder.item("$base - ($waxed + $edited)").frame();
            assertTrue(frame.texts().contains("("), frame.texts().toString());
            List<List<DrawnFrame.Text>> ratios = ratios(frame);
            // $base, $waxed, $edited, the sum, then the whole value.
            assertEquals(5, ratios.size(), frame.texts().toString());
            ratios.forEach(sides -> assertRatioReads(frame, sides, List.of("1", "1")));
        }

        @Test
        void freshGooTypeRowDrawsOneToOne() {
            assertOneToOneAtRest(Builder.item("{\"metal\": \"$base\"}").frame());
        }

        @Test
        void wholeValueDrawsOneToOneAfterItsTerms() {
            DrawnFrame frame = Builder.item("$waxed + $edited").frame();
            List<List<DrawnFrame.Text>> ratios = ratios(frame);
            assertEquals(3, ratios.size(), frame.texts().toString());
            DrawnFrame.Text edited = frame.find("edited");
            assertTrue(ratios.getLast().getFirst().y() > edited.y(), frame.drawn().toString());
            assertOneToOneAtRest(frame);
        }

        private static Builder wholeValueCommitted(int side) {
            Builder builder = Builder.item("$waxed + $edited");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).get(side));
            builder.commitNumber("3");
            return builder;
        }

        @Test
        void committingTheWholeValuesDivisorFirstDividesTheSum() {
            assertEquals("($waxed + $edited) / 3", wholeValueCommitted(1).written.getLast());
        }

        @Test
        void committingTheWholeValuesMultiplierFirstMultipliesTheSum() {
            assertEquals("3 ($waxed + $edited)", wholeValueCommitted(0).written.getLast());
        }

        @Test
        void scaledWholeValueDrawsAsOneSumCarryingItsRatio() {
            DrawnFrame frame = wholeValueCommitted(1).frame();
            assertTrue(frame.texts().contains("("), frame.texts().toString());
            assertRatioReads(frame, ratioSides(frame), List.of("1", "3"));
        }

        @Test
        void committingTheDivisorFirstDividesTheTerm() {
            Builder builder = Builder.item("$waxed");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).get(1));
            builder.commitNumber("3");
            assertTrue(builder.written.getLast().endsWith(" / 3"), builder.written.toString());
        }

        // Decision ratio-holds-multiplier-and-divisor-together: the divisor box sets the divisor alone.
        @Test
        void committingTheDivisorKeepsTheMultiplier() {
            Builder builder = Builder.item("2 $iron_ingot");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).get(1));
            builder.commitNumber("3");
            assertEquals("2 $iron_ingot / 3", builder.written.getLast());
            DrawnFrame next = builder.frame();
            assertRatioReads(next, ratioSides(next), List.of("2", "3"));
        }

        @Test
        void committingTheMultiplierFirstWritesTheCountForm() {
            Builder builder = Builder.item("$waxed");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).getFirst());
            builder.commitNumber("3");
            assertEquals("3 $waxed", builder.written.getLast());
        }

        @Test
        void factorSetBackToOneKeepsDrawingOneToOneOnTheNextOpen() {
            Builder builder = Builder.item("3 $waxed");
            DrawnFrame frame = builder.frame();
            clickAt(frame, ratioSides(frame).getFirst());
            builder.commitNumber("1");
            builder.pane.open(builder.written.getLast(), builder.written::add, () -> { });
            assertOneToOneAtRest(builder.frame());
        }
    }

    /**
     * Remove draws as a red X whose tooltip reads Delete, and done as a green check with none (decisions remove-draws-as-a-red-x,
     * done-draws-green-with-a-check-mark).
     */
    @Nested
    class RemoveAndDone {
        private static final String X = "X";
        private static final String DONE = "✓ Done";

        private static List<DrawnFrame.Text> removes(DrawnFrame frame) {
            return frame.drawn().stream().filter(text -> text.text().equals(X)).toList();
        }

        @Test
        void termRemovesDrawAsRedXsReadingDelete() {
            DrawnFrame frame = Builder.item("$waxed + $edited").frame();
            assertFalse(frame.texts().contains("remove"), frame.texts().toString());
            List<DrawnFrame.Text> xs = removes(frame);
            assertEquals(2, xs.size(), frame.texts().toString());
            xs.forEach(x -> assertEquals(ExpressionBuilderPane.RED, x.color()));
            assertEquals(BuilderOverlays.hintKey("Delete"),
                    frame.targets.hovered(xs.getFirst().x() + 1, xs.getFirst().y() + 1).orElseThrow());
        }

        @Test
        void clickingATermsXRemovesThatTerm() {
            Builder builder = Builder.item("$waxed + $edited");
            DrawnFrame frame = builder.frame();
            clickAt(frame, removes(frame).getFirst());
            assertEquals("$edited", builder.written.getLast());
        }

        @Test
        void rowRemovesDrawAsRedXsReadingDelete() {
            Builder builder = Builder.item("{\"metal\": \"5\", \"pulse\": \"2\"}");
            DrawnFrame frame = builder.frame();
            assertFalse(frame.texts().contains("remove"), frame.texts().toString());
            List<DrawnFrame.Text> xs = removes(frame);
            assertEquals(2, xs.size(), frame.texts().toString());
            xs.forEach(x -> assertEquals(ExpressionBuilderPane.RED, x.color()));
            assertEquals(BuilderOverlays.hintKey("Delete"),
                    frame.targets.hovered(xs.getFirst().x() + 1, xs.getFirst().y() + 1).orElseThrow());
            clickAt(frame, xs.getFirst());
            assertFalse(builder.written.getLast().contains("metal"), builder.written.toString());
        }

        @Test
        void sumRemoveDrawsAsARedXReadingDelete() {
            DrawnFrame frame = Builder.item("$base + ($waxed - $edited)").frame();
            assertFalse(frame.texts().contains("remove"), frame.texts().toString());
            List<String> hints = removes(frame).stream().map(x -> frame.targets.hovered(x.x() + 1, x.y() + 1)
                    .orElseThrow()).toList();
            assertTrue(hints.contains(BuilderOverlays.hintKey("Delete")),
                    hints.toString());
        }

        @Test
        void doneDrawsGreenWithACheckMarkAndNoTooltip() {
            boolean[] finished = {false};
            Builder builder = new Builder(PREFABS);
            builder.pane.open("$waxed + $edited", builder.written::add, () -> finished[0] = true);
            DrawnFrame opened = builder.frame();
            clickAt(opened, removes(opened).getFirst());
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text done = frame.find(DONE);
            assertEquals(ExpressionBuilderPane.GREEN, done.color());
            assertTrue(frame.targets.hovered(done.x() + 1, done.y() + 1).isEmpty(), "done carries no tooltip");
            frame.click(DONE);
            assertTrue(finished[0]);
            assertFalse(builder.pane.isOpen());
        }

        /**
         * Until a change is fully in, the builder's one button reads cancel, closing it on the opening value
         * (decision value-expression-boxes-take-a-cancel-button).
         */
        @Test
        void unchangedBuilderDrawsCancelInPlaceOfDone() {
            boolean[] finished = {false};
            Builder builder = new Builder(PREFABS);
            builder.pane.open("$waxed", builder.written::add, () -> finished[0] = true);
            DrawnFrame unchanged = builder.frame();
            assertFalse(unchanged.texts().contains(DONE), unchanged.texts().toString());
            DrawnFrame.Text cancel = unchanged.find(CANCEL);
            assertEquals(ExpressionBuilderPane.RED, cancel.color());
            unchanged.click(CANCEL);
            assertTrue(finished[0]);
            assertFalse(builder.pane.isOpen());
        }

        /**
         * A partial value, one the draft cannot take yet, still reads cancel alone, so cancel is always there to
         * back out (decision value-expression-boxes-take-a-cancel-button).
         */
        @Test
        void partialValueDrawsCancelAloneAndRestoresTheOpeningValue() {
            boolean[] finished = {false};
            Builder builder = new Builder(PREFABS);
            builder.pane.open("$waxed", builder.written::add, () -> finished[0] = true);
            clickAt(builder.frame(), ratioSides(builder.frame()).getFirst());
            ArgumentCaptor<AbstractWidget> focused = ArgumentCaptor.forClass(AbstractWidget.class);
            verify(builder.actions, atLeastOnce()).focus(focused.capture());
            ((EditBox) focused.getValue()).setValue("7");
            DrawnFrame partial = builder.frame();
            assertFalse(partial.texts().contains(DONE), partial.texts() + " " + builder.written);
            partial.click(CANCEL);
            assertEquals(List.of("$waxed"), builder.written, partial.texts().toString());
            assertTrue(finished[0]);
        }

        /**
         * Once a change is fully in, done draws with cancel beside it, and cancel writes the opening value back and
         * closes the builder (decision value-expression-boxes-take-a-cancel-button).
         */
        @Test
        void cancelDrawsBesideDoneOnceChangedAndRestoresTheOpeningValue() {
            boolean[] finished = {false};
            Builder builder = new Builder(PREFABS);
            builder.pane.open("$waxed + $edited", builder.written::add, () -> finished[0] = true);
            DrawnFrame unchanged = builder.frame();
            clickAt(unchanged, removes(unchanged).getFirst());
            DrawnFrame changed = builder.frame();
            DrawnFrame.Text done = changed.find(DONE);
            DrawnFrame.Text cancel = changed.find(CANCEL);
            assertEquals(done.y(), cancel.y());
            assertTrue(cancel.x() < done.x(), changed.drawn().toString());
            changed.click(CANCEL);
            assertEquals("$waxed + $edited", builder.written.getLast());
            assertTrue(finished[0]);
            assertFalse(builder.pane.isOpen());
        }
    }

    private static final String CANCEL = "✗ Cancel";
}
