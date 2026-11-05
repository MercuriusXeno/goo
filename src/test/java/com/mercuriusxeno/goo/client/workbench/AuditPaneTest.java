package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the Audit tab's pane through drawn frames of recorded sections, with no Screen (decisions
 * audit-section-lines-word-wrap, sections-start-expanded-with-an-expand-collapse-all-button,
 * chevron-only-where-a-section-holds-more-than-one-row, section-chevron-becomes-a-button,
 * flagless-section-draws-green-with-a-checkmark, audit-headings-group-by-file-under-a-file-heading,
 * heading-name-weighs-more-than-its-parenthesized-results).
 */
class AuditPaneTest {
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 0, 200, 400);
    private static final int TEXT_WIDTH = VIEW.right() - VIEW.left() - ScrollBar.WIDTH - ScrollBar.GAP;
    private static final String LONG_LINE = "minecraft:cobblestone remainders 2 items solved 40 via the rock root"
            + " and the metal root";
    private static final String OPEN_MARK = "v";
    private static final String CHECKMARK = "✔";
    private static final int WHITE = 0xFFFFFFFF;
    private static final List<Integer> BODY_COLORS = List.of(0xFFA0A0A0, 0xFFFF5555);

    @Nested
    class Wrapping {
        @Test
        void wrapsALineWiderThanThePane() {
            DrawnFrame frame = draw(sections(section("First", LONG_LINE)));
            List<DrawnFrame.Text> body = bodyOf(frame, "First");
            assertTrue(DrawnFrame.font().width(LONG_LINE) > TEXT_WIDTH, "the line fits the pane unwrapped");
            for (DrawnFrame.Text line : body) {
                assertTrue(DrawnFrame.font().width(line.text()) <= TEXT_WIDTH, line.text() + " runs past the pane");
            }
            assertEquals(LONG_LINE, String.join(" ", body.stream().map(DrawnFrame.Text::text).toList()));
        }

        @Test
        void nextHeaderDrawsBelowTheWrappedLines() {
            DrawnFrame frame = draw(sections(section("First", LONG_LINE), section("Second", "rock 1")));
            List<DrawnFrame.Text> body = bodyOf(frame, "First");
            assertEquals(3, body.size(), "the first section's line wraps to three lines: " + body);
            assertTrue(header(frame, "Second").y() > body.getLast().y());
        }
    }

    @Nested
    class Opening {
        private static final String THREE_SECTIONS = sections(section("Alpha", "alpha one", "alpha two"),
                section("Beta", "beta one", "beta two"), section("Gamma", "gamma one", "gamma two"));
        private static final List<String> BODY_LINES = List.of("alpha one", "alpha two", "beta one", "beta two",
                "gamma one", "gamma two");

        @Test
        void firstFrameDrawsEveryBody() {
            assertTrue(draw(THREE_SECTIONS).texts().containsAll(BODY_LINES));
        }

        @Test
        void collapseAllClosesEverySectionAndExpandAllReopens() {
            AuditPane pane = pane(THREE_SECTIONS);
            DrawnFrame first = draw(pane);
            first.click("Collapse all");
            DrawnFrame collapsed = draw(pane);
            assertTrue(BODY_LINES.stream().noneMatch(collapsed.texts()::contains), collapsed.texts().toString());
            collapsed.click("Expand all");
            DrawnFrame expanded = draw(pane);
            assertTrue(expanded.texts().containsAll(BODY_LINES));
            assertTrue(expanded.texts().contains("Collapse all"));
        }

        @Test
        void sectionButtonTogglesOnlyAboveOneRow() {
            AuditPane pane = pane(sections(section("Alpha", "alpha one", "alpha two"), section("Beta", "beta one")));
            DrawnFrame first = draw(pane);
            DrawnFrame.Text mark = first.find(OPEN_MARK);
            assertTrue(mark.x() < header(first, "Alpha").x(), "the button stands before the header");
            DrawnFrame.Text single = header(first, "Beta");
            assertTrue(single.text().startsWith("Beta"), single.text());
            assertFalse(first.targets.click(single.x() - 1, single.y() + 1));
            assertFalse(first.targets.click(single.x() + 1, single.y() + 1));
            assertTrue(first.texts().contains("beta one"));
            first.click(OPEN_MARK);
            DrawnFrame toggled = draw(pane);
            assertFalse(toggled.texts().contains("alpha one"));
            assertTrue(toggled.texts().contains("beta one"));
        }
    }

    @Nested
    class Flagless {
        @Test
        void flaglessHeaderDrawsGreenWithACheckmark() {
            DrawnFrame frame = draw(sections(section("Quiet", "  No recipe cycles detected."),
                    "{\"header\": \"Loud\", \"flags\": [\"first flag\", \"second flag\"], \"lines\": [\"body\"]}"));
            List<DrawnFrame.Text> quiet = headerLine(frame, "Quiet");
            assertTrue(quiet.stream().anyMatch(piece -> piece.text().contains(CHECKMARK)), quiet.toString());
            assertTrue(quiet.stream().allMatch(piece -> piece.color() == Palette.GOOD), quiet.toString());
            List<DrawnFrame.Text> loud = headerLine(frame, "Loud");
            assertTrue(loud.stream().anyMatch(piece -> piece.text().contains("2 flags")), loud.toString());
            assertTrue(loud.stream().noneMatch(piece -> piece.text().contains(CHECKMARK)), loud.toString());
            assertTrue(loud.stream().allMatch(piece -> piece.color() == WHITE), loud.toString());
        }
    }

    /**
     * One wheel notch over the sections moves them one wheel step (decision one-wheel-step-and-one-dialog-primitive).
     */
    @Test
    void wheelNotchMovesTheSectionsOneWheelStep() {
        String[] many = IntStream.range(0, 40).mapToObj(index -> section("S" + index, "line"))
                .toArray(String[]::new);
        AuditPane pane = pane(sections(many));
        draw(pane);
        pane.scroll(VIEW.top() + 200, -1);
        assertEquals(ScrollBar.WHEEL_STEP, pane.sectionsBar().offset());
    }

    @Nested
    class Progress {
        /**
         * A run in flight fills its progress lines in the palette's meter fill, Audit's dark bar (decision
         * fills-are-two-roles-selected-and-panel).
         */
        @Test
        void progressLinesFillTheMeter() {
            AuditTab tab = new AuditTab();
            tab.readSections(sections(section("First", "one")));
            tab.startRun();
            tab.progress("auditing");
            DrawnFrame frame = draw(new AuditPane(tab, new ManagerDraft(), mock(ManagerActions.class)));
            int line = frame.find("auditing").y();
            assertTrue(frame.fillsOf(Palette.METER_FILL).stream().anyMatch(fill -> fill[0] == VIEW.left()
                    && fill[2] == VIEW.right() && fill[1] <= line && line < fill[3]), frame.drawn().toString());
        }
    }

    @Nested
    class Headings {
        private static final String AUDIT_TEXT = "goo_audit.txt";
        private static final String CLIQUES_TEXT = "goo_cliques.txt";

        @Test
        void fileHeadingDrawsOnceAboveItsSections() {
            DrawnFrame frame = draw(sections(dumped("Cycles", AUDIT_TEXT), dumped("Conflicts", AUDIT_TEXT),
                    dumped("Cliques", CLIQUES_TEXT), dumped("Lossy", CLIQUES_TEXT)));
            assertEquals(1, frame.count(AUDIT_TEXT));
            assertEquals(1, frame.count(CLIQUES_TEXT));
            int audit = frame.find(AUDIT_TEXT).y();
            int cliques = frame.find(CLIQUES_TEXT).y();
            assertTrue(audit < header(frame, "Cycles").y() && audit < header(frame, "Conflicts").y());
            assertTrue(cliques > header(frame, "Conflicts").y());
            assertTrue(cliques < header(frame, "Cliques").y() && cliques < header(frame, "Lossy").y());
            assertTrue(frame.texts().stream().filter(text -> !text.equals(AUDIT_TEXT) && !text.equals(CLIQUES_TEXT))
                    .noneMatch(text -> text.endsWith(".txt") || text.endsWith(".json")), frame.texts().toString());
        }

        @Test
        void headerNameDrawsBoldBeforeItsPlainResults() {
            DrawnFrame frame = draw(sections(section("Clique tables (12 types)", "one line"),
                    section("Plain heading", "another line")));
            assertTrue(frame.bold("Clique tables"));
            DrawnFrame.Text name = frame.find("Clique tables");
            DrawnFrame.Text results = frame.find("(12 types)");
            assertFalse(frame.bold("(12 types)"));
            assertEquals(name.y(), results.y());
            assertTrue(results.x() > name.x());
            assertTrue(frame.bold("Plain heading"));
        }

        private static String dumped(String header, String dump) {
            return "{\"header\": \"" + header + "\", \"lines\": [\"" + header + " line\"], \"dump\": \"" + dump
                    + "\"}";
        }
    }

    private static String section(String header, String... lines) {
        return "{\"header\": \"" + header + "\", \"lines\": [\"" + String.join("\", \"", lines) + "\"]}";
    }

    private static String sections(String... sections) {
        return "{\"sections\": [" + String.join(",", sections) + "]}";
    }

    private static AuditPane pane(String sectionsJson) {
        AuditTab tab = new AuditTab();
        tab.readSections(sectionsJson);
        return new AuditPane(tab, new ManagerDraft(), mock(ManagerActions.class));
    }

    private static DrawnFrame draw(String sectionsJson) {
        return draw(pane(sectionsJson));
    }

    private static DrawnFrame draw(AuditPane pane) {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets);
        return frame;
    }

    private static DrawnFrame.Text header(DrawnFrame frame, String name) {
        return frame.drawn().stream().filter(text -> text.text().contains(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " not drawn in " + frame.texts()));
    }

    // The pieces a section's header line draws from its name rightward: name, results, badge or checkmark.
    private static List<DrawnFrame.Text> headerLine(DrawnFrame frame, String name) {
        DrawnFrame.Text start = header(frame, name);
        return frame.drawn().stream().filter(text -> text.y() == start.y() && text.x() >= start.x()).toList();
    }

    // The gray and red texts drawn straight after a section's header line: its body lines and its flags.
    private static List<DrawnFrame.Text> bodyOf(DrawnFrame frame, String name) {
        List<DrawnFrame.Text> drawn = frame.drawn();
        DrawnFrame.Text head = header(frame, name);
        int start = drawn.indexOf(head) + 1;
        while (start < drawn.size() && drawn.get(start).y() == head.y()) {
            start++;
        }
        int end = start;
        while (end < drawn.size() && BODY_COLORS.contains(drawn.get(end).color())) {
            end++;
        }
        return drawn.subList(start, end);
    }
}
