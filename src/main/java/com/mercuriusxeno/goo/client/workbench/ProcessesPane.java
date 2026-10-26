package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Draws the Mutators tab: each block's summary and its entries, each on two lines, the key, the form, the parser's
 * verdict and Delete on the first and the text at full width on the second (decision mutator-rows-take-an-extra-line);
 * a click on an entry's text opens its controls on a line below it, which build the entry with nothing typed but a
 * number or a search (decision expression-builder-composes-every-form-without-typing), and Delete drops the entry, as
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
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int LINK = 0xFF80C0FF;
    private static final int TREE_COLOR = 0xFFE0C060;
    private static final int TEXT_COLOR = 0x30FFFFFF;
    private static final int OPEN_COLOR = 0x50FFFFFF;
    private static final String ASSIGNMENT = "assignment";
    private static final String DELETE = "[Delete]";
    private static final String SUMMARY_GAP = "  ";

    private final ProcessesTab processes;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final BuilderOverlays overlays;
    private final MutatorControls controls;
    private final ScrollBar bar = new ScrollBar(SCROLL_STEP);
    private @Nullable String openSection;
    private @Nullable String openKey;

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
        this.controls = new MutatorControls(processes, overlays, treePrefabs, actions::draftChanged);
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
            for (ProcessesTab.ProcessRow row : section.rows()) {
                y = drawRow(graphics, font, section.section(), row, rows, y, targets);
            }
            y += SECTION_GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, targets, y, bounds.right(), bounds.top(), bounds.bottom());
        overlays.draw(graphics, font, targets);
    }

    // Answers the y below the row and, where it stands open, its controls' line.
    private int drawRow(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                        GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        drawHeadLine(graphics, font, section, row, bounds, y, targets);
        ProcessesTab.RowLayout layout = ProcessesTab.layoutRow(bounds.left(), bounds.right(), y);
        boolean open = section.equals(openSection) && row.key().equals(openKey);
        // decision curated-view-shows-no-brace-quote-or-raw-id
        List<ExpressionLines.Run> runs = ExpressionLines.mutatorRuns(row.text());
        int textHeight = ExpressionLines.lineHeight(runs);
        graphics.fill(layout.textX(), layout.textY() - 1, layout.textX() + layout.textWidth(),
                layout.textY() + textHeight, open ? OPEN_COLOR : TEXT_COLOR);
        targets.add(layout.textX(), layout.textY() - 1, layout.textWidth(), textHeight + 1,
                () -> toggleOpen(section, row.key()));
        ExpressionLines.draw(font, runs, layout.textX(), layout.textY(), layout.textWidth(),
                new ExpressionLines.Painter(graphics, null, targets, row.parsed() ? WHITE : RED, LINK,
                        (name, linkX, linkY, linkWidth) -> { }));
        int below = y + layout.height();
        if (!open) {
            return below;
        }
        controls.draw(graphics, font, section, row.key(), bounds.left(), below, targets);
        return below + BuilderOverlays.LINE;
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

    private void toggleOpen(String section, String key) {
        overlays.close();
        boolean wasOpen = section.equals(openSection) && key.equals(openKey);
        openSection = wasOpen ? null : section;
        openKey = wasOpen ? null : key;
    }

    private void delete(String section, String key) {
        if (key.equals(openKey)) {
            overlays.close();
            openSection = null;
            openKey = null;
        }
        processes.delete(section, key);
        actions.draftChanged();
    }
}
