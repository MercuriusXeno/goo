package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The one way an item icon draws in the manager: its sprite, and where it stands in a pane its link, a shift + left
 * click opening the Items tab on that item, a plain left click passing to the area under it, and hovering it showing
 * the item's value; the icon at the start of an Items row is the one sprite that links nothing (decision
 * one-item-icon-primitive-hovers-and-links-every-icon-but-the-rows-own).
 * Decision used-by-icons-link-to-items-and-hover-their-value.
 * Decision shift-left-click-follows-plain-left-modifies.
 */
final class ItemLinks {
    /** An item icon's side. */
    static final int ICON_SIZE = 16;

    private ItemLinks() {
    }

    /**
     * Draws an item icon that hovers the item's value and follows to it.
     *
     * @param graphics the frame's graphics
     * @param icons    the item stacks the sprites draw from
     * @param targets  the frame's click areas
     * @param actions  the panes' line back to the screen
     * @param id       the item's id
     * @param x        the icon's left edge
     * @param y        the icon's top edge
     */
    static void draw(GuiGraphicsExtractor graphics, ItemIcons icons, ClickTargets targets, ManagerActions actions,
                     String id, int x, int y) {
        sprite(graphics, icons.of(id), x, y);
        add(targets, actions, id, x, y);
    }

    /**
     * Draws an item's sprite alone, where no click or hover can reach it: inside a tooltip, a dropdown's row or an
     * Items row's own start.
     *
     * @param graphics the frame's graphics
     * @param stack    the item's stack
     * @param x        the sprite's left edge
     * @param y        its top edge
     */
    static void sprite(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }

    /**
     * Lays an item icon's click and hover.
     *
     * @param targets the frame's click areas
     * @param actions the panes' line back to the screen
     * @param id      the item's id
     * @param x       the icon's left edge
     * @param y       the icon's top edge
     */
    static void add(ClickTargets targets, ManagerActions actions, String id, int x, int y) {
        add(targets, actions, id, x, y, ICON_SIZE, ICON_SIZE);
    }

    /**
     * Lays an item's click and hover over an area of its own size, as an item's id named in an Audit finding takes.
     *
     * @param targets the frame's click areas
     * @param actions the panes' line back to the screen
     * @param id      the item's id
     * @param x       the area's left edge
     * @param y       its top edge
     * @param width   its width
     * @param height  its height
     */
    static void add(ClickTargets targets, ManagerActions actions, String id, int x, int y, int width, int height) {
        targets.addFollow(x, y, width, height, () -> actions.openItem(id), ItemIcons.hoverKey(id));
    }
}
