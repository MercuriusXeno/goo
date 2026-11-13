package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the one reveal primitive every pane follows through (decision
 * one-reveal-primitive-centers-before-the-first-draw).
 */
class RevealTest {
    private static final int ROW = 20;
    private static final int ROWS = 30;
    private static final int VIEW = 100;
    private static final int TARGET = 12;
    private static final int RIGHT = 50;

    /**
     * A follow clears the search at once; centering then measures the rows under a scissor holding no room, centers
     * the target through its bar and starts its glow at age zero, in that order, and with nothing held measures
     * nothing.
     */
    @Test
    void clearsThenMeasuresThenCentersThenGlows() {
        List<String> order = new ArrayList<>();
        Reveal reveal = new Reveal();
        ScrollBar bar = new ScrollBar(ScrollBar.WHEEL_STEP);
        RevealGlow glow = new RevealGlow();
        reveal.follow("row" + TARGET, () -> order.add("clear"));
        DrawnFrame frame = new DrawnFrame();
        reveal.centerBeforeDraw(frame.graphics, scratch -> {
            order.add("measure at " + bar.offset());
            for (int index = 0; index < ROWS; index++) {
                frame.graphics.text(DrawnFrame.font(), "row" + index, 0, index * ROW, Palette.TEXT);
                reveal.laid("row" + index, index * ROW, ROW, 0);
            }
            bar.measure(ROWS * ROW, VIEW);
        }, column -> bar, glow);
        ScrollBar expected = new ScrollBar(ScrollBar.WHEEL_STEP);
        expected.measure(ROWS * ROW, VIEW);
        expected.center(TARGET * ROW, ROW);
        assertEquals(List.of("clear", "measure at 0"), order);
        assertEquals(expected.offset(), bar.offset(), "centered");
        assertEquals(List.of(), frame.texts(), "the measuring pass reached no screen");
        glow.draw(frame.graphics, 0, 0, RIGHT);
        assertEquals(List.of(List.of(0, TARGET * ROW, RIGHT, TARGET * ROW + ROW)),
                frame.fillsOf(RevealGlow.colorAt(0)).stream().map(fill -> List.of(fill[0], fill[1], fill[2], fill[3]))
                        .toList(), "the glow at age zero on the target");
        order.clear();
        reveal.centerBeforeDraw(frame.graphics, scratch -> order.add("measure"), column -> bar, glow);
        assertTrue(order.isEmpty(), "nothing held measures nothing");
    }

    /**
     * A target no row lays, as the row a create asked for before its reply lists it, stays held: the next measuring
     * pass that lays it centers it (decision create-reveals-the-new-row-like-a-follow).
     */
    @Test
    void aTargetNoRowLaysStaysHeldUntilAPassLaysIt() {
        Reveal reveal = new Reveal();
        ScrollBar bar = new ScrollBar(ScrollBar.WHEEL_STEP);
        RevealGlow glow = new RevealGlow();
        DrawnFrame frame = new DrawnFrame();
        reveal.follow("row" + TARGET, () -> { });
        reveal.centerBeforeDraw(frame.graphics, scratch -> bar.measure(ROWS * ROW, VIEW), column -> bar, glow);

        reveal.centerBeforeDraw(frame.graphics, scratch -> {
            for (int index = 0; index < ROWS; index++) {
                reveal.laid("row" + index, index * ROW, ROW, 0);
            }
            bar.measure(ROWS * ROW, VIEW);
        }, column -> bar, glow);

        ScrollBar expected = new ScrollBar(ScrollBar.WHEEL_STEP);
        expected.measure(ROWS * ROW, VIEW);
        expected.center(TARGET * ROW, ROW);
        assertEquals(expected.offset(), bar.offset(), "centered once a pass lays it");
    }
}
