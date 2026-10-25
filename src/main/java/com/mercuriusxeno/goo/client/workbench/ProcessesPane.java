package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Draws the Processes tab: each block's summary and its entries with their form, their text and the parser's
 * verdict; a click on an entry's text swaps in an edit box that writes the draft on Enter or blur, and Delete drops
 * the entry, as index.html's processes section does (decision goo-workbench-opens-the-in-game-manager-screen). The
 * state lives in {@link ProcessesTab}.
 */
final class ProcessesPane {
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_HEIGHT = 13;
    private static final int BOX_HEIGHT = 12;
    // The edit box's text sits lower in the box than a row's text, so the box stands a little above the row.
    private static final int BOX_NUDGE = 2;
    private static final int KEY_WIDTH = 120;
    private static final int KIND_WIDTH = 70;
    private static final int VERDICT_WIDTH = 160;
    private static final int GAP = 6;
    private static final int SECTION_GAP = 6;
    private static final int SCROLL_STEP = ROW_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int LINK = 0xFF80C0FF;
    private static final int TREE_COLOR = 0xFFE0C060;
    private static final int TEXT_COLOR = 0x30FFFFFF;
    private static final String ASSIGNMENT = "assignment";
    private static final String DELETE = "[Delete]";
    private static final String SUMMARY_GAP = "  ";
    private static final String NO_TEXT = "";

    private final ProcessesTab processes;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private @Nullable EditBox field;
    private @Nullable String editingSection;
    private @Nullable String editingKey;
    private boolean fieldHeldFocus;
    private boolean visible;
    private int scroll;

    ProcessesPane(ProcessesTab processes, ManagerDraft draft, ManagerActions actions) {
        this.processes = processes;
        this.draft = draft;
        this.actions = actions;
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        EditBox previous = field;
        field = actions.addWidget(new EditBox(font, 0, 0, KEY_WIDTH, BOX_HEIGHT, Component.literal(NO_TEXT)));
        field.setMaxLength(Short.MAX_VALUE);
        if (previous != null) {
            field.setValue(previous.getValue());
        }
        show(visible);
    }

    /**
     * @param shown whether the Processes tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        if (field != null) {
            field.visible = shown && editingKey != null;
        }
    }

    /**
     * Describes the draft's processes afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.PROCESSES, draft.text(), processes::readDescribed);
    }

    /**
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * SCROLL_STEP);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the pane took the key: Enter or Escape ends the edit
     */
    boolean keyPressed(int key) {
        if (field != null && field.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE)) {
            field.setFocused(false);
            return true;
        }
        return false;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        commitOnBlur();
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        boolean fieldShown = false;
        int y = bounds.top() - scroll;
        for (ProcessesTab.ProcessSection section : processes.sections()) {
            graphics.text(font, section.section() + SUMMARY_GAP + section.summary(), bounds.left(), y, WHITE);
            y += LINE_HEIGHT + GAP;
            for (ProcessesTab.ProcessRow row : section.rows()) {
                fieldShown |= drawRow(graphics, font, section.section(), row, bounds, y, targets);
                y += ROW_HEIGHT;
            }
            y += SECTION_GAP;
        }
        scroll = Math.min(scroll, Math.max(0, y + scroll - bounds.bottom()));
        targets.unclip();
        graphics.disableScissor();
        if (field != null) {
            field.visible = visible && fieldShown;
        }
    }

    // Answers whether the edit box stands on this row, inside the pane.
    private boolean drawRow(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                            GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        graphics.text(font, font.plainSubstrByWidth(row.key(), KEY_WIDTH - GAP), x, y, WHITE);
        graphics.text(font, row.kind(), x + KEY_WIDTH, y, ASSIGNMENT.equals(row.kind()) ? TREE_COLOR : GRAY);
        int textX = x + KEY_WIDTH + KIND_WIDTH;
        int deleteX = bounds.right() - font.width(DELETE);
        int verdictX = deleteX - GAP - VERDICT_WIDTH;
        int textWidth = verdictX - GAP - textX;
        boolean editingThis = section.equals(editingSection) && row.key().equals(editingKey) && field != null;
        if (editingThis) {
            field.setPosition(textX, y - BOX_NUDGE);
            field.setWidth(textWidth);
        } else {
            graphics.fill(textX, y - 1, textX + textWidth, y + LINE_HEIGHT, TEXT_COLOR);
            graphics.text(font, font.plainSubstrByWidth(row.text(), textWidth), textX, y, row.parsed() ? WHITE : RED);
            targets.add(textX, y - 1, textWidth, LINE_HEIGHT + 1, () -> startEditing(section, row));
        }
        graphics.text(font, font.plainSubstrByWidth(row.verdict(), VERDICT_WIDTH), verdictX, y,
                row.parsed() ? GREEN : RED);
        graphics.text(font, DELETE, deleteX, y, LINK);
        targets.add(deleteX, y, font.width(DELETE), LINE_HEIGHT, () -> delete(section, row.key()));
        return editingThis && y >= bounds.top() && y + BOX_HEIGHT <= bounds.bottom();
    }

    private void startEditing(String section, ProcessesTab.ProcessRow row) {
        commit();
        editingSection = section;
        editingKey = row.key();
        if (field != null) {
            field.setValue(row.text());
            field.visible = visible;
            actions.focus(field);
        }
        fieldHeldFocus = false;
    }

    private void commitOnBlur() {
        if (field == null || editingKey == null) {
            return;
        }
        if (field.isFocused()) {
            fieldHeldFocus = true;
        } else if (fieldHeldFocus) {
            commit();
        }
    }

    private void commit() {
        if (field == null || editingSection == null || editingKey == null) {
            return;
        }
        processes.edit(editingSection, editingKey, field.getValue());
        editingSection = null;
        editingKey = null;
        fieldHeldFocus = false;
        show(visible);
        actions.draftChanged();
    }

    private void delete(String section, String key) {
        if (key.equals(editingKey)) {
            editingSection = null;
            editingKey = null;
        }
        processes.delete(section, key);
        show(visible);
        actions.draftChanged();
    }
}
