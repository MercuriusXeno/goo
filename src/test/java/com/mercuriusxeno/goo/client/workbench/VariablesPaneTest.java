package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Prefabs tab's search box at the pane's layout seam, with Font mocked and no Screen: where the box and the
 * list stand, when the box shows, and how the list's scroll bar follows the search (decisions
 * prefabs-tab-searches-its-list-by-name, every-tab-draws-a-scroll-bar).
 */
class VariablesPaneTest {
    private static final int PREFABS = 12;
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 20, 400, 120);

    private Font font;
    private GuiGraphicsExtractor graphics;
    private VariablesTab variables;
    private VariablesPane pane;

    private static String model() {
        String entries = IntStream.range(0, PREFABS).mapToObj(index -> """
                {"key": "prefab%d", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}""".formatted(index))
                .collect(Collectors.joining(", "));
        return """
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(entries);
    }

    @BeforeEach
    void standThePane() {
        font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        graphics = mock(GuiGraphicsExtractor.class);
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        variables = new VariablesTab(draft);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        pane = new VariablesPane(variables, draft, icons, actions, builder);
        pane.init(font);
        pane.show(true);
    }

    private void draw() {
        pane.extract(graphics, font, VIEW, new ClickTargets(), 0, 0);
    }

    private EditBox searchBox() {
        EditBox box = pane.searchBox();
        assertNotNull(box);
        return box;
    }

    /**
     * After init and a draw, the search box's top stands at the view's top and the first prefab's name draws below
     * the box's bottom.
     */
    @Test
    void searchBoxStandsAboveTheList() {
        draw();
        EditBox box = searchBox();
        assertEquals(VIEW.top(), box.getY());
        ArgumentCaptor<Integer> nameY = ArgumentCaptor.forClass(Integer.class);
        verify(graphics).text(eq(font), eq("prefab0"), anyInt(), nameY.capture(), anyInt());
        assertTrue(nameY.getValue() > box.getY() + box.getHeight());
    }

    /**
     * The box shows only while the Prefabs tab is the one shown.
     */
    @Test
    void searchBoxShowsWithTheTab() {
        pane.show(false);
        assertFalse(searchBox().visible);
        pane.show(true);
        assertTrue(searchBox().visible);
    }

    /**
     * With the list scrolled by the wheel, typing in the box returns the list to its top.
     */
    @Test
    void typingReturnsTheListToItsTop() {
        draw();
        pane.scroll(-1);
        pane.scroll(-1);
        assertTrue(pane.listBar().offset() > 0);
        searchBox().setValue("prefab1");
        assertEquals(0, pane.listBar().offset());
    }

    /**
     * Unsearched, the whole draft overflows the view, so the thumb is shorter than its track; a search keeping one
     * prefab fits the view, so the bar measures that block alone and the thumb fills the track.
     */
    @Test
    void scrollBarMeasuresTheFilteredPrefabsAlone() {
        draw();
        int track = 50;
        assertTrue(pane.listBar().thumb(0, track).height() < track);
        searchBox().setValue("prefab11");
        assertEquals(List.of("prefab11"), variables.listed().stream().map(VariablesTab.ConstantBlock::name).toList());
        draw();
        assertEquals(track, pane.listBar().thumb(0, track).height());
    }

    // The tops of every fill the draw made at one color, in draw order.
    private List<Integer> fillTopsAt(int color) {
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(), eq(color));
        return tops.getAllValues();
    }

    /**
     * Scrolled to its top, the first prefab's Rename and Delete borders start at least one pixel row below the list's
     * clip top; each block draws its Rename then its Delete, so the first two border fills are the first block's.
     */
    @Test
    void firstPrefabButtonsPadBelowTheListTop() {
        draw();
        ArgumentCaptor<Integer> clipTop = ArgumentCaptor.forClass(Integer.class);
        verify(graphics).enableScissor(anyInt(), clipTop.capture(), anyInt(), anyInt());
        List<Integer> borders = fillTopsAt(ButtonPainter.BORDER);
        assertTrue(borders.get(0) > clipTop.getValue(), "Rename top " + borders.get(0) + " vs " + clipTop.getValue());
        assertTrue(borders.get(1) > clipTop.getValue(), "Delete top " + borders.get(1) + " vs " + clipTop.getValue());
    }

    /**
     * The second prefab's Rename and Delete borders start at least two rows below the separator above it, leaving an
     * empty row between them.
     */
    @Test
    void laterPrefabButtonsPadBelowTheirSeparator() {
        draw();
        int separator = fillTopsAt(RowLayout.SEPARATOR_COLOR).get(0);
        List<Integer> borders = fillTopsAt(ButtonPainter.BORDER);
        assertTrue(borders.get(2) >= separator + 2, "Rename top " + borders.get(2) + " vs " + separator);
        assertTrue(borders.get(3) >= separator + 2, "Delete top " + borders.get(3) + " vs " + separator);
    }

    /**
     * In the delete confirm, a shift + left click on a $name reader opens that prefab, and a plain left click on it
     * runs nothing and leaves the confirm standing (decision shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void cascadeReaderFollowsOnShiftClickAndTakesNoPlainClick() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "base", "kind": "scalar", "value": "8", "error": null, "usedBy": [],
                                "readers": [{"id": "egg", "kind": "constant"}]}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        list.click("Delete");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        confirm.click("egg");
        verify(actions, never()).openVariable(anyString());
        DrawnFrame standing = new DrawnFrame();
        read.extract(standing.graphics, DrawnFrame.font(), VIEW, standing.targets, 0, 0);
        standing.shiftClick("egg");
        verify(actions).openVariable("egg");
    }

    /**
     * In a prefab's read view, a right click on a $name ref runs nothing, a shift + left click on it opens that
     * prefab's block and a plain left click on the same ref starts editing the block it sits in (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void readViewRefFollowsOnShiftClickAndEditsItsBlockOnPlainClick() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null},
                    {"key": "plank", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood / 4\\"", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        assertFalse(frame.rightClick("wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(prefabs.editing().isEmpty());
        frame.shiftClick("wood");
        verify(actions).openVariable("wood");
        assertTrue(prefabs.editing().isEmpty());
        frame.click("wood");
        assertEquals(java.util.Optional.of("plank"), prefabs.editing());
    }

    /**
     * A prefab used by more items than its line fits ends its used-by line on a ..+n button counting the rest
     * (decision more-button-reads-dot-dot-plus-n).
     */
    @Test
    void cutShortUsedByLineEndsOnDotDotPlusN() {
        String direct = IntStream.range(0, 40).mapToObj("\"minecraft:item%d\""::formatted)
                .collect(Collectors.joining(", "));
        variables.readEvaluated("""
                {"constants": [{"name": "prefab0", "kind": "scalar", "value": "8", "direct": [%s]}]}
                """.formatted(direct));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        assertTrue(frame.texts().stream().anyMatch(text -> text.matches("^\\.\\.\\+\\d+$")), frame.texts().toString());
    }
}
