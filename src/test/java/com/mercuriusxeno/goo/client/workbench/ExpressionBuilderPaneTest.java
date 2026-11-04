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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    // A type row's rail segment, whose right border its goo type's segment shares (decision
    // type-row-rail-sits-on-the-goo-type-button-with-no-box).
    private static final int ROW_RAIL = ControlFlow.RAIL_SEGMENT_WIDTH - 1;
    // The outline a term's and a type row's box drew before their containers were segmented, which nothing draws now.
    private static final int RETIRED_OUTLINE = 0x60FFFFFF;

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
            return frameAt(area, 0);
        }

        /**
         * @param area the area the builder spans
         * @param top  the builder's top edge, as a builder scrolled near the screen's bottom stands
         * @return the frame the builder drew there
         */
        DrawnFrame frameAt(GroupsPane.Bounds area, int top) {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, font, area, top, frame.targets);
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
     * A parenthetical opening a parenthetical stays on the line of the outer one's "(", the line growing to give
     * the inner box its padding (decision drag-rail-containers-keep-a-pixel-between-borders).
     */
    @Test
    void nestedParentheticalsStayInline() {
        DrawnFrame frame = Builder.item("$waxed + (($base + $waxed) + $edited)").frame();
        double[] outer = railOf(frame, List.of(1));
        double[] inner = railOf(frame, List.of(1, 0));
        DrawnFrame.Text open = frame.drawn().stream().filter(text -> text.text().equals("(")).findFirst().orElseThrow();
        DrawnFrame.Placed outerOpen = placedAt(frame, "(", 0);
        double openMiddle = outerOpen.y() + outerOpen.scale() * Lettering.inkRows(DrawnFrame.font()) / 2.0;
        DrawnFrame.Text base = frame.find("$base");
        double baseMiddle = base.y() + LETTERS.lineHeight() / 2.0;
        assertTrue(Math.abs(openMiddle - baseMiddle) < HALF_LINE, "the inner parenthetical's terms share the "
                + "outer's ( line, the ( centered on it at " + openMiddle + " against " + baseMiddle);
        assertTrue(inner[0] > outer[0], "the inner rail stands right of the outer one");
    }

    // Where a term's or a parenthetical's container opens: its rail's segment (the operator's ruling of 2026-11-04
    // placing the rail left of the sign).
    private static int containerLeft(DrawnFrame frame, double[] rail) {
        int framed = frame.buttonAround((int) rail[0], (int) rail[1])[0];
        boolean ringed = fills(frame, ControlFlow.TERM_BORDER).stream().anyMatch(side -> side[0] == framed - 1
                && side[1] <= rail[1] && rail[1] < side[3]);
        return ringed ? framed - 1 : framed;
    }

    // A term's ringed container round the frame of a piece in it: the frame grown by the pixel the 2 px ring adds
    // (decision term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
    private static int[] ringAround(int[] framed) {
        return new int[] {framed[0] - 1, framed[1] - 1, framed[2] + 1, framed[3] + 1};
    }

    // Whether two texts stand on one builder line, their tops within half a line.
    private static boolean onOneLine(DrawnFrame.Text one, DrawnFrame.Text other) {
        return Math.abs(one.y() - other.y()) < HALF_LINE;
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
     * A plain term's, a parenthetical's and a type row's rail fills its own segment, flush inside the segment's border
     * from top to bottom, with three one-pixel grip lines inside it (decisions rails-fill-the-left-edge-of-their-box,
     * containers-segment-into-click-to-edit-buttons, parens-are-segments-bounding-the-parenthetical,
     * type-row-rail-sits-on-the-goo-type-button-with-no-box).
     */
    @Test
    void railsFillTheLeftEdgeOfTheirBoxes() {
        DrawnFrame frame = Builder.item("$base + $waxed + ($waxed - $edited)").frame();
        for (List<Integer> path : List.of(List.of(1), List.of(2))) {
            assertRailFillsItsSegment(frame, railOf(frame, path));
        }
        DrawnFrame typed = Builder.item(TWO_TYPES).frame();
        assertRailFillsItsSegment(typed, rowRailOf(typed, 0));
    }

    // The rail answering at a point fills its segment one pixel inside the segment's border, its three one-pixel grip
    // lines inside it.
    private static void assertRailFillsItsSegment(DrawnFrame frame, double[] point) {
        int[] rail = fills(frame, ControlFlow.RAIL_COLOR).stream().filter(fill -> fill[0] == (int) point[0]
                && fill[1] <= point[1] && fill[3] > point[1]).findFirst()
                .orElseThrow(() -> new AssertionError("no rail fill at " + point[0] + "," + point[1]));
        assertEquals(List.of(rail[0] - 1, rail[1] - 1, rail[2] + 1, rail[3] + 1),
                java.util.Arrays.stream(frame.buttonAround(rail[0], rail[1])).boxed().toList(),
                "the rail fills its segment inside the border");
        List<int[]> grips = fills(frame, ControlFlow.GRIP_COLOR).stream()
                .filter(fill -> fill[0] >= rail[0] && fill[2] <= rail[2] && fill[1] >= rail[1] && fill[3] <= rail[3])
                .toList();
        assertEquals(3, grips.size(), "grip lines inside the rail");
        grips.forEach(grip -> assertEquals(1, grip[3] - grip[1], "a grip line is one pixel tall"));
    }

    /**
     * Sibling terms' segmented containers stand the term margin apart, the room between containers kept as the
     * operator ruled when they were segmented.
     */
    @Test
    void siblingContainersStandTheTermMarginApart() {
        assertTrue(ExpressionBuilderPane.TERM_MARGIN > 4, "the margin was 4 before");
        DrawnFrame frame = Builder.item("$waxed + $edited + ($base - $waxed)").frame();
        // Edited's container opens on its rail's segment; waxed's ends on the last segment left of it.
        double[] edited = railOf(frame, List.of(1));
        int editedLeft = containerLeft(frame, edited);
        int waxedRight = fills(frame, ControlFlow.TERM_BORDER).stream().filter(side -> side[2] <= editedLeft
                && side[1] <= edited[1] && edited[1] < side[3]).mapToInt(side -> side[2]).max()
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
     * number as Nx before the prefab, and the prefab button opens the prefab list (decisions
     * operand-times-number-terms-edit-like-every-term,
     * multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after).
     */
    @Test
    void operandTimesNumberDrawsItsButtonsAndOpensTheirLists() {
        Builder builder = Builder.item("$food.jelly + $saturation.jelly * 12");
        DrawnFrame frame = builder.frame();
        DrawnFrame.Text saturation = frame.find("$saturation");
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("12x") && text.y() == saturation.y()
                && text.x() < saturation.x()), frame.drawn().toString());
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

        // The term's segments left to right: its rail's, its sign's, its multiplier's (Nx), its name's, its type's and
        // its divisor's (/n), each as left, top, right and bottom (decision
        // multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after).
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
            return List.of(frame.buttonAround(rail[0], rail[1]), frame.buttonAround(frame.findFirst("+")),
                    frame.buttonAround(frame.findFirst("3x")), frame.buttonAround(waxed),
                    frame.buttonAround(icon[0], icon[1]), frame.buttonAround(frame.findFirst("/2")));
        }

        /**
         * The rail, the sign, the multiplier, the name with its 's, the type and the divisor abut, a 2 px seam between
         * each two; no ring of the retired box outline color stands round them (the operator's rulings of 2026-11-04:
         * the rail left of the sign, the 's in the name's segment).
         */
        @Test
        void termSegmentsAbutSharingTheirBordersWithNoRing() {
            DrawnFrame frame = Builder.item(TERM).frame();
            List<int[]> segments = segments(frame);
            for (int index = 1; index < segments.size(); index++) {
                assertEquals(segments.get(index - 1)[2], segments.get(index)[0], "segment " + index
                        + " abuts the one before: " + segments.stream().map(java.util.Arrays::toString).toList());
            }
            assertArrayEquals(segments.get(3), frame.buttonAround(frame.findFirst("'s")), "the 's in the name's segment");
            int left = segments.getFirst()[0];
            int right = segments.getLast()[2];
            assertEquals(List.of(), fills(frame, RETIRED_OUTLINE).stream().filter(fill -> fill[0] <= left
                    && fill[2] >= right).map(java.util.Arrays::toString).toList(), "an outline rings the segments");
        }

        // The term's container: its segments' union grown by the pixel its 2 px ring adds outside their frames.
        private static int[] ringOf(List<int[]> segments) {
            return new int[] {segments.getFirst()[0] - 1, segments.getFirst()[1] - 1, segments.getLast()[2] + 1,
                segments.getFirst()[3] + 1};
        }

        // A color's brightness as the sum of its red, green and blue.
        private static int luminance(int color) {
            return (color >> 16 & 0xFF) + (color >> 8 & 0xFF) + (color & 0xFF);
        }

        /**
         * The term's container draws a 2 px ring in TERM_BORDER, brighter than a segment's border, round its
         * segments, from the rail's side to the divisor's (decision
         * term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
         */
        @Test
        void termContainerDrawsATwoPixelBrighterRing() {
            assertTrue(luminance(ControlFlow.TERM_BORDER) > luminance(ButtonPainter.BORDER), "the ring reads brighter");
            DrawnFrame frame = Builder.item(TERM).frame();
            int[] ring = ringOf(segments(frame));
            List<List<Integer>> sides = fills(frame, ControlFlow.TERM_BORDER).stream()
                    .map(side -> java.util.Arrays.stream(side).boxed().toList()).toList();
            assertEquals(List.of(List.of(ring[0], ring[1], ring[2], ring[1] + 2),
                    List.of(ring[0], ring[3] - 2, ring[2], ring[3]),
                    List.of(ring[0], ring[1] + 2, ring[0] + 2, ring[3] - 2),
                    List.of(ring[2] - 2, ring[1] + 2, ring[2], ring[3] - 2)), sides, "the ring's four 2 px sides");
        }

        /**
         * Each seam between two of the term's segments is 2 px, SEAM_LIGHT on its left pixel and the darker
         * SEAM_DARK on its right, from the container's top to its bottom, the right segment's fill starting past the
         * dark pixel (decision term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
         */
        @Test
        void eachSeamIsTwoPixelsLightThenDark() {
            assertTrue(luminance(ControlFlow.SEAM_DARK) < luminance(ControlFlow.SEAM_LIGHT), "the dark pixel darker");
            DrawnFrame frame = Builder.item(TERM).frame();
            List<int[]> segments = segments(frame);
            int[] ring = ringOf(segments);
            List<int[]> lights = fills(frame, ControlFlow.SEAM_LIGHT);
            List<int[]> darks = fills(frame, ControlFlow.SEAM_DARK);
            for (int index = 1; index < segments.size(); index++) {
                int seam = segments.get(index)[0];
                assertTrue(lights.stream().anyMatch(fill -> java.util.Arrays.equals(fill,
                        new int[] {seam - 1, ring[1], seam, ring[3]})), "no light pixel left of seam " + index);
                assertTrue(darks.stream().anyMatch(fill -> java.util.Arrays.equals(fill,
                        new int[] {seam, ring[1], seam + 1, ring[3]})), "no dark pixel right of seam " + index);
                int fillLeft = segments.get(index)[0] + 1;
                assertTrue(fills(frame, ButtonPainter.FILL).stream().anyMatch(fill -> fill[0] == fillLeft
                        && fill[1] == ring[1] + 2), "segment " + index + "'s fill past its dark pixel");
            }
            assertEquals(segments.size() - 1, lights.size(), "a light pixel a seam");
        }

        /**
         * Only a term rings: a parenthetical's lead and ratio segments and its container, a type row's goo type
         * button and the () handle keep their 1 px frames, no ring or seam color inside them (decision
         * term-draws-a-two-pixel-brighter-border-with-two-tone-seams: terms only).
         */
        @Test
        void onlyATermRings() {
            DrawnFrame frame = Builder.item("3 ($waxed + $edited) / 2 + $base").frame();
            List<int[]> framed = new ArrayList<>();
            double[] rail = railOf(frame, List.of(0));
            framed.add(frame.buttonAround((int) rail[0], (int) rail[1]));
            framed.add(frame.buttonAround(frame.findFirst("3x")));
            framed.add(frame.buttonAround(frame.findFirst("/2")));
            framed.addAll(fills(frame, ExpressionBuilderPane.NEST_BORDER));
            DrawnFrame typed = Builder.item(TWO_TYPES).frame();
            double[] rowRail = rowRailOf(typed, 0);
            List<int[]> rowFramed = List.of(typed.buttonAround((int) rowRail[0], (int) rowRail[1]),
                    typed.buttonAround(handles(typed).getFirst()));
            for (int color : List.of(ControlFlow.TERM_BORDER, ControlFlow.SEAM_LIGHT, ControlFlow.SEAM_DARK)) {
                assertEquals(List.of(), fills(frame, color).stream().filter(fill -> framed.stream()
                        .anyMatch(box -> overlaps(fill, box))).map(java.util.Arrays::toString).toList(),
                        "a ring or seam in a parenthetical's own frames");
                assertEquals(List.of(), fills(typed, color).stream().filter(fill -> rowFramed.stream()
                        .anyMatch(box -> overlaps(fill, box))).map(java.util.Arrays::toString).toList(),
                        "a ring or seam in a type button or the handle");
            }
        }

        // Whether two rectangles, as left, top, right, bottom with the right and bottom outside, share a pixel.
        private static boolean overlaps(int[] one, int[] other) {
            return one[0] < other[2] && other[0] < one[2] && one[1] < other[3] && other[1] < one[3];
        }

        /**
         * The operand's segment opens the term list, the type's the goo type picker, and the multiplier's and the
         * divisor's the number box.
         */
        @Test
        void eachSegmentOpensItsEditor() {
            Builder operand = Builder.item(TERM);
            DrawnFrame frame = operand.frame();
            int[] waxed = segments(frame).get(3);
            assertTrue(frame.targets.click(waxed[0] + 1, waxed[1] + 1), "the operand takes no click");
            assertTrue(operand.frame().texts().contains("$edited"), "the term list opens");
            Builder type = Builder.item(TERM);
            DrawnFrame typed = type.frame();
            int[] metal = segments(typed).get(4);
            assertTrue(typed.targets.click(metal[2] - 2, metal[1] + 1), "the type takes no click");
            assertTrue(type.pane.listOpen(), "the goo type picker opens");
            for (int side : List.of(2, 5)) {
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
            int[] rail = segments(frame).getFirst();
            assertTrue(frame.targets.click(rail[0] + 2, rail[1] + 2), "the rail takes no press");
            assertTrue(frame.targets.drag(AREA.right() - 40, frame.find("$edited").y()));
            builder.frame();
            frame.targets.release();
            assertEquals("$edited + " + TERM, builder.written.getLast());
        }

        /**
         * The term's container is exactly its ringed segments' union, the () handle's mark over it reading from the
         * ring's outer left to its outer right and top to bottom.
         */
        @Test
        void termContainerIsExactlyItsSegmentsUnion() {
            Builder builder = Builder.item(TERM);
            DrawnFrame frame = builder.frame();
            int[] ring = ringOf(segments(frame));
            clickAt(frame, handles(frame).getFirst());
            DrawnFrame.Text waxed = frame.find("$waxed");
            assertTrue(frame.targets.drag(waxed.x() + 1, waxed.y() + 1));
            List<int[]> marks = fills(builder.frame(), ExpressionBuilderPane.WRAP_MARK);
            assertEquals(1, marks.size(), "one wrap mark");
            assertArrayEquals(ring, marks.getFirst(), "the container's bounds");
            frame.targets.release();
        }
    }

    /**
     * A builder's lines stand apart, so nothing on one line touches anything on the next: the operator saw a relative
     * value's terms overlap its kind dropdown and wrapped lines of terms touch once the box padding went.
     */
    @Nested
    class LinesStandApart {

        @Test
        void anItemTermStandsClearOfTheKindDropdown() {
            DrawnFrame frame = Builder.item("anvil + $waxed").frame();
            int kindBottom = frame.buttonAround(frame.drawn().getFirst())[3];
            int termTop = frame.buttonAround(frame.find("anvil"))[1];
            assertTrue(termTop > kindBottom, "the term's top " + termTop + " against the kind's bottom "
                    + kindBottom);
        }

        @Test
        void wrappedLinesOfTermsStandTheLineGapApart() {
            DrawnFrame frame = Builder.item("$waxed + $edited + $base + $waxed + $edited + $base").frame(
                    new GroupsPane.Bounds(0, 0, 200, 390));
            List<int[]> rails = IntStream.range(0, 6).mapToObj(index -> railOf(frame, List.of(index)))
                    .map(rail -> frame.buttonAround((int) rail[0], (int) rail[1])).toList();
            List<Integer> tops = rails.stream().map(rail -> rail[1]).distinct().sorted().toList();
            assertTrue(tops.size() > 1, "the terms wrap: " + tops);
            // Each term's ringed container stands a pixel past its rail's frame above and below.
            int height = rails.getFirst()[3] - rails.getFirst()[1] + 2;
            for (int line = 1; line < tops.size(); line++) {
                assertEquals(ControlFlow.BUILDER_LINE_GAP, tops.get(line) - tops.get(line - 1) - height,
                        "the room between line " + (line - 1) + " and line " + line);
            }
        }
    }

    /**
     * A goo type row draws no box: its rail is the first segment of its goo type button, and its colon and terms
     * follow on the line (decisions type-row-rail-sits-on-the-goo-type-button-with-no-box,
     * containers-segment-into-click-to-edit-buttons).
     */
    @Nested
    class TypeRowSegments {

        // The row's rail segment and its goo type's icon segment, each as left, top, right and bottom.
        private static List<int[]> typeButton(DrawnFrame frame, int row) {
            double[] grab = rowRailOf(frame, row);
            int[] rail = frame.buttonAround((int) grab[0], (int) grab[1]);
            int[] icon = iconBlits(frame).stream().filter(blit -> blit[0] > rail[0]
                    && Math.abs(blit[1] - grab[1]) < ControlFlow.LINE).min(java.util.Comparator.comparingInt(
                            blit -> blit[0])).orElseThrow(() -> new AssertionError("no icon right of row " + row));
            return List.of(rail, frame.buttonAround(icon[0], icon[1]));
        }

        @Test
        void rowRailIsTheGooTypeButtonsFirstSegmentAndNoBoxRingsTheRow() {
            DrawnFrame frame = Builder.item(TWO_TYPES).frame();
            List<int[]> metal = typeButton(frame, 0);
            int[] rail = metal.get(0);
            int[] icon = metal.get(1);
            assertEquals(rail[2] - 1, icon[0], "the icon's segment shares the rail's right border");
            assertEquals(List.of(rail[1], rail[3]), List.of(icon[1], icon[3]), "the rail and the icon stand alike");
            int last = frame.buttonAround(frame.findFirst("5"))[2];
            assertEquals(List.of(), fills(frame, RETIRED_OUTLINE).stream().filter(fill -> fill[0] <= rail[0]
                    && fill[2] >= last).map(java.util.Arrays::toString).toList(), "a box rings the row");
        }

        @Test
        void rowRailReordersTheRowAndItsIconOpensThePicker() {
            Builder builder = Builder.item(TWO_TYPES);
            DrawnFrame frame = builder.frame();
            int[] rail = typeButton(frame, 0).get(0);
            assertTrue(frame.targets.click(rail[0] + 2, rail[1] + 2), "the rail takes no press");
            assertTrue(frame.targets.drag(rail[0] + 2, frame.find("+?").y()));
            builder.frame();
            frame.targets.release();
            assertEquals("{\"pulse\": \"2\", \"metal\": \"5\"}", builder.written.getLast());
            Builder picking = Builder.item(TWO_TYPES);
            DrawnFrame typed = picking.frame();
            int[] icon = typeButton(typed, 0).get(1);
            assertTrue(typed.targets.click(icon[0] + 2, icon[1] + 2), "the icon takes no click");
            assertTrue(picking.pane.listOpen(), "the goo type picker opens");
        }
    }

    /**
     * A parenthetical reads [rail][+/-], then its tinted container with a 1 px border, one clear pixel between the
     * border and "(", "(" and its terms, its terms and ")", and ")" and the border, then its ratio after the
     * container; its parens draw regular weight, as tall as the container's contents, so a nested one is smaller
     * (the operator's rulings of 2026-11-04).
     */
    @Nested
    class ParenContainers {
        private static final String SCALED = "3 ($waxed + $edited) / 2 + $base";

        // The parenthetical's border as left, top, right and bottom, read off the border color's fills.
        private static int[] border(DrawnFrame frame) {
            List<int[]> sides = fills(frame, ExpressionBuilderPane.NEST_BORDER);
            assertFalse(sides.isEmpty(), "no parenthetical border");
            return new int[] {sides.stream().mapToInt(side -> side[0]).min().orElseThrow(),
                sides.stream().mapToInt(side -> side[1]).min().orElseThrow(),
                sides.stream().mapToInt(side -> side[2]).max().orElseThrow(),
                sides.stream().mapToInt(side -> side[3]).max().orElseThrow()};
        }

        @Test
        void everythingInsideStandsAClearPixelFromTheBorderAndEachOther() {
            DrawnFrame frame = Builder.item(SCALED).frame();
            int[] box = border(frame);
            DrawnFrame.Placed open = frame.placed("(");
            DrawnFrame.Placed close = frame.placed(")");
            Lettering parens = new Lettering(DrawnFrame.font(), open.scale());
            double[] first = railOf(frame, List.of(0, 0));
            int[] firstRail = frame.buttonAround((int) first[0], (int) first[1]);
            int[] lastDivisor = frame.buttonAround(frame.drawn().get(frame.drawn().indexOf(frame.find(")")) - 1));
            assertEquals(box[0] + 2, open.x(), "( a clear pixel inside the left border");
            int[] firstTerm = ringAround(firstRail);
            int[] lastTerm = ringAround(lastDivisor);
            assertEquals(open.x() + parens.inkWidth("(") + 1, firstTerm[0], "the first term a clear pixel past (");
            assertEquals(lastTerm[2] + 1, close.x(), ") a clear pixel past the last term");
            assertEquals(close.x() + parens.inkWidth(")") + 2, box[2], ") a clear pixel inside the right border");
            assertEquals(List.of(box[1] + 2, box[3] - 2), List.of(firstTerm[1], firstTerm[3]),
                    "the terms a clear pixel inside the top and bottom borders");
            assertTrue(frame.buttonAround(frame.findFirst("/2"))[0] > box[2], "the divisor after the container");
            double[] rail = railOf(frame, List.of(0));
            int[] railSegment = frame.buttonAround((int) rail[0], (int) rail[1]);
            int[] sign = frame.buttonAround(frame.findFirst("+"));
            assertEquals(railSegment[2] - 1, sign[0], "the sign right of the rail");
            // The operator's ruling of 2026-11-04: the rail, the sign and the multiplier touch the container.
            int[] multiplier = frame.buttonAround(frame.findFirst("3x"));
            assertEquals(List.of(sign[2] - 1, box[0]), List.of(multiplier[0], multiplier[2]),
                    "the multiplier after the sign, touching the container's left border");
        }

        @Test
        void parensDrawRegularWeightAsTallAsTheirContentsSmallerWhenNested() {
            DrawnFrame frame = Builder.item("(($waxed) + $edited) + $base").frame();
            assertEquals(2, frame.count("("), "two parentheticals");
            List<Float> scales = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                scales.add(placedAt(frame, "(", index).scale());
                assertFalse(placedAt(frame, "(", index).bold(), "( draws regular weight");
            }
            assertTrue(scales.get(0) > scales.get(1), "the outer ( larger than the inner: " + scales);
            float term = frame.placed("$waxed").scale();
            assertTrue(scales.get(1) > term, "the inner ( larger than the term text");
        }

        /**
         * A parenthetical whose terms wrap is one box, like a div: its rail and sign touch its left border, every
         * line of its terms starts at the same left right after "(", its right edge stands a clear pixel and its
         * border past its widest line, it grows down to hold its last line, and the term after it stands outside it
         * (the operator's ruling of 2026-11-04).
         */
        @Test
        void aWrappedParentheticalIsOneBoxHoldingEveryLine() {
            DrawnFrame frame = Builder.item("$base + ($waxed + $edited + $waxed + $edited + $waxed + $edited) + $base")
                    .frame(new GroupsPane.Bounds(0, 0, 260, 390));
            int[] box = border(frame);
            // Each inner term's whole container: its rail segment's ring to its ring's right side, the ring's top
            // side spanning it.
            List<int[]> inner = IntStream.range(0, 6).mapToObj(index -> railOf(frame, List.of(1, index)))
                    .map(rail -> ringAround(frame.buttonAround((int) rail[0], (int) rail[1])))
                    .map(ring -> new int[] {ring[0], ring[1], fills(frame, ControlFlow.TERM_BORDER).stream()
                            .filter(side -> side[0] == ring[0] && side[1] == ring[1]).mapToInt(side -> side[2])
                            .max().orElseThrow(), ring[3]}).toList();
            List<Integer> lineTops = inner.stream().map(ring -> ring[1]).distinct().sorted().toList();
            assertTrue(lineTops.size() > 1, "the parenthetical's terms wrap: " + lineTops);
            int contentLeft = inner.getFirst()[0];
            for (int top : lineTops.subList(1, lineTops.size())) {
                int lineLeft = inner.stream().filter(ring -> ring[1] == top).mapToInt(ring -> ring[0]).min()
                        .orElseThrow();
                assertEquals(contentLeft, lineLeft, "the line at " + top + " starts where the first one does");
            }
            DrawnFrame.Placed close = frame.placed(")");
            int closeRight = close.x() + new Lettering(DrawnFrame.font(), close.scale()).inkWidth(")");
            int widest = Math.max(closeRight, inner.stream().mapToInt(ring -> ring[2]).max().orElseThrow());
            assertEquals(widest + 2, box[2], "the box's right a clear pixel and its border past its widest line");
            int lastBottom = Math.max(inner.getLast()[3], close.y() + 1);
            assertTrue(box[3] >= inner.getLast()[3] + 2 && box[3] >= lastBottom, "the box holds its last line");
            assertEquals(inner.getFirst()[1] - 2, box[1], "the box's top a clear pixel above its first line");
            double[] rail = railOf(frame, List.of(1));
            int[] sign = frame.buttonAround(frame.drawn().stream().filter(text -> text.text().equals("+")
                    && text.x() > rail[0] && Math.abs(text.y() - rail[1]) < HALF_LINE).min(
                    java.util.Comparator.comparingInt(DrawnFrame.Text::x)).orElseThrow());
            assertEquals(box[0], sign[2], "the sign touches the box's left border");
            double[] after = railOf(frame, List.of(2));
            int[] next = ringAround(frame.buttonAround((int) after[0], (int) after[1]));
            assertTrue(next[0] >= box[2] || next[1] >= box[3], "the term after the box stands inside it: "
                    + java.util.Arrays.toString(next) + " against " + java.util.Arrays.toString(box));
        }

        @Test
        void handleDroppedOnTheContainerWrapsItAgain() {
            Builder builder = Builder.item(SCALED);
            DrawnFrame frame = builder.frame();
            clickAt(frame, handles(frame).getFirst());
            DrawnFrame.Text open = frame.find("(");
            assertTrue(frame.targets.drag(open.x() + 1, open.y() + 1));
            builder.frame();
            frame.targets.release();
            assertEquals("(3 ($waxed + $edited) / 2) + $base", builder.written.getLast());
        }
    }

    /**
     * The control bay's x and / drag onto a term, a parenthetical being a term and a bare number taking neither,
     * and open the number box at that term's multiplier or divisor with its value selected, so a typed number scales
     * that side of that term alone (decision x-and-slash-drag-from-the-bay-onto-a-term).
     */
    @Nested
    class FactorBay {

        // The bay's button reading the label, on the () handle's line.
        private static DrawnFrame.Text bay(DrawnFrame frame, String label) {
            DrawnFrame.Text handle = handles(frame).getFirst();
            return frame.drawn().stream().filter(text -> text.text().equals(label) && text.y() == handle.y())
                    .findFirst().orElseThrow(() -> new AssertionError("no " + label + " in the bay: " + frame.drawn()));
        }

        // Grabs the bay's button and drags it to a point, drawing the frame that follows.
        private static void hold(Builder builder, DrawnFrame frame, String label, double x, double y) {
            DrawnFrame.Text button = bay(frame, label);
            assertTrue(frame.targets.click(button.x() + 1, button.y() + 1), "the bay's " + label + " grabs");
            assertTrue(frame.targets.drag(x, y));
            builder.frame();
        }

        // The number box focused last, which the builder opened.
        private static EditBox numberBox(Builder builder) {
            ArgumentCaptor<AbstractWidget> focused = ArgumentCaptor.forClass(AbstractWidget.class);
            verify(builder.actions, atLeastOnce()).focus(focused.capture());
            return (EditBox) focused.getValue();
        }

        // Drops the bay's button on a term's name and answers the number box it opened, after the frame placing it.
        private static EditBox dropOn(Builder builder, String label, String name) {
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text target = frame.find(name);
            hold(builder, frame, label, target.x() + 1, target.y() + 1);
            frame.targets.release();
            builder.frame();
            return numberBox(builder);
        }

        @Test
        void xDroppedOnATermOpensItsMultiplierAndScalesItAlone() {
            Builder builder = Builder.item("$waxed + $edited");
            EditBox box = dropOn(builder, "x", "$edited");
            int multiplier = builder.frame().buttonAround(builder.frame().findFirst("1x"))[0];
            assertEquals(List.of("1", "1", multiplier), List.of(box.getValue(), box.getHighlighted(), box.getX()),
                    "the box at the multiplier, its value selected");
            builder.commitNumber("3");
            assertEquals("$waxed + 3 $edited", builder.written.getLast());
        }

        @Test
        void slashDroppedOnATermOpensItsDivisorAndScalesItAlone() {
            Builder builder = Builder.item("$waxed + $edited");
            EditBox box = dropOn(builder, "/", "$edited");
            int divisor = builder.frame().buttonAround(builder.frame().findFirst("/1"))[0];
            assertEquals(List.of("1", "1", divisor), List.of(box.getValue(), box.getHighlighted(), box.getX()),
                    "the box at the divisor, its value selected");
            builder.commitNumber("3");
            assertEquals("$waxed + $edited / 3", builder.written.getLast());
        }

        @Test
        void aParentheticalTakesTheDropAndANumberTakesNone() {
            String shown = "{\"metal\": \"5 + $base + ($base)\"}";
            Builder builder = Builder.item(shown);
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text number = frame.findFirst("5");
            DrawnFrame.Text reference = frame.findFirst("$base");
            DrawnFrame.Text open = frame.find("(");
            hold(builder, frame, "x", AREA.right() - 60, AREA.bottom() - 5);
            List<int[]> holes = fills(builder.frame(), DragMask.HIGHLIGHT);
            java.util.function.Predicate<DrawnFrame.Text> lit = text -> holes.stream().anyMatch(hole ->
                    hole[0] <= text.x() && text.x() < hole[2] && hole[1] <= text.y() && text.y() < hole[3]);
            // The reference, the parenthetical and the reference inside it each take the drop; the number none.
            assertEquals(List.of(3, true, true, false), List.of(holes.size(), lit.test(reference), lit.test(open),
                    lit.test(number)), "holes over the references and the parenthetical alone");
            assertTrue(frame.targets.drag(open.x() + 1, open.y() + 1));
            builder.frame();
            frame.targets.release();
            DrawnFrame placed = builder.frame();
            int multiplier = placed.buttonAround(placed.findFirst("1x"))[0];
            assertTrue(multiplier < placed.find("(").x(), "the parenthetical's 1x before its (");
            assertEquals(multiplier, numberBox(builder).getX(), "the box at the parenthetical's multiplier");
            Builder numbers = Builder.item(shown);
            DrawnFrame numbered = numbers.frame();
            DrawnFrame.Text five = numbered.findFirst("5");
            hold(numbers, numbered, "x", five.x() + 1, five.y() + 1);
            numbered.targets.release();
            numbers.frame();
            verify(numbers.actions, never()).focus(any(AbstractWidget.class));
            assertEquals(List.of(), numbers.written, "a number scaled");
        }

        @Test
        void aDropOffEveryTermOrEscapedScalesNothing() {
            Builder builder = Builder.item("$waxed + $edited");
            DrawnFrame frame = builder.frame();
            int pointerX = AREA.right() - 60;
            int pointerY = AREA.bottom() - 40;
            hold(builder, frame, "x", pointerX, pointerY);
            DrawnFrame held = builder.frame();
            assertTrue(held.drawn().stream().anyMatch(text -> text.text().equals("x")
                    && text.x() == pointerX + 8 + ButtonPainter.PADDING), "the x draws at the pointer");
            frame.targets.release();
            builder.frame();
            Builder escaped = Builder.item("$waxed + $edited");
            DrawnFrame second = escaped.frame();
            DrawnFrame.Text edited = second.find("$edited");
            hold(escaped, second, "/", edited.x() + 1, edited.y() + 1);
            assertTrue(escaped.pane.keyPressed(GLFW.GLFW_KEY_ESCAPE), "Escape drops the held /");
            second.targets.release();
            escaped.frame();
            for (Builder each : List.of(builder, escaped)) {
                verify(each.actions, never()).focus(any(AbstractWidget.class));
                assertEquals(List.of(), each.written, "a drop off every term or escaped scaled something");
            }
        }
    }

    /**
     * A whole-value (relative) expression takes every rule a type row's expression takes: each rule's assertion runs
     * through one helper against both kinds, and the two kinds draw the same geometry relative to the container
     * (decision whole-value-expressions-take-every-type-row-rule).
     */
    @Nested
    class WholeValueParity {
        private static final String TERM = "3 $waxed.metal / 2";
        private static final String SCALED = "3 ($base + $base) / 2 + $base";
        private static final String NESTED = "(($base) + $base) + $base";
        private static final String MANY = "$base + $base + $base + $base + $base + $base";
        private static final String THREE = "$base + $base + $base";
        private static final GroupsPane.Bounds NARROW = new GroupsPane.Bounds(0, 0, 200, 390);

        // The expression opened as a whole value, and as the amount of a metal row.
        private static List<Builder> bothKinds(String expression) {
            return List.of(Builder.item(expression), Builder.item("{\"metal\": \"" + expression + "\"}"));
        }

        // Where a sum's terms stand in each kind: the whole value's own, or the metal row's.
        private static List<List<Integer>> sums() {
            return List.of(List.of(), List.of(0));
        }

        // Rectangles relative to an origin, as left, top, right and bottom.
        private static List<List<Integer>> relative(List<int[]> rects, int left, int top) {
            return rects.stream().map(rect -> List.of(rect[0] - left, rect[1] - top, rect[2] - left, rect[3] - top))
                    .toList();
        }

        // A plain term's segments' frames, its ring's sides and its seams, relative to its ring's top-left.
        private static List<List<Integer>> termGeometry(DrawnFrame frame) {
            List<int[]> segments = SegmentedTerms.segments(frame);
            int[] ring = SegmentedTerms.ringOf(segments);
            List<int[]> pieces = new ArrayList<>(segments);
            pieces.addAll(fills(frame, ControlFlow.TERM_BORDER));
            pieces.addAll(fills(frame, ControlFlow.SEAM_LIGHT));
            pieces.addAll(fills(frame, ControlFlow.SEAM_DARK));
            return relative(pieces, ring[0], ring[1]);
        }

        @Test
        void aTermDrawsTheSameSegmentsRingAndSeamsInBothKinds() {
            List<List<List<Integer>>> geometry = bothKinds(TERM).stream().map(Builder::frame)
                    .map(WholeValueParity::termGeometry).toList();
            assertEquals(geometry.get(1), geometry.get(0), "the whole value's term against the type row's");
            Builder rows = Builder.item("{\"metal\": \"" + TERM + " + $base\"}");
            DrawnFrame frame = rows.frame();
            int[] rail = SegmentedTerms.segments(frame).getFirst();
            assertTrue(frame.targets.click(rail[0] + 2, rail[1] + 2), "the row's term rail takes no press");
            assertTrue(frame.targets.drag(AREA.right() - 40, frame.find("$base").y()));
            rows.frame();
            frame.targets.release();
            assertEquals("{\"metal\": \"$base + " + TERM + "\"}", rows.written.getLast(), "the row's term drags");
        }

        // A parenthetical's container with one clear pixel inside its border round "(", its terms and ")", its
        // parens regular weight and sized to its contents; answers its geometry relative to its border's top-left.
        private static List<List<Integer>> paddedParenthetical(DrawnFrame frame, List<Integer> sum) {
            int[] box = ParenContainers.border(frame);
            DrawnFrame.Placed open = frame.placed("(");
            DrawnFrame.Placed close = frame.placed(")");
            Lettering parens = new Lettering(DrawnFrame.font(), open.scale());
            double[] first = railOf(frame, placeOf(sum, 0, 0));
            int[] firstTerm = ringAround(frame.buttonAround((int) first[0], (int) first[1]));
            int[] lastTerm = ringAround(frame.buttonAround(frame.drawn().get(frame.drawn().indexOf(frame.find(")"))
                    - 1)));
            assertEquals(box[0] + 2, open.x(), "( a clear pixel inside the left border");
            assertEquals(open.x() + parens.inkWidth("(") + 1, firstTerm[0], "the first term a clear pixel past (");
            assertEquals(lastTerm[2] + 1, close.x(), ") a clear pixel past the last term");
            assertEquals(close.x() + parens.inkWidth(")") + 2, box[2], ") a clear pixel inside the right border");
            assertEquals(List.of(box[1] + 2, box[3] - 2), List.of(firstTerm[1], firstTerm[3]),
                    "the terms a clear pixel inside the top and bottom borders");
            assertFalse(open.bold() || close.bold() || open.shadow() || close.shadow(), "the parens draw plain");
            return relative(List.of(box, firstTerm, lastTerm, new int[] {open.x(), open.y(), close.x(), close.y()}),
                    box[0], box[1]);
        }

        // A list of indices: a sum's place, then the indices under it.
        private static List<Integer> placeOf(List<Integer> sum, Integer... under) {
            List<Integer> path = new ArrayList<>(sum);
            path.addAll(List.of(under));
            return path;
        }

        @Test
        void aParentheticalDrawsTheSamePaddedContainerInBothKinds() {
            List<Builder> kinds = bothKinds(SCALED);
            List<List<List<Integer>>> geometry = IntStream.range(0, 2).mapToObj(kind ->
                    paddedParenthetical(kinds.get(kind).frame(), sums().get(kind))).toList();
            assertEquals(geometry.get(1), geometry.get(0), "the whole value's parenthetical against the type row's");
        }

        @Test
        void aNestedParenthesisDrawsSmallerInBothKinds() {
            for (Builder kind : bothKinds(NESTED)) {
                DrawnFrame frame = kind.frame();
                assertTrue(placedAt(frame, "(", 0).scale() > placedAt(frame, "(", 1).scale(),
                        "the outer ( larger than the inner in " + frame.texts());
            }
        }

        // Wrapped lines of terms stand BUILDER_LINE_GAP apart, container to container.
        private static void assertLinesStandTheGapApart(DrawnFrame frame, List<Integer> sum) {
            List<int[]> rings = IntStream.range(0, 6).mapToObj(index -> railOf(frame, placeOf(sum, index)))
                    .map(rail -> ringAround(frame.buttonAround((int) rail[0], (int) rail[1]))).toList();
            List<Integer> tops = rings.stream().map(ring -> ring[1]).distinct().sorted().toList();
            assertTrue(tops.size() > 1, "the terms wrap: " + tops);
            int height = rings.getFirst()[3] - rings.getFirst()[1];
            for (int line = 1; line < tops.size(); line++) {
                assertEquals(ControlFlow.BUILDER_LINE_GAP, tops.get(line) - tops.get(line - 1) - height,
                        "the room between line " + (line - 1) + " and line " + line);
            }
        }

        @Test
        void wrappedLinesStandTheGapApartInBothKinds() {
            List<Builder> kinds = bothKinds(MANY);
            for (int kind = 0; kind < 2; kind++) {
                assertLinesStandTheGapApart(kinds.get(kind).frame(NARROW), sums().get(kind));
            }
        }

        // The list a term's name opens near the screen's bottom stands above the name's line.
        private static void assertListOpensAbove(Builder builder) {
            int top = DrawnFrame.SCREEN_HEIGHT - ControlFlow.LINE * 3;
            DrawnFrame frame = builder.frameAt(AREA, top);
            DrawnFrame.Text name = frame.findFirst("$base");
            int[] segment = frame.buttonAround(name);
            clickAt(frame, name);
            builder.frameAt(AREA, top);
            ArgumentCaptor<AbstractWidget> widgets = ArgumentCaptor.forClass(AbstractWidget.class);
            verify(builder.actions, atLeastOnce()).addWidget(widgets.capture());
            AbstractWidget search = widgets.getAllValues().stream().filter(widget -> widget.visible).findFirst()
                    .orElseThrow(() -> new AssertionError("no list opened"));
            assertTrue(search.getY() + search.getHeight() <= segment[1], "the list's search box at " + search.getY()
                    + " stands above the name's line at " + segment[1]);
        }

        @Test
        void aListOpenedNearTheBottomOpensAboveInBothKinds() {
            bothKinds(THREE).forEach(WholeValueParity::assertListOpensAbove);
        }

        // A drag to the slot before a sum's third term draws its cursor exactly as tall as the term's ringed
        // container, the mask's hole a pixel round it.
        private static void assertCursorSpansTheContainer(Builder builder, List<Integer> sum) {
            DrawnFrame frame = builder.frame();
            double[] first = railOf(frame, placeOf(sum, 0));
            double[] third = railOf(frame, placeOf(sum, 2));
            int[] ring = ringAround(frame.buttonAround((int) third[0], (int) third[1]));
            assertTrue(frame.targets.click(first[0], first[1]));
            assertTrue(frame.targets.drag(containerLeft(frame, third) - 3, third[1]));
            DrawnFrame held = builder.frame();
            List<int[]> cursors = fills(held, ExpressionBuilderPane.DROP_CURSOR);
            assertEquals(1, cursors.size(), "one cursor");
            assertEquals(List.of(ring[1], ring[3]), List.of(cursors.getFirst()[1], cursors.getFirst()[3]),
                    "the cursor as tall as the term's ringed container");
            assertHoleStandsAPixelAround(held, cursors.getFirst());
            frame.targets.release();
        }

        @Test
        void theDropCursorSpansTheContainerBesideItInBothKinds() {
            List<Builder> kinds = bothKinds(THREE);
            for (int kind = 0; kind < 2; kind++) {
                assertCursorSpansTheContainer(kinds.get(kind), sums().get(kind));
            }
        }
    }

    // The index-th placement of a text, in drawing order.
    private static DrawnFrame.Placed placedAt(DrawnFrame frame, String label, int index) {
        return frame.placements().stream().filter(placed -> placed.text().equals(label)).toList().get(index);
    }

    /**
     * Every term's container, the first included, carries its sign's segment right after its rail, so any term flips
     * negative in place and drags with its sign (decision every-term-carries-its-sign-as-a-segment, and the
     * operator's ruling of 2026-11-04 placing the rail left of the sign).
     */
    @Nested
    class SignOnEveryTerm {
        private static final String SUM = "$waxed + $edited";

        // The sign drawn nearest right of a term's rail on its line, whose segment shares its left border with the
        // rail's segment.
        private static DrawnFrame.Text signBefore(DrawnFrame frame, double[] rail) {
            DrawnFrame.Text sign = frame.drawn().stream().filter(text -> (text.text().equals("+")
                    || text.text().equals("-")) && text.x() > rail[0] && Math.abs(text.y() - rail[1]) < HALF_LINE)
                    .min(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                    .orElseThrow(() -> new AssertionError("no sign after the rail at " + rail[0]));
            assertEquals(frame.buttonAround((int) rail[0], (int) rail[1])[2], frame.buttonAround(sign)[0],
                    "the sign's segment abuts the rail's, the seam between them");
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
         * Two goo type rows stand apart, the pulse row's rail segment the line gap and the button gap below the metal
         * row's, so no row's elements touch the next's, the row pitch below the 34 px it was (decisions
         * type-row-rail-sits-on-the-goo-type-button-with-no-box, type-rows-stand-apart-in-the-builder, and the
         * operator's ruling of 2026-11-04 restoring the gaps between rows).
         */
        @Test
        void typeRowsStandTheLineAndButtonGapsApart() {
            DrawnFrame frame = Builder.item(TWO_TYPES).frame();
            // Each row's tallest piece, its term's ringed container.
            List<int[]> rows = List.of("5", "2").stream().map(amount -> ringAround(frame.buttonAround(
                    frame.findFirst(amount)))).toList();
            assertEquals(ControlFlow.BUILDER_LINE_GAP + RowLayout.BUTTON_GAP, rows.get(1)[1] - rows.get(0)[3],
                    "the clear room between rows");
            List<DrawnFrame.Text> colons = frame.drawn().stream().filter(text -> text.text().equals(":")).toList();
            int colonX = colons.stream().mapToInt(DrawnFrame.Text::x).min().orElseThrow();
            List<Integer> rowColons = colons.stream().filter(colon -> colon.x() == colonX).map(DrawnFrame.Text::y)
                    .toList();
            assertTrue(rowColons.get(1) - rowColons.get(0) < 34, "the row pitch " + rowColons);
        }
    }

    /**
     * A term dragged to the slot after a parenthetical wrapping onto two lines draws its drop cursor exactly from that
     * parenthetical container's top to its bottom, and the mask's hole there stands one pixel around the cursor
     * (decisions drop-indicators-size-to-the-container-they-land-beside,
     * mask-holes-fit-the-indicator-and-the-layer-interior).
     */
    @Test
    void dropCursorAndHoleSpanTheParentheticalTheySitBeside() {
        Builder builder = Builder.item("$edited + ($waxed + $edited + $waxed + $edited + $waxed) + $base");
        GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 330, 390);
        DrawnFrame frame = builder.frame(narrow);
        DrawnFrame.Text open = frame.find("(");
        DrawnFrame.Text close = frame.find(")");
        assertTrue(close.y() > open.y() + HALF_LINE, "the parenthetical wraps onto a second line");
        // The parenthetical's container, its border's top to its bottom.
        int[] parenthetical = ParenContainers.border(frame);
        double[] first = railOf(frame, List.of(0));
        assertTrue(frame.targets.click(first[0], first[1]));
        // Just past the container, which draws no ratio at 1x (decision
        // one-x-is-implied-so-an-unscaled-term-draws-no-ratio).
        int afterClose = parenthetical[2] + 2;
        assertTrue(frame.targets.drag(afterClose, close.y() + HALF_LINE));
        DrawnFrame held = builder.frame(narrow);
        List<int[]> cursors = fills(held, ExpressionBuilderPane.DROP_CURSOR);
        assertEquals(1, cursors.size(), "one cursor");
        int[] cursor = cursors.getFirst();
        assertTrue(cursor[3] - cursor[1] > ControlFlow.LINE, "the cursor spans more than one line");
        assertEquals(List.of(parenthetical[1], parenthetical[3]), List.of(cursor[1], cursor[3]),
                "the cursor spans the parenthetical's container");
        assertHoleStandsAPixelAround(held, cursor);
        frame.targets.release();
    }

    // The mask's hole at the cursor is the cursor grown one pixel each way, centered on it (decision
    // mask-holes-fit-the-indicator-and-the-layer-interior).
    private static void assertHoleStandsAPixelAround(DrawnFrame frame, int[] cursor) {
        int[] hole = fills(frame, DragMask.HIGHLIGHT).stream().filter(lit -> lit[0] <= cursor[0] && cursor[2] <= lit[2]
                && lit[1] <= cursor[1] && cursor[3] <= lit[3]).findFirst()
                .orElseThrow(() -> new AssertionError("no hole at the cursor " + java.util.Arrays.toString(cursor)));
        assertEquals(List.of(cursor[0] - 1, cursor[1] - 1, cursor[2] + 1, cursor[3] + 1),
                java.util.Arrays.stream(hole).boxed().toList(), "the hole one pixel around the cursor");
    }

    /**
     * A term dragged to the slot before another plain term draws its drop cursor exactly as tall as that term's
     * segments, its rail segment's top to its bottom, and the mask's hole there stands one pixel around the cursor
     * (decision mask-holes-fit-the-indicator-and-the-layer-interior).
     */
    @Test
    void dropCursorSpansTheTermsSegmentsAndItsHoleAPixelAround() {
        Builder builder = Builder.item("$waxed + $edited + $base");
        DrawnFrame frame = builder.frame();
        double[] waxed = railOf(frame, List.of(0));
        double[] base = railOf(frame, List.of(2));
        int[] baseRail = frame.buttonAround((int) base[0], (int) base[1]);
        assertTrue(frame.targets.click(waxed[0], waxed[1]));
        assertTrue(frame.targets.drag(containerLeft(frame, base) - 3, base[1]));
        DrawnFrame held = builder.frame();
        List<int[]> cursors = fills(held, ExpressionBuilderPane.DROP_CURSOR);
        assertEquals(1, cursors.size(), "one cursor");
        int[] cursor = cursors.getFirst();
        // The term's container: its rail's frame grown by the pixel its ring adds above and below (decision
        // term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
        assertEquals(List.of(baseRail[1] - 1, baseRail[3] + 1), List.of(cursor[1], cursor[3]),
                "the cursor as tall as the term's ringed container");
        assertHoleStandsAPixelAround(held, cursor);
        frame.targets.release();
        assertEquals("$edited + $waxed + $base", builder.written.getLast());
    }

    /**
     * A parenthetical's "(" and ")" draw larger than the term text beside them and regular weight, never bold (the
     * operator's ruling of 2026-11-04).
     */
    @Test
    void parenthesesDrawLargerThanTheirTermsAndRegularWeight() {
        DrawnFrame frame = Builder.item("($waxed + $edited) + $base").frame();
        float term = frame.placed("$waxed").scale();
        for (String paren : List.of("(", ")")) {
            DrawnFrame.Placed drawn = frame.placed(paren);
            assertFalse(drawn.bold(), paren + " draws bold");
            // The operator's ruling of 2026-11-04: the shadow under a paren looks wrong.
            assertFalse(drawn.shadow(), paren + " draws a drop shadow");
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
            int count = 14;
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
     * A per-type prefab's 's draws inside its word's segment, flush after the word (decision
     * possessive-sits-flush-against-its-word-button, and the operator's ruling of 2026-11-04 moving the 's into the
     * name's segment).
     */
    @Test
    void possessiveSitsFlushAgainstItsWordButton() {
        DrawnFrame frame = Builder.item("{\"jelly\": \"$food.jelly\"}").frame();
        DrawnFrame.Text food = frame.find("$food");
        DrawnFrame.Text possessive = frame.find("'s");
        assertArrayEquals(frame.buttonAround(food), frame.buttonAround(possessive), "the 's in the word's segment");
        assertEquals(food.x() + LETTERS.width(food.text()), possessive.x(), frame.drawn().toString());
    }

    private static void assertRests(DrawnFrame frame) {
        assertEquals(1, fills(frame, ExpressionBuilderPane.TRASH_REST).size(), "the strip rests");
        assertEquals(List.of(), fills(frame, ExpressionBuilderPane.TRASH_LIT), "the strip lit");
        assertEquals(List.of(), fills(frame, ExpressionBuilderPane.TRASH_HOVER), "the strip brightened");
    }

    private static final String TWO_TYPES = "{\"metal\": \"5\", \"pulse\": \"2\"}";

    /**
     * The [+?] button's border, which opens the line [+?] () stands on, draws at the first type row's goo type button
     * left, its rail segment's, in the one column the rows share (decisions
     * plus-question-and-equals-align-with-the-other-lines, type-row-rail-sits-on-the-goo-type-button-with-no-box).
     */
    @Test
    void plusQuestionStandsAtTheTypeRowsLeft() {
        DrawnFrame frame = Builder.item(TWO_TYPES).frame();
        double[] rail = rowRailOf(frame, 0);
        int rowLeft = frame.buttonAround((int) rail[0], (int) rail[1])[0];
        assertEquals(rowLeft, hoverCorner(frame, BuilderOverlays.hintKey("Add a goo type"))[0],
                "the +? border at the row's left " + rowLeft);
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
            assertEquals(typeLeft + ControlFlow.INDENT, containerLeft(frame, parentheticalRail),
                    frame.drawn().toString());
            assertEquals(lineOf(frame, minus.y()), lineOf(frame, (int) parentheticalRail[1]));
            assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("(") && onOneLine(text, minus)),
                    frame.drawn().toString());
            // A wrap inside the parenthetical opens one indent further in than the parenthetical's line.
            int innerLine = lineOf(frame, minus.y()) + 1;
            int parentheticalLeft = containerLeft(frame, parentheticalRail);
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
            // Decision multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after: saturation's 12 reads 12x.
            assertEquals(List.of(":", "+", "$food", "'s", "+", "12x", "$saturation", "'s"),
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
            assertEquals(List.of(":", "+", "$dye", "'s", "-", "(", "+", "$food"),
                    lineTexts(frame, head).subList(0, 8), frame.drawn().toString());
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
            // The bay's line is the builder's last, so Cancel stands at its right end past the bay's buttons
            // (decision whole-value-ratio-draws-after-the-last-term-unlabelled moved the ratio off a line of its own).
            List<DrawnFrame.Text> bay = frame.drawn().stream().filter(text -> text.y() == handle.y()
                    && !text.text().equals("✗ Cancel")).sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                    .toList();
            // Decision x-and-slash-drag-from-the-bay-onto-a-term: the x and / stand after the + and -.
            assertEquals(List.of("()", "+", "-", "x", "/"), bay.stream().map(DrawnFrame.Text::text).toList(),
                    bay.toString());
            assertEquals(0, frame.count(SUM_ADD), frame.texts().toString());
            DrawnFrame.Text minus = bay.get(2);
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
            clickAt(frame, frame.findFirst("+"));
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
        DrawnFrame frame = Builder.item("3 $waxed / 2").frame();
        DrawnFrame.Text multiplier = frame.findFirst("3x");
        DrawnFrame.Text divisor = frame.findFirst("/2");
        assertEquals(BuilderOverlays.hintKey("Multiplier"),
                frame.targets.hovered(multiplier.x() + 1, multiplier.y() + 1).orElseThrow());
        assertEquals(BuilderOverlays.hintKey("Divisor"),
                frame.targets.hovered(divisor.x() + 1, divisor.y() + 1).orElseThrow());
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
     * 1x is implied: an unscaled term draws no factor, the sum no trailing ratio; a multiplier reads Nx between the
     * sign and the identity, a divisor /n after it, each a click-to-edit segment; a bay drop on a side at 1 draws
     * that side's segment at 1 for as long as its number box stands open (decisions
     * one-x-is-implied-so-an-unscaled-term-draws-no-ratio, multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after,
     * x-and-slash-drag-from-the-bay-onto-a-term).
     */
    @Nested
    class ImpliedOne {

        // The texts the builder's term lines draw: everything but the kind's line and the bay's.
        private static List<String> termTexts(DrawnFrame frame) {
            int bay = handles(frame).getFirst().y();
            return frame.drawn().subList(1, frame.drawn().size()).stream().filter(text -> text.y() != bay)
                    .map(DrawnFrame.Text::text).toList();
        }

        private static void assertNoFactor(DrawnFrame frame) {
            List<String> texts = termTexts(frame);
            assertEquals(List.of(), texts.stream().filter(text -> text.equals("1") || text.equals(":")
                    || text.endsWith("x") || text.startsWith("/")).toList(), "a factor drawn in " + texts);
        }

        @Test
        void unscaledTermsDrawNoFactorAndTheSumNoTrailingRatio() {
            assertNoFactor(Builder.item("$bush + $magic + $wealth").frame());
            assertNoFactor(Builder.item("($food + $saturation)").frame());
        }

        // The segment holding a text, its left and right.
        private static int[] segmentOf(DrawnFrame frame, String label) {
            return frame.buttonAround(frame.findFirst(label));
        }

        @Test
        void aMultiplierReadsNxBetweenTheSignAndTheIdentity() {
            DrawnFrame frame = Builder.item("4 $food + 24 $saturation").frame();
            for (List<String> term : List.of(List.of("4x", "$food", "0"), List.of("24x", "$saturation", "1"))) {
                int[] factor = segmentOf(frame, term.get(0));
                DrawnFrame.Text name = frame.findFirst(term.get(1));
                DrawnFrame.Text sign = frame.drawn().stream().filter(text -> text.text().equals("+")
                        && text.x() < name.x()).max(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                        .orElseThrow();
                assertEquals(List.of(frame.buttonAround(sign)[2], frame.buttonAround(name)[0]),
                        List.of(factor[0], factor[2]), term.get(0) + " between the sign and the name");
            }
            assertTrue(frame.texts().stream().noneMatch(text -> text.startsWith("/") && text.length() > 1),
                    "a divisor drawn in " + frame.texts());
        }

        @Test
        void aDivisorReadsSlashNAfterTheIdentityOrTheCloseParen() {
            DrawnFrame frame = Builder.item("3 $plank / 4").frame();
            int[] multiplier = segmentOf(frame, "3x");
            int[] plank = frame.buttonAround(frame.find("$plank"));
            int[] divisor = segmentOf(frame, "/4");
            assertEquals(List.of(multiplier[2], plank[2]), List.of(plank[0], divisor[0]), "3x, $plank, /4 in order");
            DrawnFrame nested = Builder.item("($medium - $food.jelly * 4) / 2").frame();
            assertTrue(nested.findFirst("/2").x() > nested.find(")").x(), "/2 after the close paren");
        }

        @Test
        void aFactorSegmentOpensItsBoxAndHoversItsName() {
            Builder builder = Builder.item("3 $plank / 4");
            DrawnFrame frame = builder.frame();
            DrawnFrame.Text multiplier = frame.findFirst("3x");
            DrawnFrame.Text divisor = frame.findFirst("/4");
            assertEquals(BuilderOverlays.hintKey("Multiplier"), frame.targets.hovered(multiplier.x() + 1,
                    multiplier.y() + 1).orElseThrow());
            assertEquals(BuilderOverlays.hintKey("Divisor"), frame.targets.hovered(divisor.x() + 1, divisor.y() + 1)
                    .orElseThrow());
            clickAt(frame, multiplier);
            assertEquals("3", FactorBay.numberBox(builder).getValue());
            builder.commitNumber("5");
            assertEquals("5 $plank / 4", builder.written.getLast());
        }

        @Test
        void aBayDropOnASideAtOneDrawsItsSegmentWhileItsBoxStandsOpen() {
            for (List<String> side : List.of(List.of("x", "1x", "3x", "$bush + 3 $magic"),
                    List.of("/", "/1", "/3", "$bush + $magic / 3"))) {
                Builder builder = Builder.item("$bush + $magic");
                EditBox box = FactorBay.dropOn(builder, side.get(0), "$magic");
                DrawnFrame open = builder.frame();
                int[] segment = segmentOf(open, side.get(1));
                int[] name = open.buttonAround(open.find("$magic"));
                assertEquals(side.get(0).equals("x") ? name[0] : name[2], side.get(0).equals("x") ? segment[2]
                        : segment[0], side.get(1) + " beside $magic's name");
                assertEquals(List.of("1", "1", segment[0]), List.of(box.getValue(), box.getHighlighted(), box.getX()),
                        "the box open on " + side.get(1) + ", its 1 selected");
                builder.commitNumber("3");
                assertTrue(builder.frame().texts().contains(side.get(2)), side.get(2) + " stays");
                assertEquals(side.get(3), builder.written.getLast());
            }
        }

        // Decision factor-left-at-one-or-set-to-one-or-zero-erases-its-segment: a drop's box closed at 1, by Enter,
        // by a click elsewhere or by Escape, leaves the term as it stood.
        @Test
        void aDropsBoxClosedAtOneLeavesTheTermAsItStood() {
            for (int close = 0; close < 3; close++) {
                Builder builder = Builder.item("$bush + $magic");
                EditBox box = FactorBay.dropOn(builder, "x", "$magic");
                if (close == 0) {
                    builder.commitNumber("1");
                } else if (close == 1) {
                    focusFlag(box, true);
                    builder.frame();
                    focusFlag(box, false);
                } else {
                    assertTrue(builder.pane.keyPressed(GLFW.GLFW_KEY_ESCAPE), "Escape closes the box");
                }
                builder.frame();
                DrawnFrame after = builder.frame();
                assertFalse(after.texts().contains("1x"), "close " + close + " left 1x in " + after.texts());
                assertEquals(List.of(), builder.written, "close " + close + " changed the text");
            }
        }

        // EditBox.setFocused reaches the game client, which a unit test holds none of, so the flag is set directly, as
        // a click elsewhere clears it.
        private static void focusFlag(EditBox box, boolean focused) {
            try {
                java.lang.reflect.Field flag = AbstractWidget.class.getDeclaredField("focused");
                flag.setAccessible(true);
                flag.setBoolean(box, focused);
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        // Decision factor-left-at-one-or-set-to-one-or-zero-erases-its-segment: a standing side committed at 0 or 1
        // goes, its segment with it.
        @Test
        void aStandingSideSetToZeroOrOneIsErased() {
            Builder builder = Builder.item("3 $plank / 4");
            DrawnFrame frame = builder.frame();
            clickAt(frame, frame.findFirst("3x"));
            builder.commitNumber("0");
            assertEquals("$plank / 4", builder.written.getLast());
            DrawnFrame divided = builder.frame();
            assertFalse(divided.texts().stream().anyMatch(text -> text.endsWith("x") && text.length() > 1),
                    "a multiplier segment in " + divided.texts());
            clickAt(divided, divided.findFirst("/4"));
            builder.commitNumber("1");
            assertEquals("$plank", builder.written.getLast());
            assertFalse(builder.frame().texts().contains("/4"), "the divisor segment stays");
        }

        // Decision ratio-holds-multiplier-and-divisor-together: the divisor's box sets the divisor alone.
        @Test
        void committingTheDivisorKeepsTheMultiplier() {
            Builder builder = Builder.item("2 $iron_ingot / 3");
            DrawnFrame frame = builder.frame();
            clickAt(frame, frame.findFirst("/3"));
            builder.commitNumber("4");
            assertEquals("2 $iron_ingot / 4", builder.written.getLast());
            List<String> texts = builder.frame().texts();
            assertTrue(texts.contains("2x") && texts.contains("/4"), texts.toString());
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
            FactorBay.dropOn(builder, "x", "$waxed");
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
