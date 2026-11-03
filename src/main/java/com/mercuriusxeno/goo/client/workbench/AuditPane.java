package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.client.hud.WordWrap;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws the Audit tab: Run saves the draft and runs both dumps in-process on the server, its latest lines showing
 * as they arrive, then the sections the audit carries, flagged ones first and flagless ones headed green with a
 * checkmark, each starting open and, where it holds more
 * than one row, opened or closed by a button before its header or by the header, every one at once by the
 * expand/collapse-all button on the Run row, as index.html's audit section does (decisions
 * goo-workbench-opens-the-in-game-manager-screen,
 * workbench-reruns-the-audit-headless). The progress lines and the sections each scroll under a {@link ScrollBar}
 * (decision every-tab-draws-a-scroll-bar). The state lives in {@link AuditTab}.
 */
final class AuditPane {
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 4;
    private static final int PROGRESS_SHOWN = 8;
    private static final int HEADER_HEIGHT = ButtonPainter.HEIGHT;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    /** The green a flagless section's header draws in. */
    static final int GREEN = 0xFF55FF55;
    private static final int PROGRESS_COLOR = 0x40000000;
    private static final String RUN = "Run Audit";
    private static final String RUNNING = "running: saving the draft and auditing it on the server";
    private static final String OPEN_MARK = "v";
    private static final String CLOSED_MARK = ">";
    private static final String COLLAPSE_ALL = "Collapse all";
    private static final String EXPAND_ALL = "Expand all";
    private static final String CHECKMARK = "✔";
    private static final String PART_GAP = "  ";
    private static final String NO_TEXT = "";

    private final AuditTab audit;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final ScrollBar sectionsBar = new ScrollBar(SCROLL_STEP);
    private final ScrollBar progressBar = ScrollBar.tailing(1);
    private int progressTop;
    private int progressBottom;

    AuditPane(AuditTab audit, ManagerDraft draft, ManagerActions actions) {
        this.audit = audit;
        this.draft = draft;
        this.actions = actions;
    }

    /**
     * Reads the audit's sections, or the text dumps' where no audit JSON stands.
     */
    void refresh() {
        actions.request(WorkbenchOperations.AUDIT_SECTIONS, NO_TEXT, sections -> {
            if (AuditTab.holdsNoSections(sections)) {
                actions.request(WorkbenchOperations.STANDING_DUMPS, NO_TEXT, audit::readSections);
            } else {
                audit.readSections(sections);
            }
        });
    }

