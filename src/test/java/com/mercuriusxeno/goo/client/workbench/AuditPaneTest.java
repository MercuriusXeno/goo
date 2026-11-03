package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the Audit tab's pane through one drawn frame of recorded sections, with no Screen (decision
 * audit-section-lines-word-wrap).
 */
class AuditPaneTest {
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 0, 200, 400);
    private static final int TEXT_WIDTH = VIEW.right() - VIEW.left() - ScrollBar.WIDTH - ScrollBar.GAP;
    private static final String LONG_LINE = "minecraft:cobblestone remainders 2 items solved 40 via the rock root"
            + " and the metal root";

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

    private static String section(String header, String line) {
        return "{\"header\": \"" + header + "\", \"lines\": [\"" + line + "\"]}";
    }

    private static String sections(String... sections) {
        return "{\"sections\": [" + String.join(",", sections) + "]}";
    }

    private static DrawnFrame draw(String sectionsJson) {
        AuditTab tab = new AuditTab();
        tab.readSections(sectionsJson);
        AuditPane pane = new AuditPane(tab, new ManagerDraft(), mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets);
        return frame;
    }

    private static DrawnFrame.Text header(DrawnFrame frame, String name) {
        return frame.drawn().stream().filter(text -> text.text().contains(name)).findFirst()
                .orElseThrow(() -> new AssertionError(name + " not drawn in " + frame.texts()));
    }

    // The texts drawn after a section's header and before the next header, which the pane draws white.
    private static List<DrawnFrame.Text> bodyOf(DrawnFrame frame, String name) {
        List<DrawnFrame.Text> drawn = frame.drawn();
        int start = drawn.indexOf(header(frame, name)) + 1;
        int end = start;
        while (end < drawn.size() && drawn.get(end).color() != header(frame, name).color()) {
            end++;
        }
        return drawn.subList(start, end);
    }
}
