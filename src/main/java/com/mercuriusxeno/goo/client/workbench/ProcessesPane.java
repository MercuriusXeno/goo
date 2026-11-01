package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the Mutators tab: each block's summary and its entries, the key, the form, the parser's verdict and Delete on
 * an entry's first line and its sentence of controls at full width below (decision mutator-rows-take-an-extra-line),
 * which build the entry with nothing typed but a number or a search (decision
 * expression-builder-composes-every-form-without-typing), and Delete drops the entry, as
 * index.html's processes section does (decision goo-workbench-opens-the-in-game-manager-screen). The rows scroll
 * under a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ProcessesTab}.
 */
final class ProcessesPane {
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_HEIGHT = 13;
    private static final int KEY_WIDTH = 120;
    private static final int KIND_WIDTH = 70;
    private static final int VERDICT_WIDTH = 160;
    private static final int GAP = 6;
    private static final int SECTION_GAP = 6;
    private static final int SCROLL_STEP = ROW_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    /** An Invalid verdict's color. */
    static final int RED = 0xFFFF5555;
    /** A Valid verdict's color. */
    static final int GREEN = 0xFF80E080;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";
    private static final String MARK_GAP = " ";
    private static final String REASON_GAP = ": ";
    private static final String NO_REASON = "";
    private static final int TREE_COLOR = 0xFFE0C060;
    private static final int ROW_COLOR = 0x20FFFFFF;
    private static final String ASSIGNMENT = "assignment";
    private static final String DELETE = "Delete";
    private static final String SUMMARY_GAP = "  ";

    private final ProcessesTab processes;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final BuilderOverlays overlays;
    private final MutatorControls controls;
    private final ScrollBar bar = new ScrollBar(SCROLL_STEP);
    private @Nullable String revealPending;
    private @Nullable RevealedRow revealedRow;

    // Where the row a follow revealed stood inside the list's content this frame.
    private record RevealedRow(int top, int height) {
    }

    /**
     * @param processes   the Mutators tab's state
     * @param draft       the draft every tab edits
     * @param actions     the panes' line back to the screen
     * @param icons       the items' icons
     * @param treePrefabs the tree prefabs' names, which an additive adds
     */
    ProcessesPane(ProcessesTab processes, ManagerDraft draft, ManagerActions actions, ItemIcons icons,
                  Supplier<List<String>> treePrefabs) {
        this.processes = processes;
        this.draft = draft;
        this.actions = actions;
        this.overlays = new BuilderOverlays(actions, icons);
        this.controls = new MutatorControls(processes, overlays, actions, treePrefabs,
                () -> GooTypes.order().stream().map(GooTypes::id).toList(), actions::draftChanged);
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        overlays.init(font);
    }

    /**
     * @param shown whether the Mutators tab is the one drawn
     */
    void show(boolean shown) {
        overlays.show(shown);
    }

    /**
     * Describes the draft's processes afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.PROCESSES, draft.changeSetText(), processes::readDescribed);
    }

    /**
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        if (overlays.scrollList(amount)) {
            return;
        }
        bar.wheel(amount);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the controls took the key: Enter commits a number box and Escape closes a list
     */
    boolean keyPressed(int key) {
        return overlays.keyPressed(key);
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        overlays.commitOnBlur();
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        GroupsPane.Bounds rows = new GroupsPane.Bounds(bounds.left(), bounds.top(),
                bounds.right() - ScrollBar.WIDTH - ScrollBar.GAP, bounds.bottom());
        int y = bounds.top() - bar.offset();
        for (ProcessesTab.ProcessSection section : processes.sections()) {
            graphics.text(font, section.section() + SUMMARY_GAP + section.summary(), bounds.left(), y, WHITE);
            y += LINE_HEIGHT + GAP;
            List<ProcessesTab.ProcessRow> sectionRows = section.rows();
            for (int index = 0; index < sectionRows.size(); index++) {
                RowLayout.drawSeparatorAbove(graphics, index, y - 1, rows.left(), rows.right());
                int rowTop = y;
                ProcessesTab.ProcessRow row = sectionRows.get(index);
                y = drawRow(graphics, font, section.section(), row, rows, y, targets);
                if (row.key().equals(revealPending)) {
                    revealedRow = new RevealedRow(rowTop - bounds.top() + bar.offset(), y - rowTop);
                }
            }
            y += SECTION_GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, targets, y, bounds.right(), bounds.top(), bounds.bottom());
        centerRevealedRow();
        overlays.draw(graphics, font, targets);
    }

    // Answers the y below the row: its head line, then its sentence of controls wrapping at full width.
    private int drawRow(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                        GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        drawHeadLine(graphics, font, section, row, bounds, y, targets);
        ProcessesTab.RowLayout layout = ProcessesTab.layoutRow(bounds.left(), bounds.right(), y);
        int sentenceTop = layout.textY() - 1;
        int below = controls.draw(graphics, font, section, row.key(), new GroupsPane.Bounds(layout.textX(),
                sentenceTop, layout.textX() + layout.textWidth(), bounds.bottom()), sentenceTop, targets);
        graphics.fill(bounds.left(), y - 1, bounds.right(), below, ROW_COLOR);
        return below + GAP;
    }

    // The row's first line: its key, its form, the parser's verdict and Delete.
    private void drawHeadLine(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                              GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        graphics.text(font, font.plainSubstrByWidth(row.key(), KEY_WIDTH - GAP), x, y, WHITE);
        graphics.text(font, row.kind(), x + KEY_WIDTH, y, ASSIGNMENT.equals(row.kind()) ? TREE_COLOR : GRAY);
        int deleteX = bounds.right() - ButtonPainter.width(font, DELETE);
        int verdictX = Math.max(x + KEY_WIDTH + KIND_WIDTH, deleteX - GAP - VERDICT_WIDTH);
        VerdictMark mark = verdictMark(row);
        String verdict = mark.glyph() + MARK_GAP + row.verdict() + (row.reason().isEmpty() ? NO_REASON : REASON_GAP
                + row.reason());
        graphics.text(font, font.plainSubstrByWidth(verdict, deleteX - GAP - verdictX), verdictX, y, mark.color());
        ButtonPainter.drawAtText(graphics, font, DELETE, deleteX, y, targets, () -> delete(section, row.key()));
    }

    /**
     * The mark a row's verdict draws behind.
     *
     * @param glyph the check or the x
     * @param color green for Valid, red for Invalid
     */
    record VerdictMark(String glyph, int color) {
    }

    /**
     * @param row a Mutators row
     * @return a green check for a row the parser accepts, a red x for one it refuses
     *     (decision mutators-read-valid-or-invalid-with-a-mark)
     */
    static VerdictMark verdictMark(ProcessesTab.ProcessRow row) {
        return row.parsed() ? new VerdictMark(CHECK, GREEN) : new VerdictMark(CROSS, RED);
    }

    /**
     * Centers a mutator's row on the next frame, as a mutator link's shift + left click does (decisions
     * diagnose-and-correct-mutator-references-that-do-not-link, shift-follow-centers-the-target-in-its-scrolling-view).
     *
     * @param key the mutator's key
     */
    void reveal(String key) {
        revealPending = key;
    }

    // Once the revealed row has drawn and the bar has measured this frame, centers the row in the list.
    private void centerRevealedRow() {
        if (revealedRow != null) {
            bar.center(revealedRow.top(), revealedRow.height());
            revealedRow = null;
            revealPending = null;
        }
    }

    private void delete(String section, String key) {
        overlays.close();
        processes.delete(section, key);
        actions.draftChanged();
    }
}
