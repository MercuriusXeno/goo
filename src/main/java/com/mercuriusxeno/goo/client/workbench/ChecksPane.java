package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/**
 * Draws the Checks tab: each type-source rule the server ran against the draft on its last read or save, green or
 * red, a red rule listing the items it wants, as index.html's checks section does (decisions
 * goo-workbench-opens-the-in-game-manager-screen, type-source-checks-are-rules-that-gate-promote). The state lives
 * in {@link ChecksTab}.
 */
final class ChecksPane {
    private static final int LINE_HEIGHT = 11;
    private static final int GAP = 6;
    private static final int INDENT = 12;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final String NO_CHECKS = "No checks ran yet: they run against the draft on every read and save.";

    private final ChecksTab checks;
    private int scroll;

    ChecksPane(ChecksTab checks) {
        this.checks = checks;
    }

    /**
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * SCROLL_STEP);
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds) {
        List<ChecksTab.CheckRow> rows = checks.rows();
        if (rows.isEmpty()) {
            graphics.text(font, NO_CHECKS, bounds.left(), bounds.top(), GRAY);
            return;
        }
        int width = bounds.right() - bounds.left();
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        int y = bounds.top() - scroll;
        for (ChecksTab.CheckRow row : rows) {
            graphics.text(font, font.plainSubstrByWidth(row.title(), width), bounds.left(), y,
                    row.passed() ? GREEN : RED);
            y += LINE_HEIGHT;
            for (String want : row.wants()) {
                graphics.text(font, font.plainSubstrByWidth(want, width - INDENT), bounds.left() + INDENT, y, GRAY);
                y += LINE_HEIGHT;
            }
            y += GAP;
        }
        scroll = Math.min(scroll, Math.max(0, y + scroll - bounds.bottom()));
        graphics.disableScissor();
    }
}
