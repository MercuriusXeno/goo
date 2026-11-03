package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Draws a tooltip line of runs on one line, goo types as their icons beside numbers and symbols, as a conversion's
 * recipe reads (decision conversion-controls-hover-their-formula).
 */
public final class RunsTooltipComponent implements ClientTooltipComponent {
    private static final int LINE_GAP = 2;
    private static final int WHITE = 0xFFFFFFFF;

    private final TooltipLine.Runs line;

    /**
     * @param line the runs the line draws
     */
    public RunsTooltipComponent(TooltipLine.Runs line) {
        this.line = line;
    }

    @Override
    public int getHeight(Font font) {
        return ExpressionLines.lineHeight(line.runs()) + LINE_GAP;
    }

    @Override
    public int getWidth(Font font) {
        return ExpressionLines.width(font, line.runs());
    }

    // A tooltip takes no click, so the runs' hover areas land in a frame of their own.
    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        ExpressionLines.draw(font, line.runs(), x, y, getWidth(font) + 1, new ExpressionLines.Painter(graphics, null,
                new ClickTargets(), WHITE, WHITE, (variable, linkX, linkY, linkWidth) -> { }));
    }
}
