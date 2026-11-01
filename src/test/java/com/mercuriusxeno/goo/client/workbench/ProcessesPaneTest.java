package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                  "entries": [], "leading": null, "trailing": null, "closingGap": null},
                 {"key": "copper_oxidation_and_waxing_end1", "keySeparator": null, "kind": "VALUE",
                  "text": "\\"+$oak\\"", "entries": [], "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String LONG_NAME = "copper_oxidation_and_waxing_end1";
    private static final String DESCRIBED = """
            {"sections": [
              {"section": "_post_conversions", "processes": [
                 {"key": "#exposed", "kind": "ASSIGNMENT", "text": "\\"#copper @waxed\\"", "parsed": true},
                 {"key": "waxed", "kind": "ADDITIVE", "text": "\\"+$oak\\"", "parsed": true},
                 {"key": "copper_oxidation_and_waxing_end1", "kind": "ADDITIVE", "text": "\\"+$oak\\"",
                  "parsed": true}]}
            ]}
            """;

    private ProcessesPane pane;
    private final List<AbstractWidget> widgets = new ArrayList<>();

    @BeforeEach
    void readReplies() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(MODEL);
        ProcessesTab processes = new ProcessesTab(draft);
        processes.readDescribed(DESCRIBED);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        pane = new ProcessesPane(processes, draft, actions, null, () -> List.of("oak"));
    }

    /**
     * The tab stands a hinted search box above its blocks; typing in it keeps the entries whose name holds the text,
     * and a right click on it lists every entry again (decisions mutators-tab-renames-and-searches-by-name,
     * right-click-empties-every-text-box-in-the-manager).
     */
    @Test
    void searchBoxFiltersTheEntriesAndARightClickEmptiesIt() {
        pane.init(DrawnFrame.font());
        pane.show(true);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search mutators".equals(box.getMessage().getString())).findFirst().orElseThrow();
        assertTrue(search.visible);
        search.setValue("wax");
        DrawnFrame filtered = frame();
        assertTrue(search.getY() + search.getHeight() <= filtered.find("Mutators").y(), "search above the blocks");
        assertFalse(filtered.texts().contains("#exposed"), filtered.texts().toString());
        ValuesManagerScreen.emptyBoxUnder(widgets, new MouseButtonEvent(search.getX() + 1, search.getY() + 1,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)));
        assertTrue(frame().texts().contains("#exposed"));
    }

    /**
     * Conversions and post-conversions draw as sub-tabs, post-conversions shown first; a press on Conversions shows
     * its entries alone, and revealing an entry shows the sub-tab holding it (the operator's ruling).
     */
    @Test
    void subTabsShowOneSectionPostConversionsFirst() {
        String conversions = """
                {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                   {"key": "melted", "keySeparator": null, "kind": "VALUE", "text": "\\"+$oak\\"",
                    "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                 "leading": null, "trailing": null, "closingGap": null},
                """;
        ManagerDraft draft = new ManagerDraft();
        draft.read(MODEL.replace("{\"key\": \"_post_conversions\"", conversions + "{\"key\": \"_post_conversions\""));
        ProcessesTab processes = new ProcessesTab(draft);
        processes.readDescribed(DESCRIBED.replace("{\"sections\": [", """
                {"sections": [{"section": "_conversions", "processes": [
                   {"key": "melted", "kind": "ADDITIVE", "text": "\\"+$oak\\"", "parsed": true}]},"""));
        ManagerActions actions = mock(ManagerActions.class);
        pane = new ProcessesPane(processes, draft, actions, null, () -> List.of("oak"));
        assertEquals("_post_conversions", pane.shownSection());
        DrawnFrame post = frame();
        List<String> postTexts = post.texts();
        assertEquals("waxed", postTexts.get(postTexts.indexOf("Mutators") + 2), "the first row under Mutators: "
                + postTexts);
        // A filled chip's label draws with no shadow, through the overload the frame does not record, so each tab's
        // label is read while it stands hollow.
        assertTrue(post.texts().containsAll(List.of("Conversions", "waxed")), post.texts().toString());
        assertFalse(post.texts().contains("melted") || post.texts().contains("Post conversions"),
                post.texts().toString());
        post.click("Conversions");
        DrawnFrame pre = frame();
        assertTrue(pre.texts().containsAll(List.of("Post conversions", "melted")), pre.texts().toString());
        assertFalse(pre.texts().contains("waxed"), pre.texts().toString());
        pane.reveal("waxed");
        assertTrue(frame().texts().contains("waxed"));
    }

    /**
     * A row's pencil opens the name box in the name's place (decision rename-is-a-pencil-and-delete-a-red-x-everywhere).
     */
    @Test
    void pencilOpensTheNameBoxOverTheName() {
        pane.init(DrawnFrame.font());
        pane.show(true);
        DrawnFrame frame = frame();
        DrawnFrame.Text waxed = first(frame, "waxed");
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎") && text.y() == waxed.y())
                .findFirst().orElseThrow();
        assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1));
        frame();
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && "waxed".equals(each.getValue())).findFirst().orElseThrow();
        assertEquals(waxed.x(), box.getX());
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
        assertPlusBeside(frame, frame.find("Mutators"));
        assertPlusBeside(frame, frame.find("Iterators"));
        assertFalse(frame.texts().contains("New mutator"), frame.texts().toString());
        assertFalse(frame.texts().contains("New iterator"), frame.texts().toString());
    }

    /**
     * Every row's top line draws its name with a pencil just past it, then its kind dropdown and mark at the same
     * column lefts, and a red x at the row's right end, under no header (the operator's ruling: pencil by the name,
     * x at the row end).
     */
    @Test
    void everyRowDrawsItsColumnsAtTheSameLefts() {
        DrawnFrame frame = frame();
        List<DrawnFrame.Text> drawn = frame.drawn();
        for (String column : List.of("✗", "✔")) {
            List<Integer> lefts = drawn.stream().filter(text -> text.text().equals(column)).map(DrawnFrame.Text::x)
                    .distinct().toList();
            assertEquals(1, lefts.size(), column + " at " + lefts);
        }
        int nameLeft = first(frame, "waxed").x();
        assertEquals(nameLeft, frame.find("#exposed").x());
        int kindLeft = frame.find(ControlFlow.dropdownLabel("additive")).x();
        assertTrue(kindLeft > nameLeft + 20 * 6, "the kind stands past the name column at " + kindLeft);
        List<DrawnFrame.Text> pencils = drawn.stream().filter(text -> text.text().equals("✎")).toList();
        assertEquals(3, pencils.size(), frame.texts().toString());
        DrawnFrame.Text waxed = first(frame, "waxed");
        int pastName = waxed.x() + "waxed".length() * 6;
        assertTrue(pencils.stream().anyMatch(pencil -> pencil.y() == waxed.y() && pencil.x() > pastName
                && pencil.x() < pastName + 12), pencils + " vs " + waxed);
        DrawnFrame.Text cross = frame.find("✗");
        assertEquals(590 - ScrollBar.WIDTH - ScrollBar.GAP - 6 - ButtonPainter.PADDING, cross.x() ,
                "the x stands at the row's right end");
        assertFalse(frame.texts().contains("Rename") || frame.texts().contains("Delete"), frame.texts().toString());
        assertFalse(frame.texts().contains("Kind:"), frame.texts().toString());
    }

    /**
     * A name past 24 characters draws its first 20, an ellipsis and its last 4, and hovers whole.
     */
    @Test
    void longNameCutsToTwentyAnEllipsisAndFourAndHoversWhole() {
        assertEquals("copper_oxidation_and…end1", ProcessesPane.shownName(LONG_NAME));
        assertEquals("waxed", ProcessesPane.shownName("waxed"));
        DrawnFrame frame = frame();
        frame.find("copper_oxidation_and…end1");
        assertTrue(frame.targets.hoverKeys().contains(BuilderOverlays.hintKey(LONG_NAME)),
                frame.targets.hoverKeys().toString());
    }

    /**
     * Delete's box clears the one-pixel rule below its row, and a row of one sentence line takes the tighter pitch
     * (decision mutator-delete-clears-a-thinner-rule-and-lines-tighten).
     */
    @Test
    void deleteClearsTheOnePixelRuleAndRowsTighten() {
        DrawnFrame frame = frame();
        int waxedTop = first(frame, "waxed").y();
        int longTop = frame.find("copper_oxidation_and…end1").y();
        assertEquals(ProcessesPane.ONE_LINE_ROW_PITCH, longTop - waxedTop);
        assertTrue(ProcessesPane.ONE_LINE_ROW_PITCH < 36, "the pitch was 36 before");
        int deleteBottom = frame.drawn().stream().filter(text -> text.text().equals("✗")
                && text.y() < longTop - 4).mapToInt(DrawnFrame.Text::y).max().orElseThrow() - 3
                + ButtonPainter.HEIGHT;
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(1)).fill(anyInt(), tops.capture(), anyInt(), bottoms.capture(),
                eq(RowLayout.SEPARATOR_COLOR));
        int rule = tops.getAllValues().stream().filter(top -> top > waxedTop && top < longTop).findFirst()
                .orElseThrow();
        assertEquals(rule + 1, (int) bottoms.getAllValues().get(tops.getAllValues().indexOf(rule)));
        assertTrue(deleteBottom <= rule, "Delete ends at " + deleteBottom + ", the rule at " + rule);
    }

    // The row's name, drawn before any chain link naming it.
    private static DrawnFrame.Text first(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label)).findFirst().orElseThrow();
    }

    // A [+] stands right of the heading on its line.
    private static void assertPlusBeside(DrawnFrame frame, DrawnFrame.Text heading) {
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("+") && text.x() > heading.x()
                && Math.abs(text.y() - heading.y()) < ExpressionLines.TEXT_LINE_HEIGHT), frame.drawn().toString());
    }

    // The control stands right of the heading, its label within a text line of the heading's.
    private static void assertOnOneLine(DrawnFrame.Text heading, DrawnFrame.Text control) {
        assertTrue(control.x() > heading.x(), heading + " " + control);
        assertTrue(Math.abs(control.y() - heading.y()) < ExpressionLines.TEXT_LINE_HEIGHT, heading + " " + control);
    }
}
