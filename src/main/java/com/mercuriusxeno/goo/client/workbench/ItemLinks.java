package com.mercuriusxeno.goo.client.workbench;

/**
 * The link every item icon in a used-by list, an indirectly list or a group's members takes: a shift + left click
 * opens the Items tab on that item, a plain left click passes to the area under it, and hovering it shows the item's
 * value.
 * Decision used-by-icons-link-to-items-and-hover-their-value.
 * Decision shift-left-click-follows-plain-left-modifies.
 */
final class ItemLinks {
    /** An item icon's side. */
    static final int ICON_SIZE = 16;

    private ItemLinks() {
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
        targets.addFollow(x, y, ICON_SIZE, ICON_SIZE, () -> actions.openItem(id), ItemIcons.hoverKey(id));
    }
}
