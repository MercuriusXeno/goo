package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the Audit tab's pane through drawn frames of recorded sections, with no Screen (decisions
 * audit-section-lines-word-wrap, sections-start-expanded-with-an-expand-collapse-all-button,
 * chevron-only-where-a-section-holds-more-than-one-row, section-chevron-becomes-a-button,
 * flagless-section-draws-green-with-a-checkmark).
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
            DrawnFrame.Text quiet = header(frame, "Quiet");
            assertTrue(quiet.text().contains(CHECKMARK), quiet.text());
            assertEquals(AuditPane.GREEN, quiet.color());
            DrawnFrame.Text loud = header(frame, "Loud");
            assertTrue(loud.text().contains("2 flags"), loud.text());
            assertFalse(loud.text().contains(CHECKMARK), loud.text());
            assertEquals(WHITE, loud.color());
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

    // The gray and red texts drawn straight after a section's header: its body lines and its flags.
    private static List<DrawnFrame.Text> bodyOf(DrawnFrame frame, String name) {
        List<DrawnFrame.Text> drawn = frame.drawn();
        int start = drawn.indexOf(header(frame, name)) + 1;
        int end = start;
        while (end < drawn.size() && BODY_COLORS.contains(drawn.get(end).color())) {
            end++;
        }
        return drawn.subList(start, end);
    }
}
