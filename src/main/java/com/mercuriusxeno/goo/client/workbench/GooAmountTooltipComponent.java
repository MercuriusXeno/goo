package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.util.function.ToIntFunction;

/**
 * Draws a goo amount tooltip line: the type's icon at its texture's own size followed by its amount (decisions
 * value-tooltips-draw-each-types-icon-before-its-amount, goo-type-icons-draw-native-everywhere).
 */
public final class GooAmountTooltipComponent implements ClientTooltipComponent {
    private static final int ICON_GAP = 2;
    private static final int LINE_GAP = 2;
    // The font's glyphs stand 8px tall; the amount centers on the icon.
    private static final int GLYPH_HEIGHT = 8;
    private static final int WHITE = 0xFFFFFFFF;

    private final TooltipLine.GooAmount line;
    private final int iconSize;

    /**
     * @param line the type and amount the line draws
     */
    public GooAmountTooltipComponent(TooltipLine.GooAmount line) {
        this(line, GooTypeIcons::sizeOf);
    }

    /**
     * @param line   the type and amount the line draws
     * @param sizeOf a type icon's side by its type
     */
    GooAmountTooltipComponent(TooltipLine.GooAmount line, ToIntFunction<String> sizeOf) {
        this.line = line;
        this.iconSize = sizeOf.applyAsInt(line.type());
    }

    @Override
    public int getHeight(Font font) {
        return iconSize + LINE_GAP;
    }

    @Override
    public int getWidth(Font font) {
        return iconSize + ICON_GAP + font.width(line.amount());
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        GooTypeIcons.blit(graphics, line.type(), x, y);
        graphics.text(font, line.amount(), x + iconSize + ICON_GAP, y + GooTypeIcons.centered(iconSize, GLYPH_HEIGHT),
                WHITE);
    }
}
