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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private static void drawTwoMoreLines(ControlFlow flow) {
        flow.newLine();
        flow.control("second", null, at -> { });
        flow.newLine();
        flow.control("third", null, at -> { });
    }

    /**
     * A run that takes no click lays no box and no target.
     */
    @Test
    void unpressedRunsDrawNoButton() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ClickTargets targets = new ClickTargets();
        new ControlFlow(graphics, font(), targets, null, 0, 400, 0).runs(REF, null, null);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(Palette.BORDER));
        assertFalse(targets.click(1, 5));
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
