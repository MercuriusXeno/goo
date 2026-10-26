package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;

/**
 * Draws the Mutators tab: each block's summary and its entries, each on two lines, the key, the form, the parser's
 * verdict and Delete on the first and the text at full width on the second (decision mutator-rows-take-an-extra-line);
 * a click on an entry's text swaps in an edit box that writes the draft on Enter or blur, and Delete drops the entry,
 * as index.html's processes section does (decision goo-workbench-opens-the-in-game-manager-screen). The rows scroll
 * under a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ProcessesTab}.
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
    private final ScrollBar bar = new ScrollBar(SCROLL_STEP);

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
     * @param shown whether the Mutators tab is the one drawn
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
        bar.wheel(amount);
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
        GroupsPane.Bounds rows = new GroupsPane.Bounds(bounds.left(), bounds.top(),
                bounds.right() - ScrollBar.WIDTH - ScrollBar.GAP, bounds.bottom());
        int y = bounds.top() - bar.offset();
        for (ProcessesTab.ProcessSection section : processes.sections()) {
            graphics.text(font, section.section() + SUMMARY_GAP + section.summary(), bounds.left(), y, WHITE);
            y += LINE_HEIGHT + GAP;
            for (ProcessesTab.ProcessRow row : section.rows()) {
                fieldShown |= drawRow(graphics, font, section.section(), row, rows, y, targets);
                y += ProcessesTab.layoutRow(rows.left(), rows.right(), y).height();
            }
            y += SECTION_GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, targets, y, bounds.right(), bounds.top(), bounds.bottom());
        if (field != null) {
            field.visible = visible && fieldShown;
        }
    }

    // Answers whether the edit box stands on this row, inside the pane.
    private boolean drawRow(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                            GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        drawHeadLine(graphics, font, section, row, bounds, y, targets);
        ProcessesTab.RowLayout layout = ProcessesTab.layoutRow(bounds.left(), bounds.right(), y);
        int textX = layout.textX();
        int textY = layout.textY();
        int textWidth = layout.textWidth();
        boolean editingThis = section.equals(editingSection) && row.key().equals(editingKey) && field != null;
        if (editingThis) {
            field.setPosition(textX, textY - BOX_NUDGE);
            field.setWidth(textWidth);
        } else {
            // decision curated-view-shows-no-brace-quote-or-raw-id
            List<ExpressionLines.Run> runs = ExpressionLines.mutatorRuns(row.text());
            int textHeight = ExpressionLines.lineHeight(runs);
            graphics.fill(textX, textY - 1, textX + textWidth, textY + textHeight, TEXT_COLOR);
            targets.add(textX, textY - 1, textWidth, textHeight + 1, () -> startEditing(section, row));
            ExpressionLines.draw(font, runs, textX, textY, textWidth, new ExpressionLines.Painter(graphics, null,
                    targets, row.parsed() ? WHITE : RED, LINK, (name, linkX, linkY, linkWidth) -> { }));
        }
        return editingThis && textY - BOX_NUDGE >= bounds.top() && textY - BOX_NUDGE + BOX_HEIGHT <= bounds.bottom();
    }

    // The row's first line: its key, its form, the parser's verdict and Delete.
    private void drawHeadLine(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                              GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        graphics.text(font, font.plainSubstrByWidth(row.key(), KEY_WIDTH - GAP), x, y, WHITE);
        graphics.text(font, row.kind(), x + KEY_WIDTH, y, ASSIGNMENT.equals(row.kind()) ? TREE_COLOR : GRAY);
        int deleteX = bounds.right() - font.width(DELETE);
        int verdictX = Math.max(x + KEY_WIDTH + KIND_WIDTH, deleteX - GAP - VERDICT_WIDTH);
        graphics.text(font, font.plainSubstrByWidth(row.verdict(), deleteX - GAP - verdictX), verdictX, y,
                row.parsed() ? GREEN : RED);
        graphics.text(font, DELETE, deleteX, y, LINK);
        targets.add(deleteX, y, font.width(DELETE), LINE_HEIGHT, () -> delete(section, row.key()));
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
