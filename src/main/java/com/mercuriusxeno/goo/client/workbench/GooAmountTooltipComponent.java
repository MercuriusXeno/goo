package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Draws a goo amount tooltip line: the type's 16px icon followed by its amount (decision
 * value-tooltips-draw-each-types-icon-before-its-amount).
 */
public final class GooAmountTooltipComponent implements ClientTooltipComponent {
    private static final int ICON_GAP = 2;
    private static final int LINE_GAP = 2;
    // The font's glyphs stand 8px tall, so the amount centers on the 16px icon 4px down.
    private static final int TEXT_NUDGE = 4;
    private static final int WHITE = 0xFFFFFFFF;

    private final TooltipLine.GooAmount line;

    /**
     * @param line the type and amount the line draws
     */
    public GooAmountTooltipComponent(TooltipLine.GooAmount line) {
        this.line = line;
    }

    @Override
    public int getHeight(Font font) {
        return GooTypeIcons.SIZE + LINE_GAP;
    }

    @Override
    public int getWidth(Font font) {
        return GooTypeIcons.SIZE + ICON_GAP + font.width(line.amount());
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        GooTypeIcons.blit(graphics, line.type(), x, y);
        graphics.text(font, line.amount(), x + GooTypeIcons.SIZE + ICON_GAP, y + TEXT_NUDGE, WHITE);
    }
}
