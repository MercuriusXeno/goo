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
    private ManagerDraft draft;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
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

    /**
     * Mutators lie in a left column and iterators in a right one, each scrolling alone under its own bar (decision
     * mutators-left-iterators-right-each-with-its-own-scroll-bar).
     */
    @Test
    void mutatorsLeftIteratorsRightEachScrollingAlone() {
        int many = 20;
        String entry = """
                {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "\\"%s\\"", "entries": [], "leading": null,
                 "trailing": null, "closingGap": null}""";
        String described = """
                {"key": "%s", "kind": "%s", "text": "\\"%s\\"", "parsed": true}""";
        List<String> entries = new ArrayList<>();
        List<String> processes = new ArrayList<>();
        for (int index = 0; index < many; index++) {
            entries.add(entry.formatted("m%02d".formatted(index), "+$oak"));
            processes.add(described.formatted("m%02d".formatted(index), "ADDITIVE", "+$oak"));
            entries.add(entry.formatted("#i%02d".formatted(index), "#copper @solo"));
            processes.add(described.formatted("#i%02d".formatted(index), "ASSIGNMENT", "#copper @solo"));
        }
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s],
                   "leading": null, "trailing": null, "closingGap": null}]}""".formatted(String.join(", ", entries)));
        ProcessesTab tab = new ProcessesTab(draft);
        tab.readDescribed("""
                {"sections": [{"section": "_post_conversions", "processes": [%s]}]}"""
                .formatted(String.join(", ", processes)));
        pane = new ProcessesPane(tab, draft, mock(ManagerActions.class), null, () -> List.of("oak"));
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 590, 390);
        int middle = (bounds.left() + bounds.right()) / 2;
        DrawnFrame frame = frame();
        List<DrawnFrame.Text> drawn = frame.drawn();
        DrawnFrame.Text mutators = frame.find("Mutators");
        DrawnFrame.Text iterators = frame.find("Iterators");
        assertTrue(mutators.x() < middle && iterators.x() >= middle, mutators + " " + iterators);
        assertEquals(mutators.y(), iterators.y(), "the headings stand side by side");
        assertTrue(drawn.stream().filter(text -> text.text().matches("m\\d\\d") && text.x() == mutators.x())
                .count() > 1, drawn.toString());
        assertTrue(drawn.stream().filter(text -> text.text().matches("m\\d\\d")).allMatch(text -> text.x() < middle),
                "a mutator row strays right: " + drawn);
        assertTrue(drawn.stream().filter(text -> text.text().matches("#i\\d\\d")).allMatch(text -> text.x() >= middle),
                "an iterator row strays left: " + drawn);
        int track = 50;
        assertTrue(pane.columnBar(false).thumb(0, track).height() < track, "the mutators column overflows");
        assertTrue(pane.columnBar(true).thumb(0, track).height() < track, "the iterators column overflows");
        int iteratorTop = first(frame, "#i00").y();
        int mutatorTop = first(frame, "m00").y();
        pane.scroll(middle - 100, -3);
        DrawnFrame scrolled = frame();
        assertEquals(iteratorTop, first(scrolled, "#i00").y(), "the iterators stay put");
        assertTrue(scrolled.drawn().stream().noneMatch(text -> text.text().equals("m00") && text.y() == mutatorTop),
                "the mutators scrolled");
    }

    /**
     * Each row's head line, name, pencil, kind, mark and x, draws inside its column left of the column's scroll bar,
     * no element over the next, at the wide pane and at a 480-wide screen's narrow one (the operator's report: the
     * kind stood behind the scroll bar and the x and the kind overflowed).
     */
    @Test
    void rowHeadsFitLeftOfTheirColumnsScrollBar() {
        for (int width : new int[] {590, 464}) {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, width, 390), frame.targets);
            int middle = width / 2;
            List<DrawnFrame.Text> drawn = frame.drawn();
            List<DrawnFrame.Text> crosses = drawn.stream().filter(text -> text.text().equals("✗")).toList();
            assertEquals(3, crosses.size(), drawn.toString());
            for (DrawnFrame.Text cross : crosses) {
                boolean right = cross.x() >= middle;
                int barLeft = (right ? width : middle - 6) - ScrollBar.WIDTH - ScrollBar.GAP;
                List<DrawnFrame.Text> head = drawn.stream().filter(text -> Math.abs(text.y() - cross.y()) <= 5
                        && text.x() >= middle == right).sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                        .toList();
                assertTrue(head.size() >= 3, head.toString());
                DrawnFrame.Text last = head.getLast();
                assertEquals("✗", last.text(), "the x stands last: " + head);
                assertTrue(last.x() + 6 + ButtonPainter.PADDING <= barLeft,
                        width + ": the x passes the bar at " + barLeft + ": " + head);
                for (int index = 1; index < head.size(); index++) {
                    DrawnFrame.Text before = head.get(index - 1);
                    assertTrue(before.x() + before.text().length() * 6 <= head.get(index).x(),
                            width + ": " + before + " runs into " + head.get(index));
                }
            }
        }
    }

    /**
     * Every tooltip hint a drawn Mutators frame registers reads Rename, Delete, a verdict and its reason, or a cut
     * name in full (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
     */
    @Test
    void everyTooltipHintIsRenameDeleteAVerdictOrACutName() {
        DrawnFrame frame = frame();
        List<String> hints = frame.targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                .flatMap(java.util.Optional::stream).map(component -> component.getString()).distinct().toList();
        assertTrue(hints.containsAll(List.of("Rename", "Delete", LONG_NAME)), hints.toString());
        assertTrue(hints.stream().allMatch(hint -> hint.equals("Rename") || hint.equals("Delete")
                || hint.equals(LONG_NAME) || hint.startsWith("Valid") || hint.startsWith("Invalid")), hints.toString());
    }

    private DrawnFrame frame() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 590, 390), frame.targets);
        return frame;
    }

    @Test
    void mutatorsAndIteratorsEachDrawUnderTheirOwnHeading() {
        DrawnFrame frame = frame();
        List<Integer> tops = frame.topsOf(List.of("Mutators", "waxed", "Iterators", "#exposed"));
        assertTrue(tops.get(0) < tops.get(1) && tops.get(2) < tops.get(3) && tops.get(0).equals(tops.get(2)),
                tops.toString());
        assertTrue(first(frame, "#exposed").x() > first(frame, "waxed").x(), frame.drawn().toString());
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
        int middle = 590 / 2;
        for (String column : List.of("✗", "✔")) {
            for (boolean right : new boolean[] {false, true}) {
                List<Integer> lefts = drawn.stream().filter(text -> text.text().equals(column)
                        && text.x() >= middle == right).map(DrawnFrame.Text::x).distinct().toList();
                assertEquals(1, lefts.size(), column + " at " + lefts);
            }
        }
        int nameLeft = first(frame, "waxed").x();
        assertEquals(nameLeft, first(frame, "copper_oxidation_and…end1").x());
        assertTrue(frame.find("#exposed").x() >= middle, "the iterator stands in the right column");
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

    /**
     * A row's open rename box takes a ✓ and a gold ↺ right of it on its row; ✓ renames the entry to the typed name,
     * as Enter does, and ↺ closes the box leaving the name, as Escape does (decisions
     * open-name-box-takes-a-done-checkmark, open-name-box-takes-a-gold-arrow-that-cancels).
     */
    @Test
    void renameBoxTakesADoneCheckAndANevermindArrow() {
        pane.init(DrawnFrame.font());
        pane.show(true);
        assertTrue(frame().targets.click(rowControl(frame(), "waxed", "✎").x() + 1,
                rowControl(frame(), "waxed", "✎").y() + 1));
        DrawnFrame open = frame();
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && "waxed".equals(each.getValue())).findFirst().orElseThrow();
        VariablesPaneTest.assertNameBoxButtons(open, box);
        box.setValue("other");
        open.click("↺");
        assertFalse(box.visible);
        assertTrue(postConversionKeys().contains("waxed"), postConversionKeys().toString());
        DrawnFrame list = frame();
        assertTrue(list.targets.click(rowControl(list, "waxed", "✎").x() + 1, rowControl(list, "waxed", "✎").y() + 1));
        DrawnFrame reopened = frame();
        box.setValue("renamed");
        reopened.click("✓");
        assertTrue(postConversionKeys().contains("renamed"), postConversionKeys().toString());
        assertFalse(postConversionKeys().contains("waxed"), postConversionKeys().toString());
    }

    // The rightmost control labelled glyph on the head line of the row named key, inside that row's column: the
    // frame's 590 pixels part into two columns, so a row's controls stand within 295 of its name.
    private static DrawnFrame.Text rowControl(DrawnFrame frame, String key, String glyph) {
        DrawnFrame.Text name = first(frame, key);
        return frame.drawn().stream().filter(text -> text.text().equals(glyph) && text.x() > name.x()
                && text.x() < name.x() + 295
                && Math.abs(text.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT)
                .max(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                .orElseThrow(() -> new AssertionError(glyph + " on " + key + " in " + frame.drawn()));
    }

    private List<String> postConversionKeys() {
        return draft.sectionEntries("_post_conversions").asList().stream()
                .map(entry -> entry.getAsJsonObject().get("key").getAsString()).toList();
    }

    /**
     * A mutator's and an iterator's ✗ marks the entry: the row still draws with a gold ↺ hinting Restore where the ✗
     * stood and the section still holds the key; the ↺ restores the ✗; with the mark standing, the save path drops
     * the entry (decision every-delete-marks-for-removal-and-a-gold-arrow-restores).
     *
     * @param key the mutator waxed in the left column, or the iterator #exposed in the right
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"waxed", "#exposed"})
    void xMarksTheEntryAndTheArrowRestoresIt(String key) {
        DrawnFrame list = frame();
        DrawnFrame.Text cross = rowControl(list, key, "✗");
        assertTrue(list.targets.click(cross.x() + 1, cross.y() + 1));
        DrawnFrame marked = frame();
        DrawnFrame.Text arrow = rowControl(marked, key, "↺");
        assertEquals(cross.x(), arrow.x());
        assertEquals(ButtonPainter.GOLD, arrow.color());
        assertEquals(java.util.Optional.of(BuilderOverlays.hintKey("Restore")),
                marked.targets.hovered(arrow.x() + 1, arrow.y() + 1));
        assertTrue(postConversionKeys().contains(key), postConversionKeys().toString());
        assertTrue(marked.targets.click(arrow.x() + 1, arrow.y() + 1));
        DrawnFrame restored = frame();
        DrawnFrame.Text again = rowControl(restored, key, "✗");
        assertEquals(ProcessesPane.RED, again.color());
        assertTrue(restored.targets.click(again.x() + 1, again.y() + 1));
        assertEquals(List.of("_post_conversions\n" + key), draft.removals().marked(RemovalMarks.Tab.MUTATORS));
        pane.applyRemovals();
        assertFalse(postConversionKeys().contains(key), postConversionKeys().toString());
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
