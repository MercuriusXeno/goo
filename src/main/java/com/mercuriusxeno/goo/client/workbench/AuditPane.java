package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.client.hud.WordWrap;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.stream.Stream;

/**
 * Draws the Audit tab: Run saves the draft and runs both dumps in-process on the server, its latest lines showing
 * as they arrive, then the sections the audit carries under a heading for each file they came from, flagged ones
 * first, each header's name bold before its plain results and flagless ones headed green with a checkmark, each
 * starting open and, where it holds more
 * than one row, opened or closed by a button before its header or by the header, every one at once by the
 * expand/collapse-all button on the Run row, as index.html's audit section does (decisions
 * goo-workbench-opens-the-in-game-manager-screen,
 * workbench-reruns-the-audit-headless). The progress lines and the sections each scroll under a {@link ScrollBar}
 * (decision every-tab-draws-a-scroll-bar). The state lives in {@link AuditTab}.
 */
final class AuditPane {
    private static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
    private static final int GAP = 4;
    private static final int PROGRESS_SHOWN = 8;
    private static final int HEADER_HEIGHT = ButtonPainter.HEIGHT;
    private static final String RUN = "Run Audit";
    private static final String RUNNING = "running: saving the draft and auditing it on the server";
    private static final String OPEN_MARK = "v";
    private static final String CLOSED_MARK = ">";
    private static final String COLLAPSE_ALL = "Collapse all";
    private static final String EXPAND_ALL = "Expand all";
    private static final String CHECKMARK = "✔";
    private static final String RESULTS_OPEN = " (";
    private static final String SPACE = " ";
    private static final String PART_GAP = "  ";
    private static final String NO_TEXT = "";

    private final AuditTab audit;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final ScrollBar sectionsBar = new ScrollBar(ScrollBar.WHEEL_STEP);
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

    /**
     * @return the sections' scroll bar
     */
    ScrollBar sectionsBar() {
        return sectionsBar;
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
            graphics.text(font, RUNNING, bounds.left(), y, Palette.MUTED);
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
            graphics.text(font, audit.runLine(), x, y, Palette.MUTED);
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
        graphics.fill(bounds.left(), top, bounds.right(), top + height, Palette.METER_FILL);
        int y = top;
        for (String line : latest) {
            graphics.text(font, font.plainSubstrByWidth(line, width), bounds.left(), y, Palette.MUTED);
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
        String file = null;
        for (AuditTab.AuditSection section : sections) {
            // Decision audit-headings-group-by-file-under-a-file-heading.
            if (!section.source().equals(file)) {
                file = section.source();
                graphics.text(font, file, area.left(), y, Palette.FILE_HEADING);
                y += LINE_HEIGHT + GAP;
            }
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
        drawHeader(graphics, font, section, textLeft, Lettering.full(font).centeredTop(row.top(), HEADER_HEIGHT));
        int y = row.top() + HEADER_HEIGHT;
        if (open) {
            y = drawLines(graphics, font, section.flags(), row.left(), y, row.width(), Palette.DANGER);
            y = drawLines(graphics, font, section.lines(), row.left(), y, row.width(), Palette.MUTED);
        }
        return y;
    }

    // The header's name bold, its parenthesized results plain after it, then its flag count, or a checkmark with
    // the whole line green where it flags nothing.
    private static void drawHeader(GuiGraphicsExtractor graphics, Font font, AuditTab.AuditSection section, int left,
                                   int textTop) {
        // Decision flagless-section-draws-green-with-a-checkmark.
        boolean flagless = section.flags().isEmpty();
        int color = flagless ? Palette.GOOD : Palette.TEXT;
        String header = section.header();
        int results = header.indexOf(RESULTS_OPEN);
        // Decision heading-name-weighs-more-than-its-parenthesized-results.
        Component name = Component.literal(results < 0 ? header : header.substring(0, results))
                .withStyle(ChatFormatting.BOLD);
        graphics.text(font, name, left, textTop, color);
        int x = left + font.width(name);
        if (results >= 0) {
            String plain = header.substring(results).trim();
            x += font.width(SPACE);
            graphics.text(font, plain, x, textTop, color);
            x += font.width(plain);
        }
        graphics.text(font, flagless ? CHECKMARK : section.badge(), x + font.width(PART_GAP), textTop, color);
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
