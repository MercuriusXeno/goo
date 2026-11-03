package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * Draws a group's members as their sprites on one line, then +n for the members the line leaves out (decision
 * group-link-hovers-member-icons-then-plus-n).
 */
public final class MembersTooltipComponent implements ClientTooltipComponent {
    private static final int SPRITE_SIZE = 16;
    private static final int SPRITE_GAP = 1;
    private static final int TEXT_GAP = 2;
    private static final int LINE_GAP = 2;
    // The +n centers on the sprites' row.
    private static final int HALF = 2;
    private static final String MORE_MARK = "+";
    private static final int WHITE = 0xFFFFFFFF;

    private final List<ItemStack> stacks;
    private final String more;

    /**
     * @param line the members the line draws
     */
    public MembersTooltipComponent(TooltipLine.Members line) {
        this.stacks = line.ids().stream().map(ItemIcons::stackOf).filter(stack -> !stack.isEmpty()).toList();
        this.more = line.more() > 0 ? MORE_MARK + line.more() : "";
    }

    @Override
    public int getHeight(Font font) {
        return SPRITE_SIZE + LINE_GAP;
    }

    @Override
    public int getWidth(Font font) {
        int sprites = stacks.isEmpty() ? 0 : stacks.size() * (SPRITE_SIZE + SPRITE_GAP) - SPRITE_GAP;
        return more.isEmpty() ? sprites : sprites + TEXT_GAP + font.width(more);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int left = x;
        for (ItemStack stack : stacks) {
            graphics.item(stack, left, y);
            left += SPRITE_SIZE + SPRITE_GAP;
        }
        if (!more.isEmpty()) {
            graphics.text(font, more, left - SPRITE_GAP + TEXT_GAP, y + (SPRITE_SIZE - font.lineHeight) / HALF + 1,
                    WHITE, true);
        }
    }
}
