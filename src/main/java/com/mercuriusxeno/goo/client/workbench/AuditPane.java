package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the Audit tab: Run saves the draft and runs both dumps in-process on the server, its latest lines showing
 * as they arrive, then the sections the audit carries, flagged ones first, each opened or closed by its header, as
 * index.html's audit section does (decisions goo-workbench-opens-the-in-game-manager-screen,
 * workbench-reruns-the-audit-headless). The progress lines and the sections each scroll under a {@link ScrollBar}
 * (decision every-tab-draws-a-scroll-bar). The state lives in {@link AuditTab}.
 */
final class AuditPane {
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 4;
    private static final int PROGRESS_SHOWN = 8;
    private static final int SECTIONS_OPEN = 2;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int PROGRESS_COLOR = 0x40000000;
    private static final String RUN = "Run the audit";
    private static final String RUNNING = "running: saving the draft and auditing it on the server";
    private static final String NO_SECTIONS = "No audit stands yet: Run writes one.";
    private static final String OPEN_MARK = "v ";
    private static final String CLOSED_MARK = "> ";
    private static final String PART_GAP = "  ";
    private static final String NO_TEXT = "";

    private final AuditTab audit;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final Set<String> toggled = new HashSet<>();
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
        actions.stream(WorkbenchOperations.RUN_DUMPS, draft.text(), audit::progress, result -> {
            audit.finishRun(result);
            actions.reloadDraft();
        }, audit::failRun);
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        int x = bounds.left();
        int y = bounds.top();
        if (audit.running()) {
            graphics.text(font, RUNNING, x, y, GRAY);
        } else {
            ButtonPainter.Box run = ButtonPainter.drawAtText(graphics, font, RUN, x, y, targets, this::run);
            graphics.text(font, audit.runLine(), run.right() + GAP, y, GRAY);
        }
        y += LINE_HEIGHT + GAP;
        y = drawProgress(graphics, font, bounds, y, targets);
        drawSections(graphics, font, new GroupsPane.Bounds(x, y, bounds.right(), bounds.bottom()), targets);
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
        int width = bounds.right() - bounds.left() - ScrollBar.WIDTH - ScrollBar.GAP;
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
            graphics.text(font, NO_SECTIONS, area.left(), area.top(), GRAY);
            return;
        }
        int width = area.right() - area.left() - ScrollBar.WIDTH - ScrollBar.GAP;
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        targets.clip(area.left(), area.top(), area.right(), area.bottom());
        int y = area.top() - sectionsBar.offset();
        for (int index = 0; index < sections.size(); index++) {
            AuditTab.AuditSection section = sections.get(index);
            boolean open = index < SECTIONS_OPEN != toggled.contains(section.header());
            String head = (open ? OPEN_MARK : CLOSED_MARK) + section.header() + PART_GAP + section.badge() + PART_GAP
                    + section.source();
            graphics.text(font, font.plainSubstrByWidth(head, width), area.left(), y, WHITE);
            targets.add(area.left(), y, width, LINE_HEIGHT, () -> toggle(section.header()));
            y += LINE_HEIGHT;
            if (open) {
                y = drawLines(graphics, font, section.flags(), area.left(), y, width, RED);
                y = drawLines(graphics, font, section.lines(), area.left(), y, width, GRAY);
            }
            y += GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        sectionsBar.measureAndDraw(graphics, targets, y, area.right(), area.top(), area.bottom());
    }

    private static int drawLines(GuiGraphicsExtractor graphics, Font font, List<String> lines, int x, int top,
                                 int width, int color) {
        int y = top;
        for (String line : lines) {
            graphics.text(font, font.plainSubstrByWidth(line, width), x, y, color);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private void toggle(String header) {
        if (!toggled.remove(header)) {
            toggled.add(header);
        }
    }
}
