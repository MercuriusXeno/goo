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
import java.util.stream.IntStream;
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
    // Where the builder's lines end, short of the trash strip by a gap.
    private static final int CONTENT_RIGHT = AREA.right() - ExpressionBuilderPane.TRASH_WIDTH - BuilderOverlays.GAP;
    // A type row's rail and the gap after it, which stand before its goo type button.
    private static final int ROW_RAIL = ControlFlow.RAIL_ADVANCE;

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
            return frame(AREA);
        }

        DrawnFrame frame(GroupsPane.Bounds area) {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, font, area, 0, frame.targets);
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

    // Where the rail of the term at a place answers, found by its hover across the screen.
    private static double[] railOf(DrawnFrame frame, List<Integer> path) {
        String key = ExpressionBuilderPane.railHoverKey(path);
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                    return new double[] {x, y};
                }
            }
        }
        throw new AssertionError("no rail for " + path + " in " + frame.targets.hoverKeys());
    }

    // The tops of each fill drawn in the drop cursor's blue.
    private static List<Integer> cursorTops(DrawnFrame frame) {
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).fill(anyInt(), tops.capture(), anyInt(), anyInt(),
                eq(ExpressionBuilderPane.DROP_CURSOR));
        return tops.getAllValues();
    }

    /**
     * Each term draws a drag rail in place of ^ and v; a press on the rail and a drag past the last term draws the
     * blue drop cursor on the last term's line, and the release there moves the term to the end (decision
     * terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     */
    @Test
    void railDragDrawsTheDropCursorAndTheReleaseMovesTheTerm() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        assertFalse(frame.texts().contains("^") || frame.texts().contains("v"), frame.texts().toString());
        double[] rail = railOf(frame, List.of(0));
        assertTrue(frame.targets.click(rail[0], rail[1]));
        DrawnFrame.Text base = frame.find("base");
        assertTrue(frame.targets.drag(AREA.right() - 40, base.y()));
        DrawnFrame dragging = builder.frame();
        List<Integer> cursor = cursorTops(dragging);
        assertEquals(1, cursor.size(), "one drop cursor: " + cursor);
        assertTrue(Math.abs(cursor.getFirst() - base.y()) < ControlFlow.LINE, cursor + " vs " + base);
        assertTrue(dragging.texts().stream().filter("waxed"::equals).count() > 1, "the term draws at the mouse");
        frame.targets.release();
        assertEquals("$edited + $base + $waxed", builder.written.getLast());
    }

    private static final String HANDLE = "()";

    private static List<DrawnFrame.Text> handles(DrawnFrame frame) {
        return frame.drawn().stream().filter(text -> text.text().equals(HANDLE)).toList();
    }

    /**
     * A per-type value draws one () handle beside the [+?] below its rows, and no term carries one (decision
     * parenthetical-handle-drags-onto-a-term).
     */
    @Test
    void perTypeValueDrawsOneHandleBesideAddType() {
        DrawnFrame frame = Builder.item("{\"metal\": \"5 $base\", \"pulse\": \"2\"}").frame();
        List<DrawnFrame.Text> found = handles(frame);
        assertEquals(1, found.size(), frame.drawn().toString());
        DrawnFrame.Text addType = frame.find("+?");
        assertEquals(addType.y(), found.getFirst().y(), frame.drawn().toString());
        assertTrue(found.getFirst().x() > addType.x() + ControlFlow.LINE, frame.drawn().toString());
    }

    /**
     * A whole-value sum draws one () handle below its last term's line, none on a term's line (decision
     * parenthetical-handle-drags-onto-a-term).
     */
    @Test
    void wholeValueSumDrawsOneHandleBelowItsTerms() {
        DrawnFrame frame = Builder.item("$waxed + $edited").frame();
        List<DrawnFrame.Text> found = handles(frame);
        assertEquals(1, found.size(), frame.drawn().toString());
        assertTrue(found.getFirst().y() > frame.find("edited").y(), frame.drawn().toString());
        assertTrue(found.getFirst().y() > frame.find("waxed").y(), frame.drawn().toString());
    }

    /**
     * A press on the () handle and a move over the second term fills that term's box in the cursor's blue, and the
     * release there wraps the term (decision parenthetical-handle-drags-onto-a-term).
     */
    @Test
    void handleDraggedOntoATermMarksItBlueAndTheReleaseWrapsIt() {
        Builder builder = Builder.item("$waxed + $edited");
        DrawnFrame frame = builder.frame();
        clickAt(frame, handles(frame).getFirst());
        DrawnFrame.Text edited = frame.find("edited");
        assertTrue(frame.targets.drag(edited.x() + 1, edited.y() + 1));
        DrawnFrame dragging = builder.frame();
        assertTrue(filledAround(dragging, ExpressionBuilderPane.WRAP_MARK, dragging.find("edited")),
                "the term under the pointer is not marked");
        assertFalse(filledAround(dragging, ExpressionBuilderPane.WRAP_MARK, dragging.find("waxed")),
                "a term off the pointer is marked");
        assertTrue(dragging.texts().stream().filter(HANDLE::equals).count() > 1, "the () draws at the mouse");
        frame.targets.release();
        assertEquals("$waxed + ($edited)", builder.written.getLast());
    }

    /**
     * A drag on the () handle released over no term, or ended by Escape, writes nothing (decision
     * parenthetical-handle-drags-onto-a-term).
     */
    @Test
    void handleReleasedOffATermOrEscapedWrapsNothing() {
        Builder builder = Builder.item("$waxed + $edited");
        DrawnFrame frame = builder.frame();
        clickAt(frame, handles(frame).getFirst());
        assertTrue(frame.targets.drag(AREA.right() - 5, AREA.bottom() - 5));
        builder.frame();
        frame.targets.release();
        assertEquals(List.of(), builder.written);
        DrawnFrame again = builder.frame();
        clickAt(again, handles(again).getFirst());
        DrawnFrame.Text edited = again.find("edited");
        assertTrue(again.targets.drag(edited.x() + 1, edited.y() + 1));
        builder.frame();
        assertTrue(builder.pane.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(filledAround(builder.frame(), ExpressionBuilderPane.WRAP_MARK, edited), "the mark stays");
        again.targets.release();
        assertEquals(List.of(), builder.written);
    }

    /**
     * A parenthetical's box fills in the depth-one tone, and a parenthetical inside it in the depth-two tone, which
     * differs, each box spanning its own terms (decision parentheticals-alternate-gray-tones-by-depth).
     */
    @Test
    void parentheticalsFillInAlternatingTonesByDepth() {
        DrawnFrame frame = Builder.item("$waxed + ($edited + ($base + $waxed))").frame();
        assertTrue(ExpressionBuilderPane.nestTone(1) != ExpressionBuilderPane.nestTone(2));
        assertEquals(ExpressionBuilderPane.nestTone(1), ExpressionBuilderPane.nestTone(3));
        DrawnFrame.Text edited = frame.find("edited");
        DrawnFrame.Text base = frame.find("base");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(1), edited), "no depth-one box around edited");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(1), base), "the depth-one box holds base");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(2), base), "no depth-two box around base");
        assertFalse(filledAround(frame, ExpressionBuilderPane.nestTone(2), edited), "the depth-two box spans edited");
    }

    // Each fill in the color, as left, top, right, bottom.
    private static List<int[]> fills(DrawnFrame frame, int color) {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).fill(lefts.capture(), tops.capture(),
                rights.capture(), bottoms.capture(), eq(color));
        return IntStream.range(0, lefts.getAllValues().size()).mapToObj(index -> new int[] {
                lefts.getAllValues().get(index), tops.getAllValues().get(index),
                rights.getAllValues().get(index), bottoms.getAllValues().get(index)}).toList();
    }

    // Presses the term's rail and drags it onto the trash strip, answering where it was released.
    private static double[] dragToTrash(DrawnFrame frame, List<Integer> path) {
        double[] rail = railOf(frame, path);
        assertTrue(frame.targets.click(rail[0], rail[1]));
        double[] strip = {AREA.right() - ExpressionBuilderPane.TRASH_WIDTH / 2.0, rail[1]};
        assertTrue(frame.targets.drag(strip[0], strip[1]));
        return strip;
    }

    private static void trash(DrawnFrame frame, List<Integer> path) {
        dragToTrash(frame, path);
        frame.targets.release();
    }

    /**
     * The trash strip fills the builder's right edge over its full height in the rest tone; a term's rail drag
     * lights it, the pointer over it brightens it, and the release there deletes the term (decision
     * terms-and-type-rows-drag-to-a-trash-strip).
     */
    @Test
    void trashStripRestsLightsWhileDraggingAndDeletesTheTermReleasedOnIt() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        int[] panel = fills(frame, ExpressionBuilderPane.PANEL_COLOR).getFirst();
        int[] strip = fills(frame, ExpressionBuilderPane.TRASH_REST).getFirst();
        assertEquals(List.of(AREA.right() - ExpressionBuilderPane.TRASH_WIDTH, panel[1], AREA.right(), panel[3]),
                List.of(strip[0], strip[1], strip[2], strip[3]), "the strip spans the builder's right edge");
        double[] rail = railOf(frame, List.of(1));
        assertTrue(frame.targets.click(rail[0], rail[1]));
        assertTrue(frame.targets.drag(AREA.left() + 1, rail[1]));
        DrawnFrame lit = builder.frame();
        assertEquals(1, fills(lit, ExpressionBuilderPane.TRASH_LIT).size(), "the strip lights while dragging");
        assertEquals(List.of(), fills(lit, ExpressionBuilderPane.TRASH_REST));
        assertTrue(frame.targets.drag(AREA.right() - 1, rail[1]));
        DrawnFrame hovered = builder.frame();
        assertEquals(1, fills(hovered, ExpressionBuilderPane.TRASH_HOVER).size(), "the strip brightens under it");
        assertTrue(cursorTops(hovered).isEmpty(), "no drop cursor while over the strip");
        frame.targets.release();
        assertEquals("$waxed + $base", builder.written.getLast());
        assertEquals(1, fills(builder.frame(), ExpressionBuilderPane.TRASH_REST).size(), "the strip rests again");
    }

    /**
     * A parenthetical dragged by its rail onto the trash strip goes whole (decision
     * terms-and-type-rows-drag-to-a-trash-strip).
     */
    @Test
    void parentheticalReleasedOnTheTrashStripGoes() {
        Builder builder = Builder.item("$base + ($waxed - $edited)");
        trash(builder.frame(), List.of(1));
        assertEquals("$base", builder.written.getLast());
    }

    /**
     * A term's rail draws three one-pixel grip lines inside it, and stands a pixel clear of its term's outline on
     * every side (decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     */
    @Test
    void railDrawsThreeGripLinesClearOfItsTermsOutline() {
        DrawnFrame frame = Builder.item("$waxed + $edited").frame();
        double[] point = railOf(frame, List.of(1));
        int[] rail = fills(frame, ControlFlow.RAIL_COLOR).stream()
                .filter(fill -> fill[1] <= point[1] + ControlFlow.LINE && fill[3] > point[1]
                        && fill[0] <= point[0] + ControlFlow.RAIL_WIDTH + 1 && fill[2] > point[0])
                .findFirst().orElseThrow(() -> new AssertionError("no rail fill near " + point[0]));
        List<int[]> grips = fills(frame, ControlFlow.GRIP_COLOR).stream()
                .filter(fill -> fill[0] >= rail[0] && fill[2] <= rail[2] && fill[1] >= rail[1] && fill[3] <= rail[3])
                .toList();
        assertEquals(3, grips.size(), "grip lines inside the rail");
        grips.forEach(grip -> assertEquals(1, grip[3] - grip[1], "a grip line is one pixel tall"));
        assertEquals(3, grips.stream().map(grip -> grip[1]).distinct().count(), "the grip lines stand apart");
        int line = rail[1] / ControlFlow.LINE * ControlFlow.LINE;
        List<int[]> outline = fills(frame, ControlFlow.OUTLINE_COLOR).stream()
                .filter(fill -> fill[1] >= line && fill[3] <= line + ControlFlow.LINE && fill[0] < rail[2])
                .toList();
        int outlineLeft = outline.stream().mapToInt(fill -> fill[0]).filter(left -> left < rail[0]).max()
                .orElseThrow(() -> new AssertionError("no outline left of the rail"));
        assertTrue(rail[0] >= outlineLeft + 2, "the rail at " + rail[0] + " touches the outline at " + outlineLeft);
        assertTrue(rail[1] >= line + 2 && rail[3] <= line + ControlFlow.LINE - 2,
                "the rail's ends touch the outline: " + java.util.Arrays.toString(rail));
    }

    private static final String TWO_TYPES = "{\"metal\": \"5\", \"pulse\": \"2\"}";

    // Where a type row's rail answers, found by its hover across the screen.
    private static double[] rowRailOf(DrawnFrame frame, int row) {
        String key = ExpressionBuilderPane.rowRailHoverKey(row);
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                    return new double[] {x, y};
                }
            }
        }
        throw new AssertionError("no rail for row " + row + " in " + frame.targets.hoverKeys());
    }

    // Each goo icon blit as its x and y.
    private static List<int[]> iconBlits(DrawnFrame frame) {
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).blit(any(), any(), xs.capture(), ys.capture(),
                org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(), anyInt(),
                anyInt(), anyInt(), anyInt(), anyInt());
        return IntStream.range(0, xs.getAllValues().size())
                .mapToObj(index -> new int[] {xs.getAllValues().get(index), ys.getAllValues().get(index)}).toList();
    }

    /**
     * Each type row draws a rail left of its goo icon and no X (decision type-rows-drag-by-a-rail-to-reorder-or-trash).
     */
    @Test
    void typeRowsDrawARailLeftOfTheirIconAndNoX() {
        DrawnFrame frame = Builder.item(TWO_TYPES).frame();
        for (int row = 0; row < 2; row++) {
            double[] rail = rowRailOf(frame, row);
            int line = (int) rail[1] / ControlFlow.LINE;
            int icon = iconBlits(frame).stream().filter(blit -> blit[1] / ControlFlow.LINE == line)
                    .mapToInt(blit -> blit[0]).min().orElseThrow(() -> new AssertionError("no icon on " + line));
            assertTrue(rail[0] < icon, "row " + row + "'s rail at " + rail[0] + " vs its icon at " + icon);
        }
        assertTrue(frame.texts().stream().noneMatch("X"::equals), frame.drawn().toString());
    }

    /**
     * A type row's rail drag draws the drop cursor at the row slot under the pointer and the release there reorders
     * the rows; a release on the trash strip removes the type (decision type-rows-drag-by-a-rail-to-reorder-or-trash).
     */
    @Test
    void typeRowDragReordersTheRowsAndTheTrashStripRemovesOne() {
        Builder builder = Builder.item(TWO_TYPES);
        DrawnFrame frame = builder.frame();
        double[] rail = rowRailOf(frame, 0);
        assertTrue(frame.targets.click(rail[0], rail[1]));
        DrawnFrame.Text add = frame.find("+?");
        assertTrue(frame.targets.drag(rail[0], add.y()));
        DrawnFrame dragging = builder.frame();
        List<Integer> cursor = cursorTops(dragging);
        assertEquals(1, cursor.size(), "one drop cursor: " + cursor);
        assertTrue(Math.abs(cursor.getFirst() - add.y()) < ControlFlow.LINE, cursor + " vs " + add);
        frame.targets.release();
        assertEquals("{\"pulse\": \"2\", \"metal\": \"5\"}", builder.written.getLast());
        Builder trashing = Builder.item(TWO_TYPES);
        DrawnFrame opened = trashing.frame();
        double[] first = rowRailOf(opened, 0);
        assertTrue(opened.targets.click(first[0], first[1]));
        assertTrue(opened.targets.drag(AREA.right() - 1, first[1]));
        assertEquals(1, fills(trashing.frame(), ExpressionBuilderPane.TRASH_HOVER).size());
        opened.targets.release();
        assertEquals("{\"pulse\": \"2\"}", trashing.written.getLast());
    }

    /**
     * A type row released on its own slot, or dropped by Escape, writes nothing (decision
     * type-rows-drag-by-a-rail-to-reorder-or-trash).
     */
    @Test
    void typeRowReleasedOnItsOwnSlotOrEscapedWritesNothing() {
        Builder builder = Builder.item(TWO_TYPES);
        DrawnFrame frame = builder.frame();
        double[] rail = rowRailOf(frame, 1);
        assertTrue(frame.targets.click(rail[0], rail[1]));
        assertTrue(frame.targets.drag(rail[0], rail[1]));
        builder.frame();
        frame.targets.release();
        assertEquals(List.of(), builder.written);
        DrawnFrame again = builder.frame();
        double[] first = rowRailOf(again, 0);
        assertTrue(again.targets.click(first[0], first[1]));
        assertTrue(again.targets.drag(first[0], again.find("+?").y()));
        builder.frame();
        assertTrue(builder.pane.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertTrue(cursorTops(builder.frame()).isEmpty(), "the drop cursor goes with the drag");
        again.targets.release();
        assertEquals(List.of(), builder.written);
    }

    // Whether a fill in the color covers the text's top-left.
    private static boolean filledAround(DrawnFrame frame, int color, DrawnFrame.Text text) {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).fill(lefts.capture(), tops.capture(),
                rights.capture(), bottoms.capture(), eq(color));
        return IntStream.range(0, lefts.getAllValues().size()).anyMatch(index ->
                lefts.getAllValues().get(index) <= text.x() && rights.getAllValues().get(index) > text.x()
                        && tops.getAllValues().get(index) <= text.y() && bottoms.getAllValues().get(index) > text.y());
    }

    /**
     * A release on the dragged term's own slot, or Escape before the release, leaves the value as it opened
     * (decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     */
    @Test
    void releaseOnTheOriginOrEscapeLeavesTheValue() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        double[] rail = railOf(frame, List.of(1));
        assertTrue(frame.targets.click(rail[0], rail[1]));
        assertTrue(frame.targets.drag(rail[0], rail[1]));
        frame.targets.release();
        assertEquals(List.of(), builder.written);
        DrawnFrame again = builder.frame();
        double[] second = railOf(again, List.of(1));
        assertTrue(again.targets.click(second[0], second[1]));
        assertTrue(again.targets.drag(AREA.right() - 40, again.find("base").y()));
        builder.frame();
        assertTrue(builder.pane.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertTrue(cursorTops(builder.frame()).isEmpty(), "the drop cursor goes with the drag");
        again.targets.release();
        assertEquals(List.of(), builder.written);
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
        assertFalse(closed.texts().contains("$edited"), closed.texts().toString());
        assertFalse(closed.rightClick("waxed"));
        verify(builder.actions, never()).openVariable(any());
        DrawnFrame unmoved = builder.frame();
        assertFalse(unmoved.texts().contains("$edited"), unmoved.texts().toString());
        unmoved.shiftClick("waxed");
        verify(builder.actions).openVariable("waxed");
        assertFalse(builder.frame().texts().contains("$edited"));
        builder.frame().click("waxed");
        List<String> opened = builder.frame().texts();
        assertTrue(opened.contains("$edited"), opened.toString());
    }

    /**
     * Over beetroot's leaf value, the builder offers no "+ sum of terms" and draws no "the sum of:"; its parenthetical
     * reads "(", its two terms inline, then ")" past them (decisions sum-of-terms-leaves-the-builder,
     * builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    @Test
    void parentheticalReadsItsTermsInlineBetweenItsParens() {
        DrawnFrame frame = Builder.item("$dye.leaf - ($food.jelly + $saturation.jelly * 12)").frame();
        assertFalse(frame.texts().contains("+ sum of terms") || frame.texts().contains("the sum of:"),
                frame.texts().toString());
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        DrawnFrame.Text food = frame.find("food");
        DrawnFrame.Text saturation = frame.find("saturation");
        assertEquals(List.of(open.y(), open.y(), open.y()), List.of(food.y(), saturation.y(), close.y()),
                frame.drawn().toString());
        assertTrue(open.x() < food.x() && food.x() < saturation.x() && saturation.x() < close.x(),
                frame.drawn().toString());
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

        // The row's colon: gray, where the ratio's colons draw in the link color inside their boxes.
        private static DrawnFrame.Text rowColon(DrawnFrame frame, int index) {
            return line(frame, index).stream().filter(text -> text.text().equals(":") && text.color() == 0xFFA0A0A0)
                    .findFirst().orElseThrow(() -> new AssertionError("no row colon on line " + index + " of "
                            + frame.drawn()));
        }

        // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
        @Test
        void typeRowReadsItsColonThenItsTermsInline() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            DrawnFrame.Text colon = rowColon(frame, 1);
            int typeX = iconXs(frame, 1).getFirst();
            assertEquals(ControlFlow.INDENT + ROW_RAIL + ButtonPainter.PADDING, typeX);
            assertTrue(colon.x() > typeX, colon + " after the type at " + typeX);
            DrawnFrame.Text food = frame.findFirst("food");
            assertEquals(colon.y(), food.y(), frame.drawn().toString());
            assertTrue(food.x() > colon.x(), food + " after " + colon);
            assertEquals(0, frame.count("="), frame.texts().toString());
        }

        // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
        @Test
        void narrowRowsWrapUnderTheIndentationRules() {
            Builder builder = Builder.item(BEETROOT);
            DrawnFrame frame = builder.frame(new GroupsPane.Bounds(0, 0, 220, 390));
            int typeLeft = ControlFlow.INDENT;
            DrawnFrame.Text food = frame.findFirst("food");
            DrawnFrame.Text saturation = frame.findFirst("saturation");
            assertTrue(saturation.y() > food.y(), frame.drawn().toString());
            // A wrapped term's line opens on its rail, one indent in under the type.
            double[] saturationRail = railOf(frame, List.of(0, 1));
            assertEquals(typeLeft + ControlFlow.INDENT, (int) saturationRail[0], frame.drawn().toString());
            assertEquals(saturation.y() / ControlFlow.LINE, (int) saturationRail[1] / ControlFlow.LINE);
            DrawnFrame.Text dye = frame.find("dye");
            DrawnFrame.Text minus = frame.drawn().stream().filter(text -> text.text().equals("-")
                    && text.y() > dye.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            double[] parentheticalRail = railOf(frame, List.of(1, 1));
            assertEquals(typeLeft + ControlFlow.INDENT, (int) parentheticalRail[0], frame.drawn().toString());
            assertEquals(minus.y() / ControlFlow.LINE, (int) parentheticalRail[1] / ControlFlow.LINE);
            assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("(") && text.y() == minus.y()),
                    frame.drawn().toString());
            // A wrap inside the parenthetical opens on the wrapped term's rail, one indent further in.
            int innerLine = frame.drawn().stream().filter(text -> text.text().equals("saturation")
                    && text.y() > minus.y()).findFirst().orElseThrow().y() / ControlFlow.LINE;
            double[] innerRail = railOf(frame, List.of(1, 1, 1));
            assertEquals(innerLine, (int) innerRail[1] / ControlFlow.LINE, frame.drawn().toString());
            assertEquals((int) parentheticalRail[0] + ControlFlow.INDENT, (int) innerRail[0], frame.drawn().toString());
        }

        @Test
        void beetrootsJellyRowReadsKindPlusThenTheRowInline() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            assertEquals(List.of(dropdown("by type")), lineTexts(frame, 0), frame.drawn().toString());
            assertEquals(List.of(":", "food", "'s", "1", ":", "1", "+", "saturation", "'s", "x", "12", SUM_ADD),
                    lineTexts(frame, 1), frame.drawn().toString());
            assertEquals(ControlFlow.INDENT + ROW_RAIL + ButtonPainter.PADDING, iconXs(frame, 1).getFirst());
            int jelly = iconXs(frame, 1).get(1);
            assertTrue(line(frame, 1).get(2).x() < jelly && jelly < line(frame, 1).get(3).x(),
                    "jelly between 's and the ratio at " + jelly);
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

        // Decision add-type-is-a-big-square-plus-question-button-below-the-list.
        @Test
        void squarePlusQuestionBelowTheRowsAppendsATypeRow() {
            Builder builder = Builder.item(BEETROOT);
            DrawnFrame before = builder.frame();
            assertEquals(List.of(dropdown("by type")), lineTexts(before, 0), before.drawn().toString());
            DrawnFrame.Text add = before.find("+?");
            DrawnFrame.Text lastRow = before.drawn().stream().filter(text -> text.text().equals(ExpressionLines.COLON)
                    && text.x() == rowColon(before, 1).x()).reduce((first, last) -> last).orElseThrow();
            assertTrue(add.y() > lastRow.y(), add + " below " + lastRow);
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
            verify(before.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), rights.capture(),
                    bottoms.capture(), eq(ButtonPainter.BORDER));
            boolean square = IntStream.range(0, lefts.getAllValues().size()).anyMatch(index ->
                    lefts.getAllValues().get(index) < add.x() && rights.getAllValues().get(index) > add.x()
                            && tops.getAllValues().get(index) < add.y()
                            && rights.getAllValues().get(index) - lefts.getAllValues().get(index) == ControlFlow.LINE
                            && bottoms.getAllValues().get(index) - tops.getAllValues().get(index) == ControlFlow.LINE);
            assertTrue(square, "no square box a type button's height around " + add);
            long rows = rowColons(before);
            before.click("+?");
            pickType(builder.frame(), "metal");
            DrawnFrame after = builder.frame();
            assertEquals(rows + 1, rowColons(after), after.texts().toString());
            assertTrue(builder.written.getLast().endsWith("\"metal\": \"1\"}"), builder.written.toString());
        }

        // A row's colon stands at the row's own x past its type, where a ratio's colon never does.
        private static long rowColons(DrawnFrame frame) {
            int rowColonX = rowColon(frame, 1).x();
            return frame.drawn().stream().filter(text -> text.text().equals(ExpressionLines.COLON)
                    && text.x() == rowColonX).count();
        }

        // Leaf's parenthetical is subtracted, so neither it nor dye ahead of it draws a move that would land it first
        // and drop its minus; the parenthetical takes a line of its own, "-" "(" then its terms inline, and no X
        // (decision terms-and-type-rows-drag-to-a-trash-strip).
        @Test
        void parentheticalLineReadsJoinParenThenItsTermsInline() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            DrawnFrame.Text open = frame.find("(");
            int head = open.y() / ControlFlow.LINE;
            assertEquals(List.of("-", "(", "food"), lineTexts(frame, head).subList(0, 3), frame.drawn().toString());
            assertFalse(lineTexts(frame, head).contains("X"), frame.drawn().toString());
            assertEquals(List.of(":", "dye", "'s", "1", ":", "1"), lineTexts(frame, head - 1),
                    frame.drawn().toString());
            assertEquals(open.y(), frame.find(")").y(), frame.drawn().toString());
            assertTrue(frame.find(")").x() > frame.find("saturation").x(), frame.drawn().toString());
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
            builder.frame().click("$base");
            assertEquals("$base - $base", builder.written.getLast());
        }

        // Decision amount-modes-name-what-the-amount-comes-from: a number term names its amount's source, so a type
        // row's amount takes a prefab's quantity in place of the number.
        // Decision term-number-opens-number-prefab-relative-modes: the number itself opens the modes.
        @Test
        void numberTermTakesAPrefabsQuantityThroughItsAmountMode() {
            Builder builder = Builder.prefab(PREFABS, "{\"metal\": \"5\"}");
            DrawnFrame closed = builder.frame();
            assertFalse(closed.texts().contains(dropdown("number")), closed.texts().toString());
            closed.click("5");
            DrawnFrame modes = builder.frame();
            assertTrue(modes.texts().containsAll(List.of("number", "prefab", "relative")), modes.texts().toString());
            modes.click("prefab");
            builder.frame().click("$base");
            assertEquals("{\"metal\": \"$base\"}", builder.written.getLast());
        }

        // Decision term-number-opens-number-prefab-relative-modes: number opens the number box.
        @Test
        void numberModeOpensTheNumberBox() {
            Builder builder = Builder.prefab(PREFABS, "{\"metal\": \"5\"}");
            builder.frame().click("5");
            builder.frame().click("number");
            builder.commitNumber("7");
            assertEquals("{\"metal\": \"7\"}", builder.written.getLast());
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
            assertEquals(CONTENT_RIGHT, done.x() - ButtonPainter.PADDING + ButtonPainter.width(font, "✓ Done"));
            assertEquals(done.y(), cancel.y());
            assertEquals(changed.find("()").y() / ControlFlow.LINE, done.y() / ControlFlow.LINE);
            assertFalse(changed.texts().contains("Value"), changed.texts().toString());
        }
    }

    /**
     * Every builder, Items, Groups and Prefabs alike, reads its kind first, an empty value defaulting to by type with
     * the +? that adds a goo type on the line below (decisions
     * every-value-builder-reads-value-kind-on-one-line-defaulting-by-type,
     * add-type-is-a-big-square-plus-question-button-below-the-list).
     */
    @Nested
    class ValueLine {
        private static final List<String> RETIRED = List.of("Kind:", "Kind", "pick one", dropdown("pick one"),
                "No value yet: pick a kind to give it one.", "Pick the first goo type:", "+ goo type", "amount from",
                "from other items", dropdown("from other items"));

        private static void assertEmptyReadsByTypeAndPlus(Builder builder) {
            DrawnFrame frame = builder.frame();
            List<String> texts = frame.texts();
            assertEquals(List.of(dropdown("by type"), "+?"), texts.subList(0, 2), texts.toString());
            assertTrue(frame.find("+?").y() > frame.find(dropdown("by type")).y(), "the +? stands below the kind");
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
            builder.frame().click("+?");
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
        int lineTop = frame.findFirst(ExpressionLines.COLON).y() - 5;
        int typeLeft = AREA.left() + ControlFlow.INDENT + ROW_RAIL;
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
            assertTrue(after.texts().contains("+?"), after.texts().toString());
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

        /**
         * No term or parenthetical draws an X; the trash strip is their one deletion (decisions
         * terms-and-type-rows-drag-to-a-trash-strip, term-operators-right-pack).
         */
        @Test
        void noTermOrParentheticalDrawsAnX() {
            DrawnFrame frame = Builder.item("$base + $waxed + ($waxed - $edited)").frame();
            assertEquals(List.of(), removes(frame), frame.drawn().toString());
        }

        @Test
        void doneDrawsGreenWithACheckMarkAndNoTooltip() {
            boolean[] finished = {false};
            Builder builder = new Builder(PREFABS);
            builder.pane.open("$waxed + $edited", builder.written::add, () -> finished[0] = true);
            DrawnFrame opened = builder.frame();
            trash(opened, List.of(0));
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
            trash(unchanged, List.of(0));
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
