package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the Mutators tab's drawn layout from a recorded processes reply, with mocked graphics and no Screen
 * (decisions group-assignments-read-as-iterators, mutators-and-iterators-each-take-a-create-control).
 */
class ProcessesPaneTest {
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "copper", "keySeparator": null, "kind": "VALUE", "text": "[\\"copper_block\\"]",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null},
              {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                 {"key": "#exposed", "keySeparator": null, "kind": "VALUE", "text": "\\"#copper @waxed\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null},
                 {"key": "waxed", "keySeparator": null, "kind": "VALUE", "text": "\\"+$oak\\"",
                  "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String DESCRIBED = """
            {"sections": [
              {"section": "_post_conversions", "processes": [
                 {"key": "#exposed", "kind": "ASSIGNMENT", "text": "\\"#copper @waxed\\"", "parsed": true},
                 {"key": "waxed", "kind": "ADDITIVE", "text": "\\"+$oak\\"", "parsed": true}]}
            ]}
            """;

    private ProcessesPane pane;

    @BeforeEach
    void readReplies() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(MODEL);
        ProcessesTab processes = new ProcessesTab(draft);
        processes.readDescribed(DESCRIBED);
        pane = new ProcessesPane(processes, draft, mock(ManagerActions.class), null, () -> List.of("oak"));
    }

    private DrawnFrame frame() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 590, 390), frame.targets);
        return frame;
    }

    @Test
    void mutatorsAndIteratorsEachDrawUnderTheirOwnHeading() {
        List<Integer> tops = frame().topsOf(List.of("Mutators", "waxed", "Iterators", "#exposed"));
        assertTrue(tops.get(0) < tops.get(1) && tops.get(1) < tops.get(2) && tops.get(2) < tops.get(3),
                tops.toString());
    }

    @Test
    void eachHeadingCarriesItsCreateControl() {
        DrawnFrame frame = frame();
        assertOnOneLine(frame.find("Mutators"), frame.find("New mutator"));
        assertOnOneLine(frame.find("Iterators"), frame.find("New iterator"));
    }

    // The control stands right of the heading, its label within a text line of the heading's.
    private static void assertOnOneLine(DrawnFrame.Text heading, DrawnFrame.Text control) {
        assertTrue(control.x() > heading.x(), heading + " " + control);
        assertTrue(Math.abs(control.y() - heading.y()) < ExpressionLines.TEXT_LINE_HEIGHT, heading + " " + control);
    }
}
