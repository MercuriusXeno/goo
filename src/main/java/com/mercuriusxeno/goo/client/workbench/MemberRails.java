package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/**
 * A custom group's member list numbers each member and draws a drag rail at its left; a rail dragged onto another
 * member's row moves the member to that row's place, the drag masking the screen but the row it would take, as the
 * builder's type rows reorder by their rails (decision type-rows-drag-by-a-rail-to-reorder-or-trash). A #tag group's
 * members take neither, since a tag's members cannot be reordered.
 * Decision custom-group-members-drag-by-numbered-rails.
 */
final class MemberRails {
    private static final int GAP = 2;
    private static final int NONE = -1;

    /**
     * What a dropped rail does with its member.
     */
    @FunctionalInterface
    interface Mover {
        /**
         * @param from the member's index
         * @param to   the index it takes
         */
        void move(int from, int to);
    }

    private final Mover mover;
    private int listTop;
    private int count;
    private int held = NONE;
    private double heldY;

    /**
     * @param mover what a dropped rail does with its member
     */
    MemberRails(Mover mover) {
        this.mover = mover;
    }

    /**
     * Lays the list the rails drop onto this frame.
     *
     * @param top     the first member row's top
     * @param members how many members the list holds
     */
    void layList(int top, int members) {
        listTop = top;
        count = members;
    }

    /**
     * @param font the screen's font
     * @param card the member's row
     * @return where the member's icon stands, past its rail and the widest number the list draws
     */
    int iconLeft(Font font, GroupsPane.Bounds card) {
        return numberLeft(card) + font.width(String.valueOf(count)) + GAP;
    }

    private static int numberLeft(GroupsPane.Bounds card) {
        return card.left() + ControlFlow.RAIL_WIDTH + GAP;
    }

    /**
     * Draws the member's rail and its 1-based number, the rail grabbing the member, drawn after the row so the rail
     * takes the press over the row's own click.
     *
     * @param graphics the frame's graphics
     * @param font     the screen's font
     * @param card     the member's row
     * @param index    the member's index
     * @param id       the member's id, which the rail names as what it drags
     * @param targets  the frame's click areas
     */
    void draw(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds card, int index, String id,
              ClickTargets targets) {
        int bottom = card.top() + RowLayout.SPRITE_ROW_HEIGHT;
        Rails.draw(graphics, card.left(), card.top(), bottom);
        targets.addGrab(card.left(), card.top(), ControlFlow.RAIL_WIDTH, bottom - card.top(), grab(index), id);
        int textTop = Lettering.full(font).centeredTop(card.top(), RowLayout.SPRITE_ROW_HEIGHT);
        graphics.text(font, String.valueOf(index + 1), numberLeft(card), textTop, Palette.MUTED);
    }

    /**
     * Masks the screen but the row a held rail would drop its member into; draws nothing while no rail is held.
     *
     * @param graphics the frame's graphics
     * @param detail   the detail column the rows span
     */
    void drawHeld(GuiGraphicsExtractor graphics, GroupsPane.Bounds detail) {
        if (held == NONE) {
            return;
        }
        int top = listTop + slotAt(heldY) * GroupsPane.CARD_HEIGHT;
        // The row lights only where the detail column shows it, so a row scrolled past its edge lights its visible
        // part or nothing.
        DragMask.Rect row = new DragMask.Rect(detail.left(), top, detail.right(), top + GroupsPane.CARD_HEIGHT)
                .within(new DragMask.Rect(detail.left(), detail.top(), detail.right(), detail.bottom()));
        DragMask.draw(graphics, new DragMask.Rect(0, 0, graphics.guiWidth(), graphics.guiHeight()),
                row.isEmpty() ? List.of() : List.of(row));
    }

    private int slotAt(double y) {
        return Math.clamp((long) Math.floor((y - listTop) / GroupsPane.CARD_HEIGHT), 0, count - 1);
    }

    private ClickTargets.PointerGrab grab(int index) {
        return new ClickTargets.PointerGrab() {
            @Override
            public void moved(double x, double y) {
                held = index;
                heldY = y;
            }

            @Override
            public void released() {
                int from = held;
                int to = slotAt(heldY);
                held = NONE;
                if (from != NONE && to != from) {
                    mover.move(from, to);
                }
            }
        };
    }
}
