package com.mercuriusxeno.goo.client.workbench;

/**
 * Every color the values manager draws, each named by the role it plays, so a pane reads a role here rather than
 * declaring a color of its own (decision one-palette-names-every-color-by-role).
 */
final class Palette {
    /** Text, and a sprite blit's untinted white. */
    static final int TEXT = 0xFFFFFFFF;
    /** Text standing back from the text around it: a note, a suffix, a marked name, a dialog's body. */
    static final int MUTED = 0xFFA0A0A0;
    /** An error, no value, a remove control's X and a choice that loses work. */
    static final int DANGER = 0xFFFF5555;
    /** A done check, a valid mark and a flagless audit section. */
    static final int GOOD = 0xFF80E080;
    /**
     * A restore arrow (decision every-delete-marks-for-removal-and-a-gold-arrow-restores), an expression's
     * = + - x ( ) symbols (decision read-view-reads-type-equals-possessive-terms) and the draft's name.
     */
    static final int GOLD = 0xFFE0C060;
    /** The one link color: a button's label and a prefab named inside an expression. */
    static final int LINK = 0xFF80C0FF;
    /** A button's border. */
    static final int BORDER = 0xFF5070A0;
    /** A button's inner fill. */
    static final int FILL = 0xFF182030;
    /** The dim one-pixel rule between consecutive rows. */
    static final int SEPARATOR = 0x40FFFFFF;
    // Decision fills-are-two-roles-selected-and-panel.
    /** The selected row's fill: Groups' card and member, and any pane that gains selection. */
    static final int SELECTED_FILL = 0x60FFFFFF;
    /** The expression builder's panel fill. */
    static final int PANEL_FILL = 0x30FFFFFF;
    /** Audit's progress bar, a dark meter under its running lines. */
    static final int METER_FILL = 0x40000000;
    /** The pale gold a followed row glows in on the frame its glow starts. */
    static final int GLOW = 0x70FFE070;
    /** A dropdown's or picker's fill. */
    static final int POPUP_FILL = 0xF0101010;
    /** A dropdown's or picker's border. */
    static final int POPUP_BORDER = 0xFF606060;
    /** The dim a dialog lays over the screen behind it. */
    static final int DIALOG_DIM = 0xB0000000;
    /** A dialog's card. */
    static final int DIALOG_CARD = 0xF0181818;
    /** The manager screen's backdrop over the world. */
    static final int SCREEN_BACKDROP = 0xC0101010;
    /** Audit's file headings, apart from the sections' white and green headers. */
    static final int FILE_HEADING = 0xFFFFFF55;
    /** A value derived rather than set. */
    static final int DERIVED = 0xFFA0C8F0;
    /** The type picker's close and nothing glyphs. */
    static final int PICKER_GLYPH = 0xFFFF8080;
    /** A scroll bar's track. */
    static final int SCROLL_TRACK = 0x40FFFFFF;
    /** A scroll bar's thumb. */
    static final int SCROLL_THUMB = 0xC0C0C0C0;
    /** A chip switched off. */
    static final int CHIP_OFF = 0xFF808080;
    /** A hollow chip's inside. */
    static final int CHIP_HOLLOW = 0xFF202020;
    /** The text on a filled chip. */
    static final int CHIP_FILLED_TEXT = 0xFF000000;
    /**
     * A term container's ring, brighter than a segment's border (decision
     * term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
     */
    static final int TERM_BORDER = 0xFF9AB4E0;
    /** A term seam's left pixel. */
    static final int SEAM_LIGHT = 0xFF6A86B4;
    /** A term seam's right pixel, darker than its left. */
    static final int SEAM_DARK = 0xFF0C121C;
    /** A drag rail's fill. */
    static final int RAIL = 0xFF7080A0;
    /** The grip lines a drag rail draws. */
    static final int GRIP = 0xFFD0D8F0;
    /** A rail's fill where what it holds has nowhere to drop, so it takes no press. */
    static final int LOCKED_RAIL = 0xFF3C3C3C;
    /** A locked rail's grip lines, dimmed with it. */
    static final int LOCKED_GRIP = 0xFF5A5A5A;
    /** A parenthetical container's 1 px border (decision parentheticals-alternate-gray-tones-by-depth). */
    static final int NEST_BORDER = 0xC0A8B4D0;
    /** The shade an odd-depth parenthetical darkens by. */
    static final int NEST_DARKER = 0x28000000;
    /** The shade an even-depth parenthetical lightens by. */
    static final int NEST_LIGHTER = 0x28FFFFFF;
    /** The trash strip's tone at rest. */
    static final int TRASH_REST = 0x30FF5555;
    /** The trash strip's tone while a drag is in flight. */
    static final int TRASH_LIT = 0x80FF5555;
    /** The trash strip's tone while the dragged thing hovers it. */
    static final int TRASH_HOVER = 0xD0FF5555;
    /** The trash strip's border, a lighter red than its fill. */
    static final int TRASH_BORDER = 0xFFFF9A9A;
    /** The trash can drawn inside the strip. */
    static final int TRASH_CAN = 0xFFFFE0E0;
    /** The drop cursor's blue. */
    static final int DROP_CURSOR = 0xFF4090FF;
    /** The cursor's blue, see-through, over the term the held () would wrap. */
    static final int WRAP_MARK = 0x804090FF;
    /** The card a dragged term's preview draws on. */
    static final int DRAG_PREVIEW = 0xE0182030;
    /** The shadow a drag lays over what takes no drop. */
    static final int DRAG_SHADOW = 0x8C000000;
    /** The light highlight a drag lays over each target. */
    static final int DRAG_HIGHLIGHT = 0x34FFFFF0;

    private Palette() {
    }
}
