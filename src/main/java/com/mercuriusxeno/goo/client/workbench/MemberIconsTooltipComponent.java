package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import java.util.Map;

/**
 * Draws a group's members as item icons in one row, then the ..+n label counting the rest (decision
 * group-link-hovers-member-icons-then-plus-n).
 */
public final class MemberIconsTooltipComponent implements ClientTooltipComponent {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int STEP = ICON_SIZE + ICON_GAP;
    private static final int LINE_GAP = 2;
    // The font's glyphs stand 8px tall; the label centers on the icons.
    private static final int GLYPH_HEIGHT = 8;
    private static final int LABEL_NUDGE = (ICON_SIZE - GLYPH_HEIGHT) >> 1;
    private static final int WHITE = 0xFFFFFFFF;

    private final TooltipLine.MemberIcons line;
    private final ItemIcons icons = new ItemIcons(id -> Map.of());

    /**
     * @param line the members the row draws and the label after them
     */
    public MemberIconsTooltipComponent(TooltipLine.MemberIcons line) {
        this.line = line;
    }

    @Override
    public int getHeight(Font font) {
        return ICON_SIZE + LINE_GAP;
    }

    @Override
    public int getWidth(Font font) {
        return line.ids().size() * STEP + font.width(line.more());
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        for (int index = 0; index < line.ids().size(); index++) {
            graphics.item(icons.of(line.ids().get(index)), x + index * STEP, y);
        }
        if (!line.more().isEmpty()) {
            graphics.text(font, line.more(), x + line.ids().size() * STEP, y + LABEL_NUDGE, WHITE);
        }
    }
}
