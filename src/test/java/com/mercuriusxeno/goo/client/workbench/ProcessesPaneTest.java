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
import static org.mockito.Mockito.atLeastOnce;
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
              {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
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
              {"section": "_conversions", "processes": [
                 {"key": "#exposed", "kind": "ASSIGNMENT", "text": "\\"#copper @waxed\\"", "parsed": true},
                 {"key": "waxed", "kind": "ADDITIVE", "text": "\\"+$oak\\"", "parsed": true},
                 {"key": "copper_oxidation_and_waxing_end1", "kind": "ADDITIVE", "text": "\\"+$oak\\"",
                  "parsed": true}]}
            ]}
            """;

    private ProcessesPane pane;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private ManagerDraft draft;
    private ProcessesTab processes;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
        draft.read(MODEL);
        processes = new ProcessesTab(draft);
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
     * The pane leads with its entries under no sub-tab: the first row under Mutators is the first mutator, and no
     * Conversions or Post conversions control draws (decision conversions-is-the-one-section-and-the-pane-name).
     */
    @Test
    void paneLeadsWithItsEntriesUnderNoSubTab() {
        DrawnFrame frame = frame();
        List<String> texts = frame.texts();
        assertEquals("waxed", texts.get(texts.indexOf("Mutators") + 2), "the first row under Mutators: " + texts);
        assertFalse(texts.contains("Conversions") || texts.contains("Post conversions"), texts.toString());
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
        pane = manyRowsPane();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 590, 390);
        int middle = (bounds.right() - bounds.left()) * 3 / 5;
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
     * In a pane 500 wide, the iterators column starts at three fifths, 300, plus half the column gap, the mutators
     * rows' x stands left of 300, and the wheel scrolls the column it turns over (decision
     * mutators-iterators-split-sixty-forty).
     */
    @Test
    void wheelScrollsTheColumnItTurnsOver() {
        pane = manyRowsPane();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 390);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), bounds, frame.targets);
        assertEquals(300 + 12 / 2, frame.find("Iterators").x());
        DrawnFrame.Text firstMutator = first(frame, "m00");
        assertTrue(rowControl(frame, "m00", "✗").x() < 300, frame.drawn().toString());
        assertTrue(firstMutator.x() < 300);
        pane.scroll(320, -3);
        assertTrue(pane.columnBar(true).offset() > 0, "the wheel at 320 scrolls the iterators");
        assertEquals(0, pane.columnBar(false).offset());
        pane.scroll(280, -3);
        assertTrue(pane.columnBar(false).offset() > 0, "the wheel at 280 scrolls the mutators");
    }

    /**
     * An additive's prefab reads after $ and an iterator's source group after #, and the additive's prefab pick
     * lists its prefabs after $ (decision hash-marks-groups-and-dollar-marks-prefabs-everywhere).
     */
    @Test
    void conversionsMarkPrefabsWithDollarAndGroupsWithHash() {
        DrawnFrame frame = frame();
        assertTrue(frame.texts().contains("$oak"), frame.texts().toString());
        assertTrue(frame.texts().contains(ControlFlow.dropdownLabel("#copper")), frame.texts().toString());
        frame.click("$oak");
        assertTrue(frame().texts().contains("$oak"), "the prefab pick lists $oak");
        assertEquals(2, frame().count("$oak"), frame().texts().toString());
    }

    /**
     * An iterator's own name previews the group it fills and its source previews the group it copies (decision
     * group-referent-previews-shorthand-members-then-its-value).
     */
    @Test
    void iteratorNameAndSourcePreviewTheirGroups() {
        DrawnFrame frame = frame();
        DrawnFrame.Text name = frame.find("#exposed");
        assertEquals(java.util.Optional.of(GroupHovers.hoverKey("exposed")),
                frame.targets.hovered(name.x() + 1, name.y() + 1));
        DrawnFrame.Text source = frame.find(ControlFlow.dropdownLabel("#copper"));
        assertEquals(java.util.Optional.of(GroupHovers.hoverKey("copper")),
                frame.targets.hovered(source.x() + 1, source.y() + 1));
    }

    // A Mutators pane over the iterators #twice, applying waxed then rust, and #bare, applying nothing.
    private ProcessesPane chainedIterators(ManagerActions actions) {
        String entry = """
                {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "\\"%s\\"", "entries": [], "leading": null,
                 "trailing": null, "closingGap": null}""";
        String described = """
                {"key": "%s", "kind": "%s", "text": "\\"%s\\"", "parsed": true}""";
        ManagerDraft chained = new ManagerDraft();
        chained.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "copper", "keySeparator": null, "kind": "VALUE", "text": "[\\"copper_block\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s]
                   , "leading": null, "trailing": null, "closingGap": null}]}""".formatted(String.join(", ",
                entry.formatted("#twice", "#copper @waxed @rust"), entry.formatted("#bare", "#copper"),
                entry.formatted("waxed", "+$oak"), entry.formatted("rust", "+$oak"))));
        ProcessesTab tab = new ProcessesTab(chained);
        tab.readDescribed("""
                {"sections": [{"section": "_conversions", "processes": [%s]}]}""".formatted(String.join(", ",
                described.formatted("#twice", "ASSIGNMENT", "#copper @waxed @rust"),
                described.formatted("#bare", "ASSIGNMENT", "#copper"),
                described.formatted("waxed", "ADDITIVE", "+$oak"), described.formatted("rust", "ADDITIVE", "+$oak"))));
        return new ProcessesPane(tab, chained, actions, null, () -> List.of("oak"));
    }

    /**
     * An iterator reads #X on its name line, "copies" and its source one indent in on the next, then "then mutates
     * using" before its first mutator and "and" before each later one, each a line down, then "+ optional
     * mutation"; one applying nothing reads "copies" and the button alone, which opens the mutator pick (decision
     * iterator-reads-copies-then-mutates-using-and).
     */
    @Test
    void iteratorReadsCopiesThenMutatesUsingAnd() {
        pane = chainedIterators(mock(ManagerActions.class));
        DrawnFrame frame = frame();
        assertFalse(frame.texts().contains("loops over") || frame.texts().contains("and applies"),
                frame.texts().toString());
        DrawnFrame.Text twice = first(frame, "#twice");
        DrawnFrame.Text bare = first(frame, "#bare");
        List<DrawnFrame.Text> copies = frame.drawn().stream().filter(text -> text.text().equals("copies")).toList();
        List<DrawnFrame.Text> adds = frame.drawn().stream().filter(text -> text.text().equals("+ optional mutation"))
                .toList();
        assertEquals(2, copies.size(), frame.texts().toString());
        assertEquals(2, adds.size(), frame.texts().toString());
        DrawnFrame.Text twiceCopies = twice.y() < bare.y() ? copies.getFirst() : copies.getLast();
        DrawnFrame.Text twiceAdd = twice.y() < bare.y() ? adds.getFirst() : adds.getLast();
        DrawnFrame.Text then = frame.find("mutated by");
        DrawnFrame.Text and = frame.find("and");
        List<Integer> descending = List.of(twice.y(), twiceCopies.y(), then.y(), and.y(), twiceAdd.y());
        assertEquals(descending.stream().sorted().distinct().toList(), descending, frame.drawn().toString());
        assertEquals(twice.x() + ControlFlow.INDENT, twiceCopies.x(), frame.drawn().toString());
        assertEquals(1, frame.count("mutated by"), frame.texts().toString());
        assertFalse(frame.texts().contains("then mutates using"), frame.texts().toString());
        DrawnFrame.Text bareAdd = twice.y() < bare.y() ? adds.getLast() : adds.getFirst();
        assertTrue(bareAdd.y() > bare.y(), bareAdd + " under " + bare);
        assertTrue(frame.targets.click(bareAdd.x() + 1, bareAdd.y() + 1));
        assertTrue(frame().texts().containsAll(List.of("waxed", "rust")), "the mutator pick lists the mutators");
    }

    /**
     * Each mutation line right packs against a column edge a gap left of the row's validity marker: its x ends at
     * the edge, the mutator buttons share one x, each line's words stand one gap left of its button, and
     * "+ optional mutation" ends at the edge too (decisions iterator-reads-copies-then-mutates-using-and,
     * mutator-chain-forms-a-column-left-of-the-validity-marker).
     */
    @Test
    void iteratorMutationLinesRightPackWithTheirButtonsAligned() {
        pane = chainedIterators(mock(ManagerActions.class));
        DrawnFrame frame = frame();
        DrawnFrame.Text then = frame.find("mutated by");
        DrawnFrame.Text and = frame.find("and");
        DrawnFrame.Text waxed = onLineOf(frame, then, "waxed");
        DrawnFrame.Text rust = onLineOf(frame, and, "rust");
        DrawnFrame.Text marker = frame.drawn().stream().filter(text -> text.text().equals("✔") && text.x() > then.x())
                .findFirst().orElseThrow();
        int edge = marker.x() - BuilderOverlays.GAP;
        assertTrue(edge < boxRightAt(frame, then.x(), then.y()), "the column ends inside the box");
        assertEquals(edge, rightOf(onLineOf(frame, then, "x")), frame.drawn().toString());
        assertEquals(edge, rightOf(onLineOf(frame, and, "x")), frame.drawn().toString());
        assertEquals(waxed.x(), rust.x(), waxed + " vs " + rust);
        int buttonLeft = waxed.x() - ButtonPainter.PADDING;
        assertEquals(buttonLeft - BuilderOverlays.GAP, then.x() + DrawnFrame.font().width(then.text()), then.toString());
        assertEquals(buttonLeft - BuilderOverlays.GAP, and.x() + DrawnFrame.font().width(and.text()), and.toString());
        DrawnFrame.Text add = frame.drawn().stream().filter(text -> text.text().equals("+ optional mutation")
                && text.y() > and.y()).findFirst().orElseThrow();
        assertEquals(edge, rightOf(add), add.toString());
    }

    /**
     * A mutator's and an iterator's rename pencil stands the rule gap under the rule above its row and the button
     * gap past its name, and the row above keeps its last buttons the rule gap over that rule (decision
     * spacing-rules-are-one-set-across-every-pane).
     */
    @Test
    void rowsKeepTheRuleAndButtonGaps() {
        pane = chainedIterators(mock(ManagerActions.class));
        DrawnFrame frame = frame();
        assertRowSpacing(frame, "waxed", "rust", "$oak");
        assertRowSpacing(frame, "#twice", "#bare", "+ optional mutation");
    }

    // The second row's pencil against the rule above it, and the first row's last button against that rule.
    private static void assertRowSpacing(DrawnFrame frame, String firstName, String secondName, String lastButton) {
        DrawnFrame.Text first = first(frame, firstName);
        DrawnFrame.Text second = first(frame, secondName);
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")
                && text.x() > second.x() && Math.abs(text.y() - second.y()) <= 1).findFirst()
                .orElseThrow(() -> new AssertionError("no pencil by " + second + " in " + frame.drawn()));
        int pencilTop = pencil.y() - ButtonPainter.TEXT_NUDGE;
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), anyInt(), anyInt(),
                eq(RowLayout.SEPARATOR_COLOR));
        int rule = java.util.stream.IntStream.range(0, tops.getAllValues().size())
                .filter(index -> lefts.getAllValues().get(index) <= second.x())
                .map(index -> tops.getAllValues().get(index)).filter(top -> top < pencilTop).max().orElseThrow();
        assertEquals(rule + 1 + RowLayout.RULE_GAP, pencilTop, secondName + "'s pencil under the rule at " + rule);
        assertEquals(second.x() + DrawnFrame.font().width(second.text()) + RowLayout.BUTTON_GAP,
                pencil.x() - ButtonPainter.PADDING, secondName + "'s pencil past its name");
        DrawnFrame.Text last = frame.drawn().stream().filter(text -> text.text().equals(lastButton)
                && text.y() > first.y() && text.y() < second.y()).reduce((earlier, later) -> later).orElseThrow();
        int lastBottom = last.y() - ButtonPainter.TEXT_NUDGE + ButtonPainter.HEIGHT;
        assertEquals(RowLayout.RULE_GAP, rule - lastBottom, firstName + "'s last button over the rule");
    }

    private static DrawnFrame.Text onLineOf(DrawnFrame frame, DrawnFrame.Text anchor, String label) {
        return frame.drawn().stream().filter(text -> text.y() == anchor.y() && text.text().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError(label + " on " + anchor + ": " + frame.drawn()));
    }

    private static int rightOf(DrawnFrame.Text button) {
        return button.x() + DrawnFrame.font().width(button.text()) + ButtonPainter.PADDING;
    }

    // The right edge of the row box filled across a point.
    private static int boxRightAt(DrawnFrame frame, int x, int y) {
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), rights.capture(), bottoms.capture(),
                eq(ProcessesPane.ROW_COLOR));
        for (int index = 0; index < tops.getAllValues().size(); index++) {
            if (lefts.getAllValues().get(index) <= x && rights.getAllValues().get(index) > x
                    && tops.getAllValues().get(index) <= y && bottoms.getAllValues().get(index) > y) {
                return rights.getAllValues().get(index);
            }
        }
        throw new AssertionError("no row box at " + y);
    }

    /**
     * A mutator's sentence draws on the line under its name line, unindented, its kind dropdown at the name's x
     * (decision mutator-row-is-kind-then-type-reading-add-and-no-applies).
     */
    @Test
    void mutatorSentenceStartsAtTheNameWithItsKind() {
        DrawnFrame frame = frame();
        List<DrawnFrame.Text> drawn = frame.drawn();
        DrawnFrame.Text waxed = first(frame, "waxed");
        DrawnFrame.Text kind = drawn.get(drawn.indexOf(waxed) + 4);
        assertEquals(ControlFlow.dropdownLabel("add"), kind.text(), drawn.toString());
        assertTrue(kind.y() > waxed.y(), kind + " below " + waxed);
        assertEquals(waxed.x() + ButtonPainter.PADDING, kind.x(), drawn.toString());
    }

    // A Mutators pane over twenty mutators and twenty iterators, each column overflowing a 390-tall pane.
    private static ProcessesPane manyRowsPane() {
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
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s],
                   "leading": null, "trailing": null, "closingGap": null}]}""".formatted(String.join(", ", entries)));
        ProcessesTab tab = new ProcessesTab(draft);
        tab.readDescribed("""
                {"sections": [{"section": "_conversions", "processes": [%s]}]}"""
                .formatted(String.join(", ", processes)));
        return new ProcessesPane(tab, draft, mock(ManagerActions.class), null, () -> List.of("oak"));
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
            int middle = width * 3 / 5;
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
     * Every plain tooltip hint a drawn Mutators frame registers reads Rename, Delete, or a verdict and its reason; a
     * cut name hovers in full above its formula instead (decisions
     * values-manager-controls-carry-no-tooltip-but-rename-delete-and-names, conversion-controls-hover-their-formula).
     */
    @Test
    void everyTooltipHintIsRenameDeleteOrAVerdict() {
        DrawnFrame frame = frame();
        List<String> hints = frame.targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                .flatMap(java.util.Optional::stream).map(component -> component.getString()).distinct().toList();
        assertTrue(hints.containsAll(List.of("Rename", "Delete")), hints.toString());
        assertTrue(hints.stream().allMatch(hint -> hint.equals("Rename") || hint.equals("Delete")
                || hint.startsWith("Valid") || hint.startsWith("Invalid")), hints.toString());
    }

    private DrawnFrame frame() {
        return frame(590);
    }

    // A pane 740 wide gives the mutators column its three fifths, 444, more than a 20-character cut name takes.
    private static final int WIDE_PANE = 740;

    private DrawnFrame frame(int width) {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, width, 390), frame.targets);
        return frame;
    }

    /**
     * The mutators column takes 60 percent of the pane and the iterators column 40, the gap centered on the split,
     * and each column's scroll bar stands at its own right edge (decision mutators-iterators-split-sixty-forty).
     */
    @Test
    void columnsSplitThePaneSixtyForty() {
        int width = 590;
        DrawnFrame frame = frame(width);
        int middle = width * 3 / 5;
        int halfGap = 6;
        assertEquals(middle + halfGap, frame.find("Iterators").x());
        assertEquals(0, frame.find("Mutators").x());
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(1)).fill(lefts.capture(), anyInt(), rights.capture(), anyInt(),
                eq(0xC0C0C0C0));
        List<List<Integer>> thumbs = new ArrayList<>();
        for (int index = 0; index < lefts.getAllValues().size(); index++) {
            thumbs.add(List.of(lefts.getAllValues().get(index), rights.getAllValues().get(index)));
        }
        assertEquals(List.of(List.of(middle - halfGap - ScrollBar.WIDTH, middle - halfGap),
                List.of(width - ScrollBar.WIDTH, width)), thumbs);
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
     * Every row's top line draws its name with a pencil just past it, its mark at one column left, and a red x at the
     * row's right end, under no header (the operator's ruling: pencil by the name, x at the row end).
     */
    @Test
    void everyRowDrawsItsColumnsAtTheSameLefts() {
        DrawnFrame frame = frame();
        List<DrawnFrame.Text> drawn = frame.drawn();
        int middle = 590 * 3 / 5;
        for (String column : List.of("✗", "✔")) {
            for (boolean right : new boolean[] {false, true}) {
                List<Integer> lefts = drawn.stream().filter(text -> text.text().equals(column)
                        && text.x() >= middle == right).map(DrawnFrame.Text::x).distinct().toList();
                assertEquals(1, lefts.size(), column + " at " + lefts);
            }
        }
        int nameLeft = first(frame, "waxed").x();
        assertEquals(nameLeft, drawn.stream().filter(text -> text.text().startsWith("copper_oxid")).findFirst()
                .orElseThrow().x());
        assertTrue(frame.find("#exposed").x() >= middle, "the iterator stands in the right column");
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
     * A name past 24 characters draws its first 20, an ellipsis and its last 4, and hovers its whole name above its
     * formula; a whole name hovers its formula alone, where it hovered nothing (decisions
     * conversion-controls-hover-their-formula, values-manager-controls-carry-no-tooltip-but-rename-delete-and-names;
     * the operator's report of 2026-11-04: a stack's cut name hovered its name alone and a convert's whole name
     * hovered nothing).
     */
    @Test
    void mutatorNameHoversItsFormulaAndACutNameItsWholeNameAboveIt() {
        assertEquals("copper_oxidation_and…end1", ProcessesPane.shownName(LONG_NAME));
        assertEquals("waxed", ProcessesPane.shownName("waxed"));
        DrawnFrame frame = frame(WIDE_PANE);
        DrawnFrame.Text waxed = first(frame, "waxed");
        assertEquals(java.util.Optional.of(ConversionHovers.hoverKey(SECTION, "waxed")),
                frame.targets.hovered(waxed.x() + 1, waxed.y() + 1));
        DrawnFrame.Text cut = frame.find("copper_oxidation_and…end1");
        String cutKey = frame.targets.hovered(cut.x() + 1, cut.y() + 1).orElseThrow();
        List<TooltipLine> lines = ConversionHovers.tooltipFor(processes, new VariablesTab(draft), cutKey)
                .orElseThrow(() -> new AssertionError(cutKey + " shows no tooltip"));
        assertEquals(new TooltipLine.Text(net.minecraft.network.chat.Component.literal(LONG_NAME)), lines.getFirst());
        assertTrue(lines.size() > 1, "the formula follows the name: " + lines);
    }

    private static final String SECTION = "_conversions";

    /**
     * Delete's box clears the one-pixel rule below its row, and a row of one sentence line takes the tighter pitch
     * (decision mutator-delete-clears-a-thinner-rule-and-lines-tighten).
     */
    @Test
    void deleteClearsTheOnePixelRuleAndRowsTighten() {
        DrawnFrame frame = frame(WIDE_PANE);
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
     * With a section whose payload carries a summary, the Mutators heading stands on the columns' first row under the
     * search box, and it is the first text the pane draws (decisions mutators-pane-draws-no-entry-count,
     * conversions-is-the-one-section-and-the-pane-name).
     */
    @Test
    void mutatorsHeadingStandsOnTheFirstRowUnderTheSearchBox() {
        DrawnFrame frame = frame();
        DrawnFrame.Text heading = frame.find("Mutators");
        assertEquals(ProcessesPane.SEARCH_ROW + ProcessesPane.HEADING_NUDGE, heading.y());
        List<DrawnFrame.Text> drawn = frame.drawn().stream()
                .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::y)).toList();
        assertEquals("Mutators", drawn.getFirst().text(), drawn.toString());
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

    // The rightmost control labelled glyph on the head line of the row named key, inside that row's column: a
    // mutator's controls stand left of the Iterators heading.
    private static DrawnFrame.Text rowControl(DrawnFrame frame, String key, String glyph) {
        DrawnFrame.Text name = first(frame, key);
        int split = frame.find("Iterators").x();
        return frame.drawn().stream().filter(text -> text.text().equals(glyph) && text.x() > name.x()
                && (name.x() >= split || text.x() < split)
                && Math.abs(text.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT)
                .max(java.util.Comparator.comparingInt(DrawnFrame.Text::x))
                .orElseThrow(() -> new AssertionError(glyph + " on " + key + " in " + frame.drawn()));
    }

    private List<String> postConversionKeys() {
        return draft.sectionEntries("_conversions").asList().stream()
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
        assertEquals(List.of("_conversions\n" + key), draft.removals().marked(RemovalMarks.Tab.MUTATORS));
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
