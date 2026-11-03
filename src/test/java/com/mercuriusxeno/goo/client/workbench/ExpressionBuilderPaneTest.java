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
    // The builder's lettering on the mocked font, and how far below their line's top its words stand
    // (decision builder-font-and-spacing-shrink-padding-untouched).
    private static final Lettering LETTERS = new Lettering(DrawnFrame.font(), ControlFlow.BUILDER_SCALE);
    private static final int WORDS_DROP = LETTERS.centeredTop(0, ControlFlow.LINE);
    // Texts whose tops stand closer than this share a builder line.
    private static final int HALF_LINE = ControlFlow.LINE / 2;
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
        DrawnFrame.Text base = frame.find("$base");
        assertTrue(frame.targets.drag(AREA.right() - 40, base.y()));
        DrawnFrame dragging = builder.frame();
        List<Integer> cursor = cursorTops(dragging);
        assertEquals(1, cursor.size(), "one drop cursor: " + cursor);
        assertTrue(Math.abs(cursor.getFirst() - base.y()) < ControlFlow.LINE, cursor + " vs " + base);
        assertTrue(dragging.texts().stream().filter("$waxed"::equals).count() > 1, "the term draws at the mouse");
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
        assertTrue(found.getFirst().y() > frame.find("$edited").y(), frame.drawn().toString());
        assertTrue(found.getFirst().y() > frame.find("$waxed").y(), frame.drawn().toString());
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
        DrawnFrame.Text edited = frame.find("$edited");
        assertTrue(frame.targets.drag(edited.x() + 1, edited.y() + 1));
        DrawnFrame dragging = builder.frame();
        assertTrue(filledAround(dragging, ExpressionBuilderPane.WRAP_MARK, dragging.find("$edited")),
                "the term under the pointer is not marked");
        assertFalse(filledAround(dragging, ExpressionBuilderPane.WRAP_MARK, dragging.find("$waxed")),
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
        DrawnFrame.Text edited = again.find("$edited");
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
        DrawnFrame.Text edited = frame.find("$edited");
        DrawnFrame.Text base = frame.find("$base");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(1), edited), "no depth-one box around edited");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(1), base), "the depth-one box holds base");
        assertTrue(filledAround(frame, ExpressionBuilderPane.nestTone(2), base), "no depth-two box around base");
        assertFalse(filledAround(frame, ExpressionBuilderPane.nestTone(2), edited), "the depth-two box spans edited");
    }

    /**
     * Every box sizes to its own contents, its border one clear pixel above its topmost and below its bottommost
     * button or inner box, however deep a box elsewhere in the value nests, so a shallow box stands as tight as the
     * innermost one (decision drag-rail-containers-keep-a-pixel-between-borders).
     */
    @Test
    void everyBoxHugsItsContentsWithOneClearPixel() {
        for (String shown : List.of("$waxed + ($edited + ($base + $waxed))", "$waxed + (($base + $waxed) + $edited)",
                "$waxed + (($base + $waxed))", "{\"metal\": \"$base + ($waxed + ($edited))\", \"pulse\": \"2\"}")) {
            DrawnFrame frame = Builder.item(shown).frame();
            List<int[]> boxes = new ArrayList<>(boxesIn(frame, ExpressionBuilderPane.NEST_BORDER).stream()
                    .map(ExpressionBuilderPaneTest::boundsOf).toList());
            boxes.addAll(boxesIn(frame, ControlFlow.OUTLINE_COLOR).stream().map(ExpressionBuilderPaneTest::boundsOf)
                    .toList());
            List<int[]> pieces = new ArrayList<>(fills(frame, ButtonPainter.BORDER));
            pieces.addAll(boxes);
            List<String> loose = new ArrayList<>();
            for (int[] box : boxes) {
                List<int[]> inside = pieces.stream().filter(piece -> piece != box && piece[0] > box[0]
                        && piece[2] < box[2] && piece[1] > box[1] && piece[3] < box[3]).toList();
                if (inside.isEmpty()) {
                    continue;
                }
                int topGap = inside.stream().mapToInt(piece -> piece[1]).min().orElseThrow() - box[1];
                int bottomGap = box[3] - inside.stream().mapToInt(piece -> piece[3]).max().orElseThrow();
                if (topGap != 2 || bottomGap != 2) {
                    loose.add(java.util.Arrays.toString(box) + " top " + topGap + " bottom " + bottomGap);
                }
            }
            assertEquals(List.of(), loose, shown + ": boxes padded past one clear pixel");
        }
    }

    // A box's left, top, right and bottom from its four sides, top, bottom, left and right in the order drawn.
    private static int[] boundsOf(List<int[]> sides) {
        return new int[] {sides.get(2)[0], sides.get(0)[1], sides.get(3)[2], sides.get(1)[3]};
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
        assertTrue(onOneLine(open, frame.find("$base")), "the inner parenthetical's terms share the outer's ( line");
        assertTrue(inner[0] > outer[0], "the inner rail stands right of the outer one");
    }

    // Where a later term's segmented container opens: its join's segment, whose right border the rail's segment shares
    // a pixel left of the rail (decision containers-segment-into-click-to-edit-buttons).
    private static int containerLeft(DrawnFrame frame, double[] rail) {
        return frame.buttonAround((int) rail[0] - 2, (int) rail[1])[0];
    }

    // Whether two texts stand on one builder line, their tops within half a line.
    private static boolean onOneLine(DrawnFrame.Text one, DrawnFrame.Text other) {
        return Math.abs(one.y() - other.y()) < HALF_LINE;
    }

    // Each box's four sides, grouped as each box draws them, in the given border color.
    private static List<List<int[]>> boxesIn(DrawnFrame frame, int color) {
        List<int[]> sides = fills(frame, color);
        assertEquals(0, sides.size() % 4, "four sides a box");
        return IntStream.range(0, sides.size() / 4).mapToObj(box -> sides.subList(box * 4, box * 4 + 4)).toList();
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
     * A held term's lit drop places stay inside the area the builder draws visible, so a place scrolled partly under
     * the pane's edge, as under the search bar, lights only its visible part (decision
     * drags-mask-everything-but-their-valid-targets).
     */
    @Test
    void heldTermLightsOnlyTheVisiblePartOfEachDropPlace() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        double[] waxed = railOf(frame, List.of(0));
        assertTrue(frame.targets.click(waxed[0], waxed[1]));
        assertTrue(frame.targets.drag(AREA.left() + 1, waxed[1]));
        int visibleTop = (int) waxed[1] + 4;
        DrawnFrame clipped = new DrawnFrame();
        when(clipped.graphics.peekScissorStack()).thenReturn(new net.minecraft.client.gui.navigation.ScreenRectangle(
                0, visibleTop, DrawnFrame.SCREEN_WIDTH, DrawnFrame.SCREEN_HEIGHT - visibleTop));
        builder.pane.extract(clipped.graphics, builder.font, AREA, 0, clipped.targets);
        builder.pane.drawOverlays(clipped.graphics, builder.font, clipped.targets);
        List<int[]> lit = fills(clipped, DragMask.HIGHLIGHT);
        assertFalse(lit.isEmpty(), "the drop places light");
        assertEquals(List.of(), lit.stream().filter(hole -> hole[1] < visibleTop).map(java.util.Arrays::toString)
                .toList(), "lit above the visible top " + visibleTop);
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
        DrawnFrame.Text edited = frame.find("$edited");
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
     * A parenthetical's and a type row's rail fills its box's left edge, flush against the inside of the border from
     * top to bottom, with three one-pixel grip lines inside it (decision rails-fill-the-left-edge-of-their-box); a
     * plain term's rail fills its own segment, flush inside the segment's border (decision
     * containers-segment-into-click-to-edit-buttons).
     */
    @Test
    void railsFillTheLeftEdgeOfTheirBoxes() {
        DrawnFrame frame = Builder.item("$base + $waxed + ($waxed - $edited)").frame();
        double[] term = railOf(frame, List.of(1));
        int[] rail = fills(frame, ControlFlow.RAIL_COLOR).stream().filter(fill -> fill[0] == (int) term[0]
                && fill[1] <= term[1] && fill[3] > term[1]).findFirst()
                .orElseThrow(() -> new AssertionError("no rail fill at " + term[0] + "," + term[1]));
        assertEquals(List.of(rail[0] - 1, rail[1] - 1, rail[2] + 1, rail[3] + 1),
                java.util.Arrays.stream(frame.buttonAround(rail[0], rail[1])).boxed().toList(),
                "the term's rail fills its segment inside the border");
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
     * A parenthetical's box draws a one-pixel border in its own color around its fill, and sibling terms' segmented
     * containers stand the term margin apart, the room between containers kept as the operator ruled when they were
     * segmented (decision parentheticals-alternate-gray-tones-by-depth).
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
        // Edited's container opens on its sign's segment, the second +; waxed's ends on the last segment left of it.
        DrawnFrame.Text join = frame.drawn().stream().filter(text -> text.text().equals("+")).toList().get(1);
        int editedLeft = frame.buttonAround(join)[0];
        int waxedRight = fills(frame, ButtonPainter.BORDER).stream().filter(segment -> segment[2] <= editedLeft
                && segment[1] <= join.y() && join.y() < segment[3]).mapToInt(segment -> segment[2]).max()
                .orElseThrow();
        assertEquals(ExpressionBuilderPane.TERM_MARGIN, editedLeft - waxedRight, "the room between siblings");
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
        int[] button = frame.buttonAround(kind);
        int buttonLeft = button[0];
        int buttonTop = button[1];
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
        // A type row's only term may drop nowhere, since any move would empty its row: its rail locks.
        Builder rows = Builder.item(TWO_TYPES);
        DrawnFrame typed = rows.frame();
        assertLockedRailTakesNoDrag(rows, typed, typed.find("5"));
        assertEquals(List.of(), rows.written);
    }

    // A locked rail draws dark gray left of the text its box holds, names no grab, and a press and a drag on it
    // start no drag, so the next frame lights no drop place and draws no cursor (the operator's ruling of
    // 2026-11-03: no rail offers a drag that does nothing).
    private static void assertLockedRailTakesNoDrag(Builder builder, DrawnFrame frame, DrawnFrame.Text held) {
        int[] rail = railBefore(frame, ControlFlow.LOCKED_RAIL_COLOR, held);
        int middle = (rail[1] + rail[3]) / 2;
        assertEquals(java.util.Optional.empty(), frame.targets.grabNamed(rail[0] + 1, middle), "the locked rail grabs");
        frame.targets.click(rail[0] + 1, middle);
        frame.targets.drag(AREA.right() - 1, middle);
        DrawnFrame after = builder.frame();
        assertEquals(List.of(), fills(after, DragMask.SHADOW), "a drag started from the locked rail");
        assertTrue(cursorTops(after).isEmpty(), "a cursor from the locked rail");
        assertRests(after);
        frame.targets.release();
    }

    // The nearest rail drawn in the color given left of a text, on the text's line, as left, top, right and bottom.
    private static int[] railBefore(DrawnFrame frame, int color, DrawnFrame.Text text) {
        return fills(frame, color).stream().filter(rail -> rail[2] - rail[0] == ControlFlow.RAIL_WIDTH
                && rail[2] <= text.x() && rail[1] <= text.y() && text.y() < rail[3])
                .max(java.util.Comparator.comparingInt(rail -> rail[0]))
                .orElseThrow(() -> new AssertionError("no rail before " + text));
    }

    /**
     * A thing the builder will neither delete nor move, a type row's only term and the only type row, draws its
     * rail locked, and a drag from it leaves the trash strip resting (decision
     * drags-indicate-only-a-drop-the-builder-takes, and the operator's ruling of 2026-11-03).
     */
    @Test
    void trashStripRestsForAThingTheBuilderWillNotDelete() {
        Builder term = Builder.item("{\"metal\": \"5\"}");
        DrawnFrame frame = term.frame();
        assertLockedRailTakesNoDrag(term, frame, frame.find("5"));
        Builder row = Builder.item("{\"metal\": \"5\"}");
        DrawnFrame rowFrame = row.frame();
        DrawnFrame.Text colon = rowFrame.findFirst(ExpressionLines.COLON);
        assertLockedRailTakesNoDrag(row, rowFrame, colon);
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
        DrawnFrame.Text saturation = frame.find("$saturation");
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
     * A plain term draws as one segmented container: its rail, its operand, its 's goo type, its multiplier, its
     * colon and its divisor as abutting buttons sharing their borders, each editing what it names, with no outline or
     * padding around them (decision containers-segment-into-click-to-edit-buttons).
     */
    @Nested
    class SegmentedTerms {
        private static final String TERM = "3 $waxed.metal / 2";

        // The term's segments left to right: its sign's (decision every-term-carries-its-sign-as-a-segment), its
        // rail's, its operand's, its type's, its multiplier's, its colon's and its divisor's, each as left, top, right
        // and bottom.
        private static List<int[]> segments(DrawnFrame frame) {
            DrawnFrame.Text waxed = frame.find("$waxed");
            List<int[]> rails = new ArrayList<>(fills(frame, ControlFlow.RAIL_COLOR));
            rails.addAll(fills(frame, ControlFlow.LOCKED_RAIL_COLOR));
            int[] rail = rails.stream().filter(fill -> fill[2] - fill[0] == ControlFlow.RAIL_WIDTH
                    && fill[2] <= waxed.x() && fill[1] <= waxed.y() && waxed.y() < fill[3])
                    .max(java.util.Comparator.comparingInt(fill -> fill[0]))
                    .orElseThrow(() -> new AssertionError("no rail before " + waxed));
            int[] icon = iconBlits(frame).stream().filter(blit -> blit[0] > waxed.x()
                    && Math.abs(blit[1] - waxed.y()) < ControlFlow.LINE).findFirst()
                    .orElseThrow(() -> new AssertionError("no metal icon after " + waxed));
            return List.of(frame.buttonAround(frame.findFirst("+")), frame.buttonAround(rail[0], rail[1]),
                    frame.buttonAround(waxed),
                    frame.buttonAround(icon[0], icon[1]), frame.buttonAround(frame.findFirst("3")),
                    frame.buttonAround(frame.findFirst(":")), frame.buttonAround(frame.findFirst("2")));
        }

        /**
         * Each segment's left border is the right border of the segment before it, the sign's first and the
         * divisor's last, and no outline rings them.
         */
        @Test
        void termSegmentsAbutSharingTheirBordersWithNoRing() {
            DrawnFrame frame = Builder.item(TERM).frame();
            List<int[]> segments = segments(frame);
            for (int index = 1; index < segments.size(); index++) {
                assertEquals(segments.get(index - 1)[2] - 1, segments.get(index)[0], "segment " + index
                        + " shares its left border with the one before: " + segments.stream()
                        .map(java.util.Arrays::toString).toList());
            }
            int left = segments.getFirst()[0];
            int right = segments.getLast()[2];
            assertEquals(List.of(), fills(frame, ControlFlow.OUTLINE_COLOR).stream().filter(fill -> fill[0] <= left
                    && fill[2] >= right).map(java.util.Arrays::toString).toList(), "an outline rings the segments");
        }

        /**
         * The operand's segment opens the term list, the type's the goo type picker, and the multiplier's and the
         * divisor's the number box.
         */
        @Test
        void eachSegmentOpensItsEditor() {
            Builder operand = Builder.item(TERM);
            DrawnFrame frame = operand.frame();
            int[] waxed = segments(frame).get(2);
            assertTrue(frame.targets.click(waxed[0] + 1, waxed[1] + 1), "the operand takes no click");
            assertTrue(operand.frame().texts().contains("$edited"), "the term list opens");
            Builder type = Builder.item(TERM);
            DrawnFrame typed = type.frame();
            int[] metal = segments(typed).get(3);
            assertTrue(typed.targets.click(metal[2] - 2, metal[1] + 1), "the type takes no click");
            assertTrue(type.pane.listOpen(), "the goo type picker opens");
            for (int side = 4; side <= 6; side += 2) {
                Builder ratio = Builder.item(TERM);
                DrawnFrame drawn = ratio.frame();
                int[] box = segments(drawn).get(side);
                assertTrue(drawn.targets.click(box[0] + 1, box[1] + 1), "side " + side + " takes no click");
                ratio.commitNumber("7");
                assertTrue(ratio.written.getLast().contains("7"), ratio.written.toString());
            }
        }

        /**
         * A press on the rail's segment and a release past the next term moves the whole term there.
         */
        @Test
        void railSegmentDragsTheWholeTerm() {
            Builder builder = Builder.item(TERM + " + $edited");
            DrawnFrame frame = builder.frame();
            int[] rail = segments(frame).get(1);
            assertTrue(frame.targets.click(rail[0] + 2, rail[1] + 2), "the rail takes no press");
            assertTrue(frame.targets.drag(AREA.right() - 40, frame.find("$edited").y()));
            builder.frame();
            frame.targets.release();
            assertEquals("$edited + " + TERM, builder.written.getLast());
        }

        /**
         * The term's container is exactly its segments' union, the () handle's mark over it reading from the
         * rail's left to the divisor's right and from the tallest segment's top to its bottom.
         */
        @Test
        void termContainerIsExactlyItsSegmentsUnion() {
            Builder builder = Builder.item(TERM);
            DrawnFrame frame = builder.frame();
            List<int[]> segments = segments(frame);
            clickAt(frame, handles(frame).getFirst());
            DrawnFrame.Text waxed = frame.find("$waxed");
            assertTrue(frame.targets.drag(waxed.x() + 1, waxed.y() + 1));
            List<int[]> marks = fills(builder.frame(), ExpressionBuilderPane.WRAP_MARK);
            assertEquals(1, marks.size(), "one wrap mark");
            int top = segments.stream().mapToInt(segment -> segment[1]).min().orElseThrow();
            int bottom = segments.stream().mapToInt(segment -> segment[3]).max().orElseThrow();
            assertEquals(List.of(segments.getFirst()[0], top, segments.getLast()[2], bottom),
                    java.util.Arrays.stream(marks.getFirst()).boxed().toList(), "the container's bounds");
            frame.targets.release();
        }
    }

    /**
     * Every term's container, the first included, opens on its sign's segment, so any term flips negative in place
     * and drags with its sign (decision every-term-carries-its-sign-as-a-segment).
     */
    @Nested
    class SignOnEveryTerm {
        private static final String SUM = "$waxed + $edited";

        // The sign drawn nearest left of a term's rail on its line, and that sign's segment, which the rail's segment
        // shares its left border with.
        private static DrawnFrame.Text signBefore(DrawnFrame frame, double[] rail) {
            DrawnFrame.Text sign = frame.drawn().stream().filter(text -> (text.text().equals("+")
                    || text.text().equals("-")) && text.x() < rail[0] && Math.abs(text.y() - rail[1]) < HALF_LINE)
                    .max(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                    .orElseThrow(() -> new AssertionError("no sign before the rail at " + rail[0]));
            assertEquals(frame.buttonAround(sign)[2] - 1, frame.buttonAround((int) rail[0], (int) rail[1])[0],
                    "the sign's segment shares its right border with the rail's");
            return sign;
        }

        @Test
        void firstTermOpensOnAPlusSegmentAsEveryTermDoes() {
            DrawnFrame frame = Builder.item(SUM).frame();
            assertEquals("+", signBefore(frame, railOf(frame, List.of(0))).text());
            assertEquals("+", signBefore(frame, railOf(frame, List.of(1))).text());
        }

        @Test
        void clickingTheFirstTermsSignFlipsItNegativeInPlace() {
            Builder builder = Builder.item(SUM);
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text plus = signBefore(frame, railOf(frame, List.of(0)));
            clickAt(frame, plus);
            assertEquals("-$waxed + $edited", builder.written.getLast());
            DrawnFrame flipped = builder.frame();
            DrawnFrame.Text minus = signBefore(flipped, railOf(flipped, List.of(0)));
            assertEquals(List.of("-", flipped.buttonAround(minus)[0]), List.of(minus.text(),
                    frame.buttonAround(plus)[0]), "the - stands where the + stood");
        }

        @Test
        void negativeFirstTermDraggedToTheEndKeepsItsMinus() {
            Builder builder = Builder.item("-$waxed + $edited");
            DrawnFrame frame = builder.frame();
            double[] rail = railOf(frame, List.of(0));
            assertTrue(frame.targets.click(rail[0], rail[1]));
            assertTrue(frame.targets.drag(AREA.right() - 40, frame.find("$edited").y()));
            builder.frame();
            frame.targets.release();
            assertEquals("$edited - $waxed", builder.written.getLast());
        }
    }

    /**
     * The builder draws smaller text on tighter lines, its box padding untouched (decision
     * builder-font-and-spacing-shrink-padding-untouched).
     */
    @Nested
    class SmallerText {

        /**
         * A term's text draws under a pose scale below one.
         */
        @Test
        void termTextDrawsBelowFullScale() {
            DrawnFrame frame = Builder.item("$waxed + $edited").frame();
            float scale = frame.placed("$waxed").scale();
            assertTrue(scale < 1.0f, "the term draws at " + scale);
        }

        /**
         * Two goo type rows stand as close as their boxes allow: the pulse row's box one clear pixel and the button
         * gap below the metal row's, the row pitch below the 34 px it was.
         */
        @Test
        void typeRowsStandAsCloseAsTheirBoxesAllow() {
            DrawnFrame frame = Builder.item(TWO_TYPES).frame();
            List<int[]> outlines = boxesIn(frame, ControlFlow.OUTLINE_COLOR).stream()
                    .map(ExpressionBuilderPaneTest::boundsOf).toList();
            int rowLeft = outlines.stream().mapToInt(box -> box[0]).min().orElseThrow();
            List<int[]> rows = outlines.stream().filter(box -> box[0] == rowLeft)
                    .sorted(java.util.Comparator.comparingInt(box -> box[1])).toList();
            assertEquals(2, rows.size(), "a box a row");
            assertEquals(1 + RowLayout.BUTTON_GAP, rows.get(1)[1] - rows.get(0)[3], "the clear room between rows");
            List<DrawnFrame.Text> colons = frame.drawn().stream().filter(text -> text.text().equals(":")).toList();
            int colonX = colons.stream().mapToInt(DrawnFrame.Text::x).min().orElseThrow();
            List<Integer> rowColons = colons.stream().filter(colon -> colon.x() == colonX).map(DrawnFrame.Text::y)
                    .toList();
            assertTrue(rowColons.get(1) - rowColons.get(0) < 34, "the row pitch " + rowColons);
        }
    }

    /**
     * A term dragged to the slot after a parenthetical wrapping onto two lines draws its drop cursor from that
     * parenthetical box's top to its bottom, inset, and the mask's hole there stands as tall as the cursor (decision
     * drop-indicators-size-to-the-container-they-land-beside).
     */
    @Test
    void dropCursorAndHoleSpanTheParentheticalTheySitBeside() {
        Builder builder = Builder.item("$edited + ($waxed + $edited + $waxed + $edited + $waxed) + $base");
        GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 330, 390);
        DrawnFrame frame = builder.frame(narrow);
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        assertTrue(close.y() > open.y() + HALF_LINE, "the parenthetical wraps onto a second line");
        int[] parenthetical = boxesIn(frame, ExpressionBuilderPane.NEST_BORDER).stream()
                .map(ExpressionBuilderPaneTest::boundsOf).filter(box -> box[1] <= open.y() && box[3] >= close.y())
                .findFirst().orElseThrow(() -> new AssertionError("no parenthetical box"));
        double[] first = railOf(frame, List.of(0));
        assertTrue(frame.targets.click(first[0], first[1]));
        int afterClose = close.x() + ControlFlow.parenWidth(DrawnFrame.font(), ")") + BuilderOverlays.GAP;
        assertTrue(frame.targets.drag(afterClose, close.y() + HALF_LINE));
        DrawnFrame held = builder.frame(narrow);
        List<int[]> cursors = fills(held, ExpressionBuilderPane.DROP_CURSOR);
        assertEquals(1, cursors.size(), "one cursor");
        int[] cursor = cursors.getFirst();
        assertTrue(cursor[3] - cursor[1] > ControlFlow.LINE, "the cursor spans more than one line");
        assertEquals(parenthetical[3] - parenthetical[1] - 2 * 2, cursor[3] - cursor[1],
                "the cursor spans the parenthetical box less its inset");
        int[] hole = fills(held, DragMask.HIGHLIGHT).stream().filter(lit -> lit[0] <= cursor[0] && cursor[2] <= lit[2]
                && lit[1] <= cursor[3] && cursor[1] <= lit[3]).findFirst()
                .orElseThrow(() -> new AssertionError("no hole at the cursor"));
        assertEquals(List.of(cursor[1], cursor[3]), List.of(hole[1], hole[3]), "the hole as tall as the cursor");
        frame.targets.release();
    }

    /**
     * A ratio keeps one clear pixel between its colon's glyph and each of its boxes (decision
     * ratio-colon-takes-no-spaces, as the operator corrected it: the space reduced, not removed).
     */
    @Test
    void ratioKeepsAPixelBetweenItsColonAndEachBox() {
        DrawnFrame frame = Builder.item("2 $waxed / 3").frame();
        List<DrawnFrame.Text> sides = ratioSides(frame);
        assertEquals(List.of("2", "3"), sides.stream().map(DrawnFrame.Text::text).toList(), frame.texts().toString());
        DrawnFrame.Text colon = frame.drawn().get(frame.drawn().indexOf(sides.get(1)) - 1);
        int multiplierRight = frame.buttonAround(sides.get(0))[2];
        int divisorLeft = frame.buttonAround(sides.get(1))[0];
        assertEquals(List.of(multiplierRight + 1, colon.x() + LETTERS.inkWidth(":") + 1),
                List.of(colon.x(), divisorLeft), frame.drawn().toString());
    }

    /**
     * A parenthetical's "(" and ")" draw bold and larger than the term text beside them (decision
     * parentheses-draw-bold-and-larger).
     */
    @Test
    void parenthesesDrawBoldAndLargerThanTheirTerms() {
        DrawnFrame frame = Builder.item("($waxed + $edited) + $base").frame();
        float term = frame.placed("$waxed").scale();
        for (String paren : List.of("(", ")")) {
            DrawnFrame.Placed drawn = frame.placed(paren);
            assertTrue(drawn.bold(), paren + " draws bold");
            assertTrue(drawn.scale() > term, paren + " at " + drawn.scale() + " against the term's " + term);
        }
    }

    /**
     * Terms inline under the indentation rules, in type rows and parentheticals alike (decisions
     * terms-inline-again-under-the-indentation-rules, builder-inlines-terms-under-indentation-rules-with-a-colon).
     */
    @Nested
    class TermsInline {

        /**
         * A goo type row holding three short terms draws all three on the line of its colon.
         */
        @Test
        void typeRowsShortTermsShareItsColonsLine() {
            DrawnFrame frame = Builder.item("{\"metal\": \"$base + $base + $base\"}").frame();
            int colon = frame.drawn().stream().filter(text -> text.text().equals(":")).findFirst()
                    .orElseThrow(() -> new AssertionError(frame.drawn().toString())).y();
            List<Integer> terms = frame.drawn().stream().filter(text -> text.text().equals("$base"))
                    .map(DrawnFrame.Text::y).toList();
            assertEquals(List.of(colon, colon, colon), terms, frame.drawn().toString());
        }

        /**
         * A parenthetical holding two short terms draws "(", both terms and ")" on one line.
         */
        @Test
        void parentheticalsShortTermsShareItsParensLine() {
            DrawnFrame frame = Builder.item("($waxed + $edited) + $base").frame();
            DrawnFrame.Text open = frame.find("(");
            assertTrue(onOneLine(open, frame.find("$waxed")) && onOneLine(open, frame.find("$edited"))
                    && onOneLine(open, frame.find(")")), frame.drawn().toString());
        }

        /**
         * A row's terms run on its colon's line until one would pass the content's right edge, which wraps one
         * indent under the goo type, and no text passes that edge.
         */
        @Test
        void termPastTheRightEdgeWrapsOneIndentUnderTheType() {
            int count = 8;
            DrawnFrame frame = Builder.item("{\"metal\": \"" + String.join(" + ", java.util.Collections.nCopies(count,
                    "$base")) + "\"}").frame();
            // The only row is locked, so its rail stands where the row's indent puts it.
            double[] rowRail = {AREA.left() + ControlFlow.INDENT, 0};
            List<double[]> rails = IntStream.range(0, count).mapToObj(index -> railOf(frame, List.of(0, index)))
                    .toList();
            int wrapped = IntStream.range(0, count).filter(index -> rails.get(index)[1] > rails.getFirst()[1])
                    .findFirst().orElseThrow(() -> new AssertionError("no term wrapped"));
            for (int index = 1; index < wrapped; index++) {
                assertEquals(rails.getFirst()[1], rails.get(index)[1], "term " + index + " shares the first's line");
            }
            assertTrue(wrapped > 1, "a second term fits beside the first");
            assertEquals((int) rowRail[0] + ControlFlow.INDENT, containerLeft(frame, rails.get(wrapped)),
                    "the wrap's indent");
            assertEquals(List.of(), frame.drawn().stream().filter(text -> text.x()
                    + LETTERS.width(text.text()) > CONTENT_RIGHT).toList(), "text past the right edge");
        }
    }

    /**
     * A per-type prefab's 's draws in the segment after its word's, which shares the word's right border, one clear
     * pixel past it, no gap between (decisions possessive-sits-flush-against-its-word-button,
     * containers-segment-into-click-to-edit-buttons).
     */
    @Test
    void possessiveSitsFlushAgainstItsWordButton() {
        DrawnFrame frame = Builder.item("{\"jelly\": \"$food.jelly\"}").frame();
        DrawnFrame.Text food = frame.find("$food");
        DrawnFrame.Text possessive = frame.find("'s");
        int[] word = frame.buttonAround(food);
        assertEquals(word[2] - 1, frame.buttonAround(possessive)[0], "the 's segment shares the word's border");
        assertEquals(word[2] + 1, possessive.x(), frame.drawn().toString());
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
            int icon = iconBlits(frame).stream().filter(blit -> Math.abs(blit[1] - rail[1]) < ControlFlow.LINE)
                    .mapToInt(blit -> blit[0]).min().orElseThrow(() -> new AssertionError("no icon by " + rail[1]));
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
        assertTrue(again.targets.drag(AREA.right() - 40, again.find("$base").y()));
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
        assertFalse(closed.rightClick("$waxed"));
        verify(builder.actions, never()).openVariable(any());
        DrawnFrame unmoved = builder.frame();
        assertFalse(unmoved.texts().contains("$edited"), unmoved.texts().toString());
        unmoved.shiftClick("$waxed");
        verify(builder.actions).openVariable("waxed");
        assertFalse(builder.frame().texts().contains("$edited"));
        builder.frame().click("$waxed");
        List<String> opened = builder.frame().texts();
        assertTrue(opened.contains("$edited"), opened.toString());
    }

    /**
     * Over beetroot's leaf value, the builder offers no "+ sum of terms" and draws no "the sum of:"; its parenthetical
     * reads "(", its terms and ")" on one line (decisions sum-of-terms-leaves-the-builder,
     * terms-inline-again-under-the-indentation-rules).
     */
    @Test
    void parentheticalReadsItsParenItsTermsAndItsCloseOnOneLine() {
        DrawnFrame frame = Builder.item("$dye.leaf - ($food.jelly + $saturation.jelly * 12)").frame();
        assertFalse(frame.texts().contains("+ sum of terms") || frame.texts().contains("the sum of:"),
                frame.texts().toString());
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        DrawnFrame.Text food = frame.find("$food");
        DrawnFrame.Text saturation = frame.find("$saturation");
        assertTrue(onOneLine(open, food) && onOneLine(open, saturation) && onOneLine(open, close),
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

        // The texts drawn on one builder line, left to right, the kind's line first.
        private static List<DrawnFrame.Text> line(DrawnFrame frame, int index) {
            return frame.drawn().stream().filter(text -> lineOf(frame, text.y()) == index)
                    .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).toList();
        }

        // Which builder line a y stands on, the lines read from the texts drawn on them, since a line's height grows
        // per box depth (decision drag-rail-containers-keep-a-pixel-between-borders); texts within half a line of
        // each other share one, as a larger paren's centered glyphs stand a little above its line's words (decision
        // parentheses-draw-bold-and-larger).
        private static int lineOf(DrawnFrame frame, int y) {
            List<Integer> lines = new ArrayList<>();
            for (int top : frame.drawn().stream().map(DrawnFrame.Text::y).distinct().sorted().toList()) {
                if (lines.isEmpty() || top - lines.getLast() >= HALF_LINE) {
                    lines.add(top);
                }
            }
            return (int) lines.stream().filter(top -> top < y + HALF_LINE).count() - 1;
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
            DrawnFrame.Text food = frame.findFirst("$food");
            assertEquals(colon.y(), food.y(), frame.drawn().toString());
            assertTrue(food.x() > colon.x(), food + " after " + colon);
            assertEquals(0, frame.count("="), frame.texts().toString());
        }

        /**
         * Pressing and holding beetroot's leaf row by its rail grabs the row: the next frame darkens the screen and
         * lights the row slot above jelly, where leaf may drop.
         */
        @Test
        void leafRowsRailGrabsTheRowAndLightsWhereItMayDrop() {
            Builder builder = Builder.item(BEETROOT);
            GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 330, 390);
            DrawnFrame frame = builder.frame(narrow);
            double[] leafRail = rowRailOf(frame, 1);
            // The rail as drawn, its grips at its middle, where a press means to take it.
            int[] drawn = fills(frame, ControlFlow.RAIL_COLOR).stream().filter(rail -> rail[0] == (int) leafRail[0]
                    && rail[1] <= leafRail[1] + 3 && rail[3] > leafRail[1]).findFirst()
                    .orElseThrow(() -> new AssertionError("no rail drawn at the leaf row"));
            int grips = (drawn[1] + drawn[3]) / 2;
            assertTrue(frame.targets.click(leafRail[0] + 1, grips), "the leaf rail takes a press at its grips, "
                    + java.util.Arrays.toString(drawn) + " grabbed from " + leafRail[1]);
            assertTrue(frame.targets.drag(leafRail[0] + 1, grips + 1), "the leaf rail takes the drag");
            DrawnFrame held = builder.frame(narrow);
            assertFalse(fills(held, DragMask.SHADOW).isEmpty(), "the screen darkens");
            assertFalse(fills(held, DragMask.HIGHLIGHT).isEmpty(), "a drop place lights");
            frame.targets.release();
        }

        /**
         * Leaf's first term, $dye's leaf, carries its sign, so moving it behind the subtracted parenthetical leaves
         * that parenthetical first with its minus: its rail draws light blue and takes the press, as leaf's row rail,
         * which may move above jelly, does (decision every-term-carries-its-sign-as-a-segment, and the operator's
         * ruling of 2026-11-03: a rail that can drag draws light blue).
         */
        @Test
        void dyesRailDragsNowItsParentheticalMayStandFirst() {
            Builder builder = Builder.item(BEETROOT);
            GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 330, 390);
            DrawnFrame frame = builder.frame(narrow);
            DrawnFrame.Text dye = frame.find("$dye");
            int[] dyeRail = railBefore(frame, ControlFlow.RAIL_COLOR, dye);
            int middle = (dyeRail[1] + dyeRail[3]) / 2;
            assertTrue(frame.targets.click(dyeRail[0] + 1, middle), "the light blue rail takes no press");
            assertTrue(frame.targets.drag(dyeRail[0] + 1, middle + 1));
            assertFalse(fills(builder.frame(narrow), DragMask.SHADOW).isEmpty(), "the light blue rail starts no drag");
            frame.targets.release();
            double[] leafRail = rowRailOf(frame, 1);
            assertTrue(fills(frame, ControlFlow.RAIL_COLOR).stream().anyMatch(rail -> rail[0] == (int) leafRail[0]),
                    "leaf's row rail draws light blue");
        }

        // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
        @Test
        void narrowRowsWrapUnderTheIndentationRules() {
            Builder builder = Builder.item(BEETROOT);
            DrawnFrame frame = builder.frame(new GroupsPane.Bounds(0, 0, 220, 390));
            int typeLeft = ControlFlow.INDENT;
            DrawnFrame.Text food = frame.findFirst("$food");
            DrawnFrame.Text saturation = frame.findFirst("$saturation");
            assertTrue(saturation.y() > food.y(), frame.drawn().toString());
            // A wrapped term's line opens on its container, one indent in under the type.
            double[] saturationRail = railOf(frame, List.of(0, 1));
            assertEquals(typeLeft + ControlFlow.INDENT, containerLeft(frame, saturationRail), frame.drawn().toString());
            assertEquals(lineOf(frame, saturation.y()), lineOf(frame, (int) saturationRail[1]));
            DrawnFrame.Text dye = frame.find("$dye");
            DrawnFrame.Text minus = frame.drawn().stream().filter(text -> text.text().equals("-")
                    && text.y() > dye.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            double[] parentheticalRail = railOf(frame, List.of(1, 1));
            assertEquals(typeLeft + ControlFlow.INDENT + BOX_ROOM, (int) parentheticalRail[0],
                    frame.drawn().toString());
            assertEquals(lineOf(frame, minus.y()), lineOf(frame, (int) parentheticalRail[1]));
            assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("(") && onOneLine(text, minus)),
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
            // Decision terms-inline-again-under-the-indentation-rules: saturation follows food on the row's line.
            // Decision every-term-carries-its-sign-as-a-segment: food, the row's first term, opens on its +.
            assertEquals(List.of(":", "+", "$food", "'s", "1", ":", "1", "+", "$saturation", "'s", "12", ":", "1"),
                    lineTexts(frame, 1), frame.drawn().toString());
            assertEquals(ControlFlow.INDENT + ROW_RAIL + ButtonPainter.ICON_PADDING, iconXs(frame, 1).getFirst());
            int jelly = iconXs(frame, 1).get(1);
            assertTrue(line(frame, 1).get(3).x() < jelly && jelly < line(frame, 1).get(4).x(),
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

        // Leaf's subtracted parenthetical fits the rest of dye's line, so "-" "(" and its terms follow dye's ratio
        // inline, each term opening on its sign, and no X (decisions terms-and-type-rows-drag-to-a-trash-strip,
        // terms-inline-again-under-the-indentation-rules, every-term-carries-its-sign-as-a-segment).
        @Test
        void parentheticalLineReadsJoinParenThenItsTermsInline() {
            DrawnFrame frame = Builder.item(BEETROOT).frame();
            DrawnFrame.Text open = frame.find("(");
            int head = lineOf(frame, open.y());
            assertEquals(List.of(":", "+", "$dye", "'s", "1", ":", "1", "-", "(", "+", "$food"),
                    lineTexts(frame, head).subList(0, 11), frame.drawn().toString());
            assertFalse(lineTexts(frame, head).contains("X"), frame.drawn().toString());
            // Decision terms-inline-again-under-the-indentation-rules: ")" follows the parenthetical's last term.
            DrawnFrame.Text last = frame.drawn().stream().filter(text -> text.text().equals("$saturation")
                    && onOneLine(text, open)).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            assertTrue(onOneLine(frame.find(")"), last) && frame.find(")").x() > last.x(), frame.drawn().toString());
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
            int cancelRight = changed.buttonAround(cancel)[2];
            assertTrue(cancelRight < changed.buttonAround(done)[0], cancel + " left of " + done);
            assertEquals(CONTENT_RIGHT, changed.buttonAround(done)[2]);
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
        int lineTop = frame.findFirst(ExpressionLines.COLON).y() - WORDS_DROP;
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
        DrawnFrame.Text edited = frame.find("$edited");
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
            DrawnFrame.Text edited = frame.find("$edited");
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
