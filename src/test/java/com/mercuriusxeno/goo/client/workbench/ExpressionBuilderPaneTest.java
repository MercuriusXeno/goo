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
    // The room a term box leaves between its left border and its rail: none, the rail standing flush.
    private static final int BOX_ROOM = 0;

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
        String key = ExpressionBuilderPane.railName(path);
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                if (frame.targets.grabNamed(x, y).filter(key::equals).isPresent()) {
                    return new double[] {x, y};
                }
            }
        }
        throw new AssertionError("no rail for " + path);
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

    /**
     * Every box a builder draws, a term's outline, a parenthetical's border and a type row's outline, keeps a clear
     * pixel between its border and every other box's border, so a parenthetical inside a parenthetical, a term inside
     * a parenthetical and a term or parenthetical inside a type row each stand a pixel of padding inside the box
     * holding them on every side, inline as before, the lines growing taller per depth; and no border shares a pixel
     * with a button (decision drag-rail-containers-keep-a-pixel-between-borders).
     */
    @Test
    void railBoxesKeepAPixelBetweenBordersAtEveryDepth() {
        for (String shown : List.of("$waxed + ($edited + ($base + $waxed))", "$waxed + (($base + $waxed) + $edited)",
                "$waxed + (($base + $waxed))", "{\"metal\": \"$base + ($waxed + ($edited))\", \"pulse\": \"2\"}")) {
            assertBordersKeepAPixelApart(Builder.item(shown).frame(), shown);
        }
    }

    /**
     * A parenthetical opening a parenthetical stays on the line of the outer one's "(", the line growing to give
     * the inner box its padding (decision drag-rail-containers-keep-a-pixel-between-borders).
     */
    @Test
    void nestedParentheticalsStayInline() {
        DrawnFrame frame = Builder.item("$waxed + (($base + $waxed) + $edited)").frame();
        double[] outer = railOf(frame, List.of(1));
        double[] inner = railOf(frame, List.of(1, 0));
        DrawnFrame.Text open = frame.drawn().stream().filter(text -> text.text().equals("(")).findFirst().orElseThrow();
        assertEquals(open.y(), frame.find("base").y(), "the inner parenthetical's terms share the outer's ( line");
        assertTrue(inner[0] > outer[0], "the inner rail stands right of the outer one");
    }

    // Each box's four sides, grouped as each box draws them, in the given border color.
    private static List<List<int[]>> boxesIn(DrawnFrame frame, int color) {
        List<int[]> sides = fills(frame, color);
        assertEquals(0, sides.size() % 4, "four sides a box");
        return IntStream.range(0, sides.size() / 4).mapToObj(box -> sides.subList(box * 4, box * 4 + 4)).toList();
    }

    private static void assertBordersKeepAPixelApart(DrawnFrame frame, String shown) {
        List<List<int[]>> boxes = new ArrayList<>(boxesIn(frame, ExpressionBuilderPane.NEST_BORDER));
        boxes.addAll(boxesIn(frame, ControlFlow.OUTLINE_COLOR));
        assertTrue(boxes.size() >= 4, shown + ": boxes drawn " + boxes.size());
        for (int one = 0; one < boxes.size(); one++) {
            for (int other = one + 1; other < boxes.size(); other++) {
                for (int[] side : boxes.get(one)) {
                    for (int[] near : boxes.get(other)) {
                        // Grown a pixel each way, a border touching the other proves no clear pixel between them.
                        assertFalse(overlaps(side, new int[] {near[0] - 1, near[1] - 1, near[2] + 1, near[3] + 1}),
                                shown + ": the border " + java.util.Arrays.toString(side) + " has no clear pixel to "
                                        + java.util.Arrays.toString(near));
                    }
                }
            }
        }
        for (int[] button : fills(frame, ButtonPainter.BORDER)) {
            for (List<int[]> box : boxes) {
                for (int[] side : box) {
                    assertFalse(overlaps(side, button), shown + ": the border " + java.util.Arrays.toString(side)
                            + " touches the button " + java.util.Arrays.toString(button));
                }
            }
        }
    }

    // Whether two rectangles, as left, top, right, bottom with the right and bottom outside, share a pixel.
    private static boolean overlaps(int[] one, int[] other) {
        return one[0] < other[2] && other[0] < one[2] && one[1] < other[3] && other[1] < one[3];
    }

    /**
     * A held term drag shadows the screen and lights a hole over each slot canMoveTo accepts and over the trash
     * strip canRemove opens, and none over the term's own slots, which it refuses (decision
     * drags-mask-everything-but-their-valid-targets).
     */
    @Test
    void heldTermDragMasksAllButTheSlotsItTakes() {
        String shown = "$waxed + $edited + $base";
        Builder builder = Builder.item(shown);
        DrawnFrame frame = builder.frame();
        double[] waxed = railOf(frame, List.of(0));
        double[] edited = railOf(frame, List.of(1));
        assertTrue(frame.targets.click(waxed[0], waxed[1]));
        assertTrue(frame.targets.drag(AREA.left() + 1, waxed[1]));
        DrawnFrame held = builder.frame();
        assertFalse(fills(held, DragMask.SHADOW).isEmpty(), "the shadow covers the screen");
        ExpressionBuilder model = ExpressionBuilder.of(shown);
        long accepted = IntStream.rangeClosed(0, 3).filter(slot -> model.canMoveTo(List.of(0), List.of(), slot))
                .count();
        List<int[]> lit = fills(held, DragMask.HIGHLIGHT);
        assertEquals(accepted + 1, lit.size(), "a hole per slot taken and the strip");
        assertFalse(lit.stream().anyMatch(hole -> hole[0] <= waxed[0] && waxed[0] < hole[2]
                || hole[0] <= edited[0] && edited[0] < hole[2]), "no hole over the term's own slots");
        frame.targets.release();
    }

    /**
     * A held () handle lights each term it can wrap (decision drags-mask-everything-but-their-valid-targets).
     */
    @Test
    void heldHandleLightsEveryTermItWraps() {
        Builder builder = Builder.item("$waxed + $edited");
        DrawnFrame frame = builder.frame();
        DrawnFrame.Text handle = frame.find("()");
        assertTrue(frame.targets.click(handle.x(), handle.y()));
        assertTrue(frame.targets.drag(AREA.left() + 1, handle.y()));
        assertEquals(2, fills(builder.frame(), DragMask.HIGHLIGHT).size(), "a highlight per term");
        frame.targets.release();
    }

    /**
     * A term's rail and a type row's rail hover nothing while the term beside the rail hovers its preview, and the
     * trash strip hovers its hint (decisions drag-rails-hover-nothing, trash-strip-hovers-drag-items-here-to-remove).
     */
    @Test
    void railsHoverNothingAndTheTrashStripHoversItsHint() {
        DrawnFrame frame = Builder.item("$waxed + $edited").frame();
        double[] rail = railOf(frame, List.of(1));
        assertEquals(java.util.Optional.empty(), frame.targets.hovered(rail[0], rail[1]), "the rail hovers nothing");
        DrawnFrame.Text edited = frame.find("edited");
        assertTrue(frame.targets.hovered(edited.x() + 1, edited.y() + 1).isPresent(), "the term hovers its preview");
        int[] strip = fills(frame, ExpressionBuilderPane.TRASH_REST).getFirst();
        String hint = frame.targets.hovered((strip[0] + strip[2]) / 2.0, (strip[1] + strip[3]) / 2.0)
                .flatMap(BuilderOverlays::tooltipFor).map(component -> component.getString()).orElse("");
        assertEquals("Drag items here to remove", hint);
        DrawnFrame typed = Builder.item(TWO_TYPES).frame();
        double[] rowRail = rowRailOf(typed, 0);
        assertEquals(java.util.Optional.empty(), typed.targets.hovered(rowRail[0], rowRail[1]),
                "the row's rail hovers nothing");
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
     * A term's, a parenthetical's and a type row's rail fills its box's left edge, flush against the inside of the
     * border from top to bottom, with three one-pixel grip lines inside it (decision rails-fill-the-left-edge-of-
     * their-box).
     */
    @Test
    void railsFillTheLeftEdgeOfTheirBoxes() {
        DrawnFrame frame = Builder.item("$base + $waxed + ($waxed - $edited)").frame();
        assertRailFillsItsBox(frame, railOf(frame, List.of(1)), ControlFlow.OUTLINE_COLOR);
        assertRailFillsItsBox(frame, railOf(frame, List.of(2)), ExpressionBuilderPane.NEST_BORDER);
        DrawnFrame typed = Builder.item(TWO_TYPES).frame();
        assertRailFillsItsBox(typed, rowRailOf(typed, 0), ControlFlow.OUTLINE_COLOR);
    }

    // The rail answering at a point stands flush inside the border of the given color on its left, top and bottom.
    private static void assertRailFillsItsBox(DrawnFrame frame, double[] point, int borderColor) {
        int[] rail = fills(frame, ControlFlow.RAIL_COLOR).stream()
                .filter(fill -> fill[0] == (int) point[0] && fill[1] <= point[1] + 1 && fill[3] > point[1])
                .findFirst().orElseThrow(() -> new AssertionError("no rail fill at " + point[0] + "," + point[1]));
        List<int[]> grips = fills(frame, ControlFlow.GRIP_COLOR).stream()
                .filter(fill -> fill[0] >= rail[0] && fill[2] <= rail[2] && fill[1] >= rail[1] && fill[3] <= rail[3])
                .toList();
        assertEquals(3, grips.size(), "grip lines inside the rail");
        grips.forEach(grip -> assertEquals(1, grip[3] - grip[1], "a grip line is one pixel tall"));
        List<int[]> border = fills(frame, borderColor);
        assertTrue(border.stream().anyMatch(side -> side[2] == rail[0] && side[0] == rail[0] - 1
                && side[1] <= rail[1] - 1 && side[3] >= rail[3] + 1), "no border flush left of "
                + java.util.Arrays.toString(rail));
        assertTrue(border.stream().anyMatch(side -> side[1] == rail[1] - 1 && side[3] == rail[1]
                && side[0] <= rail[0] && side[2] >= rail[2]), "no border flush above " + java.util.Arrays.toString(rail));
        assertTrue(border.stream().anyMatch(side -> side[1] == rail[3] && side[3] == rail[3] + 1
                && side[0] <= rail[0] && side[2] >= rail[2]), "no border flush below " + java.util.Arrays.toString(rail));
    }

    /**
     * A parenthetical's box draws a one-pixel border in its own color around its fill, and sibling term boxes stand
     * the term margin apart; the box padding and the margin both read wider than before (decision
     * parentheticals-alternate-gray-tones-by-depth).
     */
    @Test
    void parentheticalBoxesDrawABorderAndTermBoxesTakeRoom() {
        assertTrue(ExpressionBuilderPane.BOX_PADDING > 2, "the padding was 2 before");
        assertTrue(ExpressionBuilderPane.TERM_MARGIN > 4, "the margin was 4 before");
        DrawnFrame frame = Builder.item("$waxed + $edited + ($base - $waxed)").frame();
        int[] fill = fills(frame, ExpressionBuilderPane.nestTone(1)).getFirst();
        List<int[]> border = fills(frame, ExpressionBuilderPane.NEST_BORDER);
        assertEquals(4, border.size(), "the border's four sides");
        int borderLeft = border.stream().mapToInt(side -> side[0]).min().orElseThrow();
        int borderTop = border.stream().mapToInt(side -> side[1]).min().orElseThrow();
        int borderRight = border.stream().mapToInt(side -> side[2]).max().orElseThrow();
        int borderBottom = border.stream().mapToInt(side -> side[3]).max().orElseThrow();
        assertTrue(borderLeft < fill[0] && borderTop < fill[1] && borderRight > fill[2] && borderBottom > fill[3],
                "the border encloses the fill " + java.util.Arrays.toString(fill));
        double[] waxed = railOf(frame, List.of(0));
        double[] edited = railOf(frame, List.of(1));
        // The vertical outline sides on the line, left to right: waxed's left and right, then edited's.
        List<int[]> sides = fills(frame, ControlFlow.OUTLINE_COLOR).stream()
                .filter(side -> side[2] - side[0] == 1 && side[1] <= waxed[1] && side[3] > waxed[1])
                .sorted(java.util.Comparator.comparingInt(side -> side[0])).toList();
        assertTrue(sides.size() >= 3 && sides.get(2)[0] < edited[0], "outline sides " + sides.size());
        int waxedRight = sides.get(1)[2];
        int editedLeft = sides.get(2)[0];
        assertEquals(ExpressionBuilderPane.TERM_MARGIN, editedLeft - waxedRight, "the room between sibling boxes");
    }

    /**
     * The builder's gray box stands the box margin outside its first button, left and above (decision
     * spacing-rules-are-one-set-across-every-pane).
     */
    @Test
    void builderBoxKeepsTheBoxMarginOutsideItsContents() {
        DrawnFrame frame = Builder.item("$waxed").frame();
        int[] panel = fills(frame, ExpressionBuilderPane.PANEL_COLOR).getFirst();
        DrawnFrame.Text kind = frame.drawn().getFirst();
        int buttonLeft = kind.x() - ButtonPainter.PADDING;
        int buttonTop = kind.y() - ButtonPainter.TEXT_NUDGE;
        assertEquals(RowLayout.BOX_MARGIN, buttonLeft - panel[0], "the box's left margin");
        assertEquals(RowLayout.BOX_MARGIN, buttonTop - panel[1], "the box's top margin");
    }

    /**
     * A drag shows a drop cursor only at a slot the builder takes: none at the term's own slot, none at a slot that
     * would empty a type row (decision drags-indicate-only-a-drop-the-builder-takes).
     */
    @Test
    void dropCursorDrawsOnlyAtASlotTheBuilderTakes() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        double[] rail = railOf(frame, List.of(1));
        assertTrue(frame.targets.click(rail[0], rail[1]));
        assertTrue(frame.targets.drag(rail[0], rail[1]));
        assertTrue(cursorTops(builder.frame()).isEmpty(), "a cursor at the term's own slot");
        frame.targets.release();
        Builder rows = Builder.item(TWO_TYPES);
        DrawnFrame typed = rows.frame();
        double[] only = railOf(typed, List.of(0, 0));
        assertTrue(typed.targets.click(only[0], only[1]));
        double[] other = railOf(typed, List.of(1, 0));
        assertTrue(typed.targets.drag(other[0], other[1]));
        assertTrue(cursorTops(rows.frame()).isEmpty(), "a cursor where the row would empty");
        typed.targets.release();
        assertEquals(List.of(), rows.written);
    }

    /**
     * The trash strip rests through a drag of a thing the builder will not delete, over the strip and off it: a type
     * row's only term, and the only type row (decision drags-indicate-only-a-drop-the-builder-takes).
     */
    @Test
    void trashStripRestsForAThingTheBuilderWillNotDelete() {
        Builder term = Builder.item("{\"metal\": \"5\"}");
        DrawnFrame frame = term.frame();
        double[] rail = railOf(frame, List.of(0, 0));
        assertTrue(frame.targets.click(rail[0], rail[1]));
        assertTrue(frame.targets.drag(AREA.left() + 1, rail[1]));
        assertRests(term.frame());
        assertTrue(frame.targets.drag(AREA.right() - 1, rail[1]));
        assertRests(term.frame());
        frame.targets.release();
        Builder row = Builder.item("{\"metal\": \"5\"}");
        DrawnFrame rowFrame = row.frame();
        double[] rowRail = rowRailOf(rowFrame, 0);
        assertTrue(rowFrame.targets.click(rowRail[0], rowRail[1]));
        assertTrue(rowFrame.targets.drag(AREA.right() - 1, rowRail[1]));
        assertRests(row.frame());
        rowFrame.targets.release();
        assertEquals(List.of(), term.written);
        assertEquals(List.of(), row.written);
    }

    /**
     * The trash strip draws a one-pixel lighter red border along its four edges and a can inside it, at rest and while
     * lit (decision trash-strip-draws-a-can-and-a-lighter-red-border).
     */
    @Test
    void trashStripDrawsACanInsideALighterRedBorder() {
        assertTrue((ExpressionBuilderPane.TRASH_BORDER >> 8 & 0xFF) > (ExpressionBuilderPane.TRASH_REST >> 8 & 0xFF),
                "the border reads lighter than the fill");
        Builder builder = Builder.item("$waxed + $edited");
        DrawnFrame rest = builder.frame();
        assertBorderAndCan(rest, fills(rest, ExpressionBuilderPane.TRASH_REST).getFirst());
        double[] rail = railOf(rest, List.of(1));
        assertTrue(rest.targets.click(rail[0], rail[1]));
        assertTrue(rest.targets.drag(AREA.left() + 1, rail[1]));
        DrawnFrame lit = builder.frame();
        assertBorderAndCan(lit, fills(lit, ExpressionBuilderPane.TRASH_LIT).getFirst());
        rest.targets.release();
    }

    private static void assertBorderAndCan(DrawnFrame frame, int[] strip) {
        List<int[]> border = fills(frame, ExpressionBuilderPane.TRASH_BORDER);
        assertTrue(border.stream().anyMatch(side -> side[0] == strip[0] && side[2] == strip[0] + 1), "left edge");
        assertTrue(border.stream().anyMatch(side -> side[2] == strip[2] && side[0] == strip[2] - 1), "right edge");
        assertTrue(border.stream().anyMatch(side -> side[1] == strip[1] && side[3] == strip[1] + 1), "top edge");
        assertTrue(border.stream().anyMatch(side -> side[3] == strip[3] && side[1] == strip[3] - 1), "bottom edge");
        List<int[]> can = fills(frame, ExpressionBuilderPane.TRASH_CAN);
        assertFalse(can.isEmpty(), "no can drawn");
        can.forEach(piece -> assertTrue(piece[0] > strip[0] && piece[2] < strip[2] && piece[1] > strip[1]
                && piece[3] < strip[3], "a can piece outside the strip: " + java.util.Arrays.toString(piece)));
    }

    /**
     * A prefab times a number draws as the prefab's button, its member's icon and a multiplier box reading the
     * number, and the prefab button opens the prefab list (decision operand-times-number-terms-edit-like-every-term).
     */
    @Test
    void operandTimesNumberDrawsItsButtonsAndOpensTheirLists() {
        Builder builder = Builder.item("$food.jelly + $saturation.jelly * 12");
        DrawnFrame frame = builder.frame();
        DrawnFrame.Text saturation = frame.find("saturation");
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("12") && text.y() == saturation.y()
                && text.x() > saturation.x()), frame.drawn().toString());
        assertTrue(iconBlits(frame).stream().anyMatch(blit -> blit[0] > saturation.x()
                && Math.abs(blit[1] - saturation.y()) < ControlFlow.LINE), "no member icon after saturation");
        clickAt(frame, saturation);
        assertTrue(builder.pane.listOpen(), "the prefab list opens");
    }

    /**
     * Each type row's goo type button stands at least the button gap below the row above it, over two and three
     * types (decision type-rows-stand-apart-in-the-builder).
     */
    @Test
    void typeRowsStandAButtonGapApart() {
        for (String value : List.of(TWO_TYPES, "{\"metal\": \"5\", \"pulse\": \"2\", \"glow\": \"1\"}")) {
            DrawnFrame frame = Builder.item(value).frame();
            int typeLeft = AREA.left() + ControlFlow.INDENT + ROW_RAIL;
            List<int[]> typeBoxes = fills(frame, ButtonPainter.BORDER).stream()
                    .filter(box -> box[0] == typeLeft)
                    .sorted(java.util.Comparator.comparingInt(side -> side[1])).toList();
            assertTrue(typeBoxes.size() >= 2, value + ": " + typeBoxes.size() + " type buttons");
            for (int index = 1; index < typeBoxes.size(); index++) {
                int gap = typeBoxes.get(index)[1] - typeBoxes.get(index - 1)[3];
                assertTrue(gap >= RowLayout.BUTTON_GAP, value + ": row " + index + " stands " + gap + " below");
            }
        }
    }

    /**
     * A type row's first term follows its colon and each later term starts a line one indent under the type; a
     * parenthetical's first term follows its "(" and each later one starts a line one indent in from its rail, its
     * ")" on a line of its own at that indent (decision each-subsequent-term-starts-a-new-line).
     */
    @Test
    void eachSubsequentTermStartsANewLine() {
        DrawnFrame row = Builder.item("{\"jelly\": \"$food.jelly + $saturation.jelly * 12\"}").frame();
        double[] first = railOf(row, List.of(0, 0));
        double[] second = railOf(row, List.of(0, 1));
        double[] rowRail = rowRailOf(row, 0);
        assertEquals((int) rowRail[1] / ControlFlow.LINE, (int) first[1] / ControlFlow.LINE, "the first term");
        assertTrue(second[1] >= first[1] + ControlFlow.LINE, "the second term shares the first's line");
        assertEquals((int) rowRail[0] + ControlFlow.INDENT, (int) second[0], "the second term's indent");
        DrawnFrame nested = Builder.item("$dye.leaf - ($food.jelly + $saturation.jelly * 12)").frame();
        double[] parenthetical = railOf(nested, List.of(1));
        double[] inner = railOf(nested, List.of(1, 0));
        double[] innerSecond = railOf(nested, List.of(1, 1));
        assertEquals((int) parenthetical[1] / ControlFlow.LINE, (int) inner[1] / ControlFlow.LINE, "after (");
        assertTrue(innerSecond[1] >= inner[1] + ControlFlow.LINE, "the inner second term shares a line");
        assertEquals((int) parenthetical[0] + ControlFlow.INDENT, (int) innerSecond[0], "the inner indent");
        DrawnFrame.Text close = nested.find(")");
        assertTrue(close.y() > innerSecond[1], ") shares the last term's line");
        assertEquals((int) parenthetical[0] + ControlFlow.INDENT, close.x(), ") stands at the inner indent");
    }

    /**
     * A per-type prefab's 's draws flush against its word's button, no gap between (decision
     * possessive-sits-flush-against-its-word-button).
     */
    @Test
    void possessiveSitsFlushAgainstItsWordButton() {
        DrawnFrame frame = Builder.item("{\"jelly\": \"$food.jelly\"}").frame();
        DrawnFrame.Text food = frame.find("food");
        DrawnFrame.Text possessive = frame.find("'s");
        assertEquals(food.x() + DrawnFrame.font().width(food.text()) + ButtonPainter.PADDING, possessive.x(),
                frame.drawn().toString());
    }

    private static void assertRests(DrawnFrame frame) {
        assertEquals(1, fills(frame, ExpressionBuilderPane.TRASH_REST).size(), "the strip rests");
        assertEquals(List.of(), fills(frame, ExpressionBuilderPane.TRASH_LIT), "the strip lit");
        assertEquals(List.of(), fills(frame, ExpressionBuilderPane.TRASH_HOVER), "the strip brightened");
    }

    private static final String TWO_TYPES = "{\"metal\": \"5\", \"pulse\": \"2\"}";

    /**
     * The [+?] button's border, which opens the line [+?] () stands on, draws at the first type row's box left, in
     * the one column the rows share (decision plus-question-and-equals-align-with-the-other-lines).
     */
    @Test
    void plusQuestionStandsAtTheTypeRowsLeft() {
        DrawnFrame frame = Builder.item(TWO_TYPES).frame();
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), anyInt(), anyInt(), anyInt(),
                eq(ControlFlow.OUTLINE_COLOR));
        // A row's box outlines after the term boxes inside it, so the leftmost outline is the rows' box left.
        int rowLeft = lefts.getAllValues().stream().mapToInt(Integer::intValue).min().orElseThrow();
        assertEquals(rowLeft, hoverCorner(frame, BuilderOverlays.hintKey("Add a goo type"))[0],
                "the +? border at the row box's left " + rowLeft);
    }

    // The top-left corner of the area hovering a key, found by its hover across the screen.
    private static int[] hoverCorner(DrawnFrame frame, String key) {
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                    return new int[] {x, y};
                }
            }
        }
        throw new AssertionError("nothing hovers " + key + " in " + frame.targets.hoverKeys());
    }

    // Where a type row's rail answers, found by its hover across the screen.
    private static double[] rowRailOf(DrawnFrame frame, int row) {
        String key = ExpressionBuilderPane.rowRailName(row);
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                if (frame.targets.grabNamed(x, y).filter(key::equals).isPresent()) {
                    return new double[] {x, y};
                }
            }
        }
        throw new AssertionError("no rail for row " + row);
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
     * reads "(" and its first term on one line, its second term on the line below and ")" below that (decisions
     * sum-of-terms-leaves-the-builder, each-subsequent-term-starts-a-new-line).
     */
    @Test
    void parentheticalReadsItsFirstTermAfterItsParenAndEachLaterTermBelow() {
        DrawnFrame frame = Builder.item("$dye.leaf - ($food.jelly + $saturation.jelly * 12)").frame();
        assertFalse(frame.texts().contains("+ sum of terms") || frame.texts().contains("the sum of:"),
                frame.texts().toString());
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        DrawnFrame.Text food = frame.find("food");
        DrawnFrame.Text saturation = frame.find("saturation");
        assertEquals(open.y(), food.y(), frame.drawn().toString());
        assertTrue(open.x() < food.x() && saturation.y() > food.y() && close.y() > saturation.y(),
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

        // The texts drawn on one builder line, left to right, the kind's line first.
        private static List<DrawnFrame.Text> line(DrawnFrame frame, int index) {
            return frame.drawn().stream().filter(text -> lineOf(frame, text.y()) == index)
                    .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).toList();
        }

        // Which builder line a y stands on, the lines read from the texts drawn on them, since a line's height grows
        // per box depth (decision drag-rail-containers-keep-a-pixel-between-borders).
        private static int lineOf(DrawnFrame frame, int y) {
            List<Integer> tops = frame.drawn().stream().map(text -> text.y() - ControlFlow.textTop(0)).distinct()
                    .sorted().toList();
            return (int) tops.stream().filter(top -> top <= y).count() - 1;
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
                if (lineOf(frame, ys.getAllValues().get(at)) == index) {
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
            // Decision type-icon-buttons-shrink-their-padding: the type's button is its icon plus a pixel each side.
            assertEquals(ControlFlow.INDENT + ROW_RAIL + 1, typeX);
            assertEquals(typeX + GooTypeIcons.sizeOf("jelly") + 1 + BuilderOverlays.GAP, colon.x(),
                    colon + " a gap past the type's button at " + typeX);
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
            assertEquals(typeLeft + ControlFlow.INDENT + BOX_ROOM, (int) saturationRail[0], frame.drawn().toString());
            assertEquals(lineOf(frame, saturation.y()), lineOf(frame, (int) saturationRail[1]));
            DrawnFrame.Text dye = frame.find("dye");
            DrawnFrame.Text minus = frame.drawn().stream().filter(text -> text.text().equals("-")
                    && text.y() > dye.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            double[] parentheticalRail = railOf(frame, List.of(1, 1));
            assertEquals(typeLeft + ControlFlow.INDENT + BOX_ROOM, (int) parentheticalRail[0],
                    frame.drawn().toString());
            assertEquals(lineOf(frame, minus.y()), lineOf(frame, (int) parentheticalRail[1]));
            assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("(") && text.y() == minus.y()),
                    frame.drawn().toString());
            // A wrap inside the parenthetical opens one indent further in than the parenthetical's line.
            int innerLine = lineOf(frame, minus.y()) + 1;
            int parentheticalLeft = (int) parentheticalRail[0] - BOX_ROOM;
            assertTrue(line(frame, innerLine).getFirst().x() >= parentheticalLeft + ControlFlow.INDENT,
                    frame.drawn().toString());
        }

        @Test
        void beetrootsJellyRowReadsKindPlusThenTheRowInline() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            assertEquals(List.of(dropdown("by type")), lineTexts(frame, 0), frame.drawn().toString());
            // Decision operand-times-number-terms-edit-like-every-term: saturation's 12 reads in its ratio box.
            // Decision each-subsequent-term-starts-a-new-line: saturation starts the row's next line.
            assertEquals(List.of(":", "food", "'s", "1", ":", "1"), lineTexts(frame, 1), frame.drawn().toString());
            assertEquals(List.of("+", "saturation", "'s", "12", ":", "1"), lineTexts(frame, 2),
                    frame.drawn().toString());
            assertEquals(ControlFlow.INDENT + ROW_RAIL + ButtonPainter.ICON_PADDING, iconXs(frame, 1).getFirst());
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
            int head = lineOf(frame, open.y());
            assertEquals(List.of("-", "(", "food"), lineTexts(frame, head).subList(0, 3), frame.drawn().toString());
            assertFalse(lineTexts(frame, head).contains("X"), frame.drawn().toString());
            assertEquals(List.of(":", "dye", "'s", "1", ":", "1"), lineTexts(frame, head - 1),
                    frame.drawn().toString());
            // Decision each-subsequent-term-starts-a-new-line: ")" stands below the parenthetical's last term.
            DrawnFrame.Text last = frame.drawn().stream().filter(text -> text.text().equals("saturation")
                    && text.y() > open.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            assertTrue(frame.find(")").y() > last.y(), frame.drawn().toString());
        }

        /**
         * The control bay draws a + and a - button beside the () handle, no sum draws a +/- control, and the - dragged
         * onto the slot before a term draws the drop cursor there and, dropped, inserts a subtracted term at that
         * slot, its term picked from the list the dropdown offered (decision
         * plus-and-minus-are-draggable-buttons-in-the-control-bay).
         */
        @Test
        void bayMinusDraggedOntoASlotInsertsASubtractedTermThere() {
            Builder builder = Builder.prefab(PREFABS, "$waxed + $edited");
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text handle = frame.find("()");
            List<DrawnFrame.Text> bay = frame.drawn().stream().filter(text -> text.y() == handle.y())
                    .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).toList();
            assertEquals(List.of("()", "+", "-"), bay.stream().map(DrawnFrame.Text::text).toList(), bay.toString());
            assertEquals(0, frame.count(SUM_ADD), frame.texts().toString());
            DrawnFrame.Text minus = bay.getLast();
            assertTrue(frame.targets.click(minus.x(), minus.y()), "the - grabs the pointer");
            double[] edited = railOf(frame, List.of(1));
            assertTrue(frame.targets.drag(edited[0], edited[1]));
            assertTrue(cursorTops(builder.frame()).size() == 1, "the drop cursor stands at the slot");
            frame.targets.release();
            builder.frame().click("$waxed");
            assertEquals("$waxed - $waxed + $edited", builder.written.getLast());
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
     * No builder control explains itself on hover but five: the () handle, [+?], the ratio's two boxes and the trash
     * strip (the operator's ruling, amended by decisions parenthetical-handle-carries-a-rail-and-hovers-its-drop,
     * plus-question-and-ratio-boxes-hover-their-names and trash-strip-hovers-drag-items-here-to-remove).
     */
    @Test
    void onlyTheFiveNamedControlsCarryATooltip() {
        java.util.Set<String> named = java.util.stream.Stream.of(HANDLE_HINT, ADD_TYPE_HINT, "Multiplier", "Divisor",
                        "Drag items here to remove")
                .map(BuilderOverlays::hintKey).collect(java.util.stream.Collectors.toSet());
        for (String shown : List.of("$base + ($waxed - $edited)", "{\"leaf\": \"4\"}", "")) {
            Builder builder = Builder.item(shown);
            List<String> hints = builder.frame().targets.hoverKeys().stream()
                    .filter(key -> BuilderOverlays.tooltipFor(key).isPresent()).distinct().toList();
            assertTrue(named.containsAll(hints), shown + ": " + hints);
        }
    }

    private static final String HANDLE_HINT = "Drop on term, wrap w/ parenthesis";
    private static final String ADD_TYPE_HINT = "Add a goo type";

    /**
     * [+?] hovers "Add a goo type", and a term's ratio hovers "Multiplier" on its left box and "Divisor" on its right
     * (decision plus-question-and-ratio-boxes-hover-their-names).
     */
    @Test
    void plusQuestionAndRatioBoxesHoverTheirNames() {
        DrawnFrame typed = Builder.item("{\"leaf\": \"4\"}").frame();
        DrawnFrame.Text add = typed.find("+?");
        assertEquals(BuilderOverlays.hintKey(ADD_TYPE_HINT), typed.targets.hovered(add.x() + 1, add.y() + 1)
                .orElseThrow());
        DrawnFrame frame = Builder.item("$waxed").frame();
        List<DrawnFrame.Text> sides = ratioSides(frame);
        assertEquals(BuilderOverlays.hintKey("Multiplier"),
                frame.targets.hovered(sides.get(0).x() + 1, sides.get(0).y() + 1).orElseThrow());
        assertEquals(BuilderOverlays.hintKey("Divisor"),
                frame.targets.hovered(sides.get(1).x() + 1, sides.get(1).y() + 1).orElseThrow());
    }

    /**
     * The () handle draws a rail with its grips flush inside its box's left border, a press on the rail starts the
     * wrap drag, and hovering the handle reads how it is used (decision
     * parenthetical-handle-carries-a-rail-and-hovers-its-drop).
     */
    @Test
    void handleCarriesARailAndHoversItsDrop() {
        Builder builder = Builder.item("$waxed + $edited");
        DrawnFrame frame = builder.frame();
        DrawnFrame.Text handle = handles(frame).getFirst();
        int[] rail = fills(frame, ControlFlow.RAIL_COLOR).stream()
                .filter(fill -> fill[2] <= handle.x() && fill[2] > handle.x() - ControlFlow.LINE
                        && fill[1] <= handle.y() && fill[3] > handle.y())
                .findFirst().orElseThrow(() -> new AssertionError("no rail left of the handle " + handle));
        assertEquals(3, fills(frame, ControlFlow.GRIP_COLOR).stream().filter(grip -> grip[0] >= rail[0]
                && grip[2] <= rail[2] && grip[1] >= rail[1] && grip[3] <= rail[3]).count(), "grips in the rail");
        assertTrue(fills(frame, ButtonPainter.BORDER).stream().anyMatch(box -> box[0] == rail[0] - 1
                && box[1] == rail[1] - 1 && box[3] == rail[3] + 1 && box[2] > handle.x()), "the rail sits flush");
        assertEquals(BuilderOverlays.hintKey(HANDLE_HINT),
                frame.targets.hovered(handle.x() + 1, handle.y() + 1).orElseThrow());
        assertTrue(frame.targets.click(rail[0] + 1, rail[1] + 1));
        DrawnFrame.Text edited = frame.find("edited");
        assertTrue(frame.targets.drag(edited.x() + 1, edited.y() + 1));
        assertTrue(filledAround(builder.frame(), ExpressionBuilderPane.WRAP_MARK, edited), "the rail starts no wrap");
        frame.targets.release();
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
