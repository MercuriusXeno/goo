package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that clickable runs in the builder draw as a bordered button and plain runs as none, with mocked graphics
 * and no Screen (decision blue-text-options-draw-as-bordered-buttons).
 */
class ControlFlowTest {
    private static final List<ExpressionLines.Run> REF = List.of(new ExpressionLines.Run(ExpressionLines.Kind.LINK,
            "waxed", "waxed"));

    private static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * A pressed run lays the painter's border, then its inner fill, and a target covering the whole box.
     */
    @Test
    void pressedRunsDrawAsAButton() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ClickTargets targets = new ClickTargets();
        boolean[] pressed = {false};
        new ControlFlow(graphics, font(), targets, null, 0, 400, 0).runs(REF, null, at -> pressed[0] = true);
        int width = "waxed".length() * 6 + 2 * ButtonPainter.PADDING;
        verify(graphics).fill(0, 2, width, 2 + ButtonPainter.HEIGHT, Palette.BORDER);
        verify(graphics).fill(1, 3, width - 1, 1 + ButtonPainter.HEIGHT, Palette.FILL);
        assertTrue(targets.click(width - 1, 2 + ButtonPainter.HEIGHT - 1));
        assertTrue(pressed[0]);
    }

    /**
     * A parenthetical's box draws Frame's nest role in its depth's tone, and a term's container closes on Frame's 2 px
     * term ring round its segments (decision one-frame-primitive-draws-every-bordered-box).
     */
    @Test
    void nestBoxAndTermRingDrawTheirFrameRoles() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ControlFlow flow = new ControlFlow(graphics, font(), new ClickTargets(), null, 0, 400, 0);
        flow.boxed(new GroupsPane.Bounds(0, 0, 40, 20), Palette.NEST_DARKER);
        verify(graphics).fill(0, 0, 40, 1, Frame.NEST.border());
        verify(graphics).fill(1, 1, 39, 19, Palette.NEST_DARKER);
        flow.openTerm(ControlFlow.ringedWidth(ButtonPainter.width(font(), "x")), ButtonPainter.HEIGHT);
        flow.labelSegment("x", null, at -> { });
        GroupsPane.Bounds term = flow.closeSegments();
        verify(graphics).fill(term.left(), term.top(), term.right(), term.top() + Frame.TERM.thickness(),
                Frame.TERM.border());
    }

    // A grab recording every place the pointer handed it.
    private static final class RecordingGrab implements ClickTargets.PointerGrab {
        final List<List<Double>> moves = new ArrayList<>();

        @Override
        public void moved(double x, double y) {
            moves.add(List.of(x, y));
        }

        @Override
        public void released() {
        }
    }

    // A box drawn a pixel inside another, as left, top, right and bottom.
    private static List<Integer> inset(List<Integer> box) {
        return List.of(box.get(0) + 1, box.get(1) + 1, box.get(2) - 1, box.get(3) - 1);
    }

    /**
     * A container's rail segment paints its rail filling the segment a pixel inside its border, once the frame
     * knows its thing may drop (decisions rails-fill-the-left-edge-of-their-box,
     * containers-segment-into-click-to-edit-buttons).
     */
    @Test
    void aRailSegmentPaintsItsRailFillingTheSegmentInsideItsBorder() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ControlFlow flow = new ControlFlow(graphics, font(), new ClickTargets(), null, 0, 400, 0);
        flow.openSegments(ControlFlow.RAIL_SEGMENT_WIDTH, ButtonPainter.HEIGHT);

        flow.railSegment("term", new RecordingGrab());
        flow.paintRails(name -> true);

        assertEquals(inset(RailsTest.fillsIn(graphics, Palette.BORDER).getFirst()),
                RailsTest.fillsIn(graphics, Palette.RAIL).getFirst());
    }

    /**
     * A grab control draws its rail flush inside its left border, and a press anywhere on it hands the pointer to
     * its grab (decisions parenthetical-handle-drags-onto-a-term,
     * parenthetical-handle-carries-a-rail-and-hovers-its-drop).
     */
    @Test
    void aGrabControlDrawsItsRailInsideItsBorderAndHandsItsPressToTheGrab() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ClickTargets targets = new ClickTargets();
        RecordingGrab grab = new RecordingGrab();

        new ControlFlow(graphics, font(), targets, null, 0, 400, 0).grabControl("()", grab, null);

        List<Integer> border = RailsTest.fillsIn(graphics, Palette.BORDER).getFirst();
        List<Integer> rail = RailsTest.fillsIn(graphics, Palette.RAIL).getFirst();
        assertEquals(List.of(border.get(0) + 1, border.get(1) + 1, border.get(3) - 1),
                List.of(rail.get(0), rail.get(1), rail.get(3)), "the rail flush inside the border");
        double x = border.get(2) - 1.0;
        double y = border.get(1) + 1.0;
        assertTrue(targets.click(x, y), "the press took");
        assertEquals(List.of(List.of(x, y)), grab.moves);
    }

    /**
     * A type-icon button's border is the icon plus one pixel each side, and the next piece starts a gap past it
     * (decision type-icon-buttons-shrink-their-padding).
     */
    @Test
    void typeIconButtonIsTheIconPlusAPixelEachSide() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ControlFlow flow = new ControlFlow(graphics, font(), new ClickTargets(), null, 0, 400, 0);
        flow.typeIcon("metal", at -> { });
        int width = GooTypeIcons.sizeOf("metal") + 2;
        verify(graphics).fill(0, 0, width, ControlFlow.FULL_LINE, Palette.BORDER);
        assertEquals(width + BuilderOverlays.GAP, flow.column());
    }

    /**
     * A ref term's runs segment on the flow's first line hands its click the segment's own left and that line's top,
     * however many lines the flow drew after it (decision
     * diagnose-and-correct-segment-popups-anchoring-at-the-box-bottom).
     */
    @Test
    void runsSegmentAnchorsAtItsOwnLineAfterLaterLines() {
        ClickTargets targets = new ClickTargets();
        ControlFlow flow = new ControlFlow(mock(GuiGraphicsExtractor.class), font(), targets, null, 0, 400, 0);
        flow.control("lead", null, at -> { });
        int segmentLeft = flow.column();
        List<BuilderOverlays.Anchor> pressed = new ArrayList<>();
        flow.openSegments(100, ControlFlow.FULL_LINE);
        flow.runsSegment(REF, "", pressed::add);
        flow.closeSegments();
        drawTwoMoreLines(flow);
        assertTrue(targets.click(segmentLeft + 1, 1));
        assertEquals(List.of(List.of(segmentLeft, 0)), pressed.stream().map(at -> List.of(at.x(), at.y())).toList());
    }

    /**
     * A goo type segment on the flow's first line hands its click the segment's own left and that line's top, however
     * many lines the flow drew after it (decision diagnose-and-correct-segment-popups-anchoring-at-the-box-bottom).
     */
    @Test
    void typeSegmentAnchorsAtItsOwnLineAfterLaterLines() {
        ClickTargets targets = new ClickTargets();
        ControlFlow flow = new ControlFlow(mock(GuiGraphicsExtractor.class), font(), targets, null, 0, 400, 0);
        flow.control("lead", null, at -> { });
        int segmentLeft = flow.column();
        List<BuilderOverlays.Anchor> pressed = new ArrayList<>();
        flow.openSegments(ControlFlow.typeSegmentWidth("metal"), ControlFlow.FULL_LINE);
        flow.typeSegment("metal", pressed::add);
        flow.closeSegments();
        drawTwoMoreLines(flow);
        assertTrue(targets.click(segmentLeft + 1, 1));
        assertEquals(List.of(List.of(segmentLeft, 0)), pressed.stream().map(at -> List.of(at.x(), at.y())).toList());
    }

    /**
     * A term's segment hands its click the top and bottom edges of the term's drawn ring, so a popup stands flush
     * against the term rather than a full-size line under its line's top (decision
     * every-popup-measures-flows-to-fit-and-scrolls).
     */
    @Test
    void termSegmentAnchorsAtItsRingsEdges() {
        DrawnFrame frame = new DrawnFrame();
        ClickTargets targets = frame.targets;
        ControlFlow flow = ControlFlow.lettered(frame.graphics, DrawnFrame.font(), targets, null,
                new ControlFlow.Span(0, 400, 20), null);
        List<BuilderOverlays.Anchor> pressed = new ArrayList<>();
        flow.openTerm(ControlFlow.ringedWidth(100), ControlFlow.LINE);
        flow.runsSegment(REF, "", pressed::add);
        GroupsPane.Bounds ring = flow.closeSegments();
        assertTrue(targets.click(ring.left() + 4, ring.top() + 4));
        assertEquals(List.of(ring.top(), ring.bottom()), List.of(pressed.getFirst().y(), pressed.getFirst().bottom()));
    }

    /**
     * A runs segment draws its suffix inside the segment, flush after its runs, as a member's 's sits against its
     * word (decision possessive-sits-flush-against-its-word-button).
     */
    @Test
    void aRunsSegmentDrawsItsSuffixFlushAfterItsRunsInsideTheSegment() {
        DrawnFrame frame = new DrawnFrame();
        ControlFlow flow = ControlFlow.lettered(frame.graphics, DrawnFrame.font(), frame.targets, null,
                new ControlFlow.Span(0, 400, 20), null);
        flow.openSegments(200, ControlFlow.LINE);
        flow.runsSegment(REF, "'s", at -> { });
        flow.closeSegments();
        DrawnFrame.Text word = frame.find("waxed");
        DrawnFrame.Text possessive = frame.find("'s");
        assertEquals(List.of(List.of(frame.buttonAround(word)[0], frame.buttonAround(word)[2]),
                        word.x() + new Lettering(DrawnFrame.font(), ControlFlow.BUILDER_SCALE).width("waxed")),
                List.of(List.of(frame.buttonAround(possessive)[0], frame.buttonAround(possessive)[2]), possessive.x()),
                "the 's in the word's segment, flush after it");
    }

    private static void drawTwoMoreLines(ControlFlow flow) {
        flow.newLine();
        flow.control("second", null, at -> { });
        flow.newLine();
        flow.control("third", null, at -> { });
    }

    /**
     * An exclusive set draws one button reading the chosen option; its click lists every option under it, and a pick
     * runs that option and closes the list (decision exclusive-choice-sets-draw-as-dropdowns).
     */
    @Test
    void choicesDrawOneButtonWhoseClickListsTheSet() {
        BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        List<String> picked = new ArrayList<>();
        List<BuilderOverlays.Option> options = List.of(
                new BuilderOverlays.Option("north", "go north", at -> picked.add("north")),
                new BuilderOverlays.Option("south", "go south", at -> picked.add("south")));
        DrawnFrame closed = new DrawnFrame();
        new ControlFlow(closed.graphics, font(), closed.targets, null, 0, 400, 0).choices(options, 1, overlays);
        assertEquals(List.of(ControlFlow.dropdownLabel("south")), closed.texts());
        assertEquals(List.of(BuilderOverlays.hintKey("go south")), closed.targets.hoverKeys());
        closed.click(ControlFlow.dropdownLabel("south"));
        DrawnFrame open = new DrawnFrame();
        overlays.draw(open.graphics, font(), open.targets);
        assertEquals(List.of("north", "south"), open.texts());
        assertTrue(open.targets.hoverKeys().containsAll(List.of(BuilderOverlays.hintKey("go north"),
                BuilderOverlays.hintKey("go south"))));
        open.click("north");
        assertEquals(List.of("north"), picked);
        assertFalse(overlays.listOpen());
    }
}