    /**
     * Over the progress lines the wheel steps back through the run's earlier lines; elsewhere it scrolls the
     * sections.
     *
     * @param y      the cursor's y
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double y, double amount) {
        if (y >= progressTop && y < progressBottom) {
            progressBar.wheel(amount);
            return;
        }
        sectionsBar.wheel(amount);
    }

    private void run() {
        if (audit.running()) {
            return;
        }
        audit.startRun();
        actions.stream(WorkbenchOperations.RUN_DUMPS, draft.changeSetText(), audit::progress, result -> {
            audit.finishRun(result);
            actions.reloadDraft();
        }, audit::failRun);
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        drawRunRow(graphics, font, bounds, targets);
        int y = bounds.top() + LINE_HEIGHT + GAP;
        y = drawProgress(graphics, font, bounds, y, targets);
        drawSections(graphics, font, new GroupsPane.Bounds(bounds.left(), y, bounds.right(), bounds.bottom()),
                targets);
    }

    // Run Audit, or the running line while a run is in flight, then the expand/collapse-all button, then what the
    // last run left.
    private void drawRunRow(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        int y = bounds.top();
        int x;
        if (audit.running()) {
            graphics.text(font, RUNNING, bounds.left(), y, GRAY);
            x = bounds.left() + font.width(RUNNING) + GAP;
        } else {
            x = ButtonPainter.drawAtText(graphics, font, RUN, bounds.left(), y, targets, this::run).right() + GAP;
        }
        // Decision sections-start-expanded-with-an-expand-collapse-all-button.
        List<String> toggling = togglingHeaders(font, textWidth(bounds));
        if (!toggling.isEmpty()) {
            String label = audit.anyOpen(toggling) ? COLLAPSE_ALL : EXPAND_ALL;
            x = ButtonPainter.drawAtText(graphics, font, label, x, y, targets, () -> audit.toggleAll(toggling))
                    .right() + GAP;
        }
        if (!audit.running()) {
            graphics.text(font, audit.runLine(), x, y, GRAY);
        }
    }

    private static int textWidth(GroupsPane.Bounds bounds) {
        return bounds.right() - bounds.left() - ScrollBar.WIDTH - ScrollBar.GAP;
    }

    // The headers of the sections holding more than one row once wrapped, the ones a button opens and closes.
    private List<String> togglingHeaders(Font font, int width) {
        return audit.sections().stream().filter(section -> rowsOf(font, section, width) > 1)
                .map(AuditTab.AuditSection::header).toList();
    }

    private static int rowsOf(Font font, AuditTab.AuditSection section, int width) {
        return Stream.concat(section.flags().stream(), section.lines().stream())
                .mapToInt(line -> WordWrap.lines(line, width, font::width).size()).sum();
    }

    // Answers the y below the progress lines, which show while a run is in flight.
    private int drawProgress(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, int top,
                             ClickTargets targets) {
        List<String> lines = audit.progressLines();
        progressTop = 0;
        progressBottom = 0;
        if (!audit.running() || lines.isEmpty()) {
            progressBar.reset();
            return top;
        }
        progressBar.measure(lines.size(), PROGRESS_SHOWN);
        int first = progressBar.offset();
        List<String> latest = lines.subList(first, Math.min(lines.size(), first + PROGRESS_SHOWN));
        int height = latest.size() * LINE_HEIGHT;
        int width = textWidth(bounds);
        progressTop = top;
        progressBottom = top + height;
        graphics.fill(bounds.left(), top, bounds.right(), top + height, PROGRESS_COLOR);
        int y = top;
        for (String line : latest) {
            graphics.text(font, font.plainSubstrByWidth(line, width), bounds.left(), y, GRAY);
            y += LINE_HEIGHT;
        }
        progressBar.draw(graphics, targets, bounds.right(), top, top + height);
        return y + GAP;
    }

    private void drawSections(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds area, ClickTargets targets) {
        List<AuditTab.AuditSection> sections = audit.sections();
        if (sections.isEmpty()) {
            return;
        }
        int width = textWidth(area);
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        targets.clip(area.left(), area.top(), area.right(), area.bottom());
        int y = area.top() - sectionsBar.offset();
        for (AuditTab.AuditSection section : sections) {
            y = drawSection(graphics, font, section, new Row(area.left(), y, width), targets) + GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        sectionsBar.measureAndDraw(graphics, targets, y, area.right(), area.top(), area.bottom());
    }

    /**
     * Where a section starts: its left edge, its top and the width its text runs.
     *
     * @param left  its left edge
     * @param top   its top
     * @param width the width its text runs
     */
    private record Row(int left, int top, int width) {
    }

    // Answers the y below the section: its header, after the button opening and closing it where its body holds
    // more than one row, then its flags and lines while open.
    private int drawSection(GuiGraphicsExtractor graphics, Font font, AuditTab.AuditSection section, Row row,
                            ClickTargets targets) {
        // Decision chevron-only-where-a-section-holds-more-than-one-row.
        boolean toggles = rowsOf(font, section, row.width()) > 1;
        boolean open = !toggles || audit.open(section.header());
        int textLeft = row.left();
        if (toggles) {
            // Decision section-chevron-becomes-a-button.
            Runnable toggle = () -> audit.toggle(section.header());
            textLeft = ButtonPainter.draw(graphics, font, open ? OPEN_MARK : CLOSED_MARK, row.left(), row.top(),
                    HEADER_HEIGHT, targets, toggle).right() + GAP;
            targets.add(textLeft, row.top(), row.left() + row.width() - textLeft, HEADER_HEIGHT, toggle);
        }
        // Decision flagless-section-draws-green-with-a-checkmark.
        boolean flagless = section.flags().isEmpty();
        String head = section.header() + PART_GAP + (flagless ? CHECKMARK : section.badge()) + PART_GAP
                + section.source();
        graphics.text(font, font.plainSubstrByWidth(head, row.left() + row.width() - textLeft), textLeft,
                row.top() + ButtonPainter.TEXT_NUDGE, flagless ? GREEN : WHITE);
        int y = row.top() + HEADER_HEIGHT;
        if (open) {
            y = drawLines(graphics, font, section.flags(), row.left(), y, row.width(), RED);
            y = drawLines(graphics, font, section.lines(), row.left(), y, row.width(), GRAY);
        }
        return y;
    }

    private static int drawLines(GuiGraphicsExtractor graphics, Font font, List<String> lines, int x, int top,
                                 int width, int color) {
        int y = top;
        for (String line : lines) {
            // Decision audit-section-lines-word-wrap.
            for (String wrapped : WordWrap.lines(line, width, font::width)) {
                graphics.text(font, wrapped, x, y, color);
                y += LINE_HEIGHT;
            }
        }
        return y;
    }
}
