package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
        verify(graphics).text(eq(font), eq("$prefab0"), anyInt(), nameY.capture(), anyInt());
        assertTrue(nameY.getValue() > box.getY() + box.getHeight());
    }

    /**
     * Consecutive prefabs stand one tighter pitch apart; it was 39 pixels for a number prefab used nowhere (decisions
     * prefab-lines-tighten-their-padding, prefab-head-is-name-pencil-and-value-then-used-by).
     */
    @Test
    void prefabRowsTakeTheTighterPitch() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, new GroupsPane.Bounds(0, 20, 400, 4000), frame.targets, 0, 0);
        List<Integer> tops = frame.topsOf(List.of("$prefab0", "$prefab1"));
        int pitch = tops.get(1) - tops.get(0);
        assertEquals(VariablesPane.UNUSED_PREFAB_PITCH, pitch);
        assertTrue(pitch - ExpressionLines.TEXT_LINE_HEIGHT < 39, "the pitch was 39 before the value's line");
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
    void wheelNotchMovesTheListOneWheelStep() {
        // Decision one-wheel-step-and-one-dialog-primitive.
        draw();
        pane.scroll(-1);
        assertEquals(ScrollBar.WHEEL_STEP, pane.listBar().offset());
    }

    /**
     * The search box stands 14 tall at the list's top and stretches to the [+] at the row's right end, one gap short
     * of it, and a prefab's rename pencil stands one pencil gap past its name (decision
     * one-search-row-per-list-with-its-create-control-beside-it).
     */
    @Test
    void searchRowStretchesToItsCreateControlAndThePencilStandsOneGapPastTheName() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        EditBox box = searchBox();
        int[] plus = frame.buttonAround(frame.drawn().stream().filter(text -> text.text().equals("+")
                && text.y() < VIEW.top() + SearchRow.HEIGHT).findFirst().orElseThrow());
        assertEquals(List.of(VIEW.top(), SearchRow.HEIGHT, plus[0] - SearchRow.GAP),
                List.of(box.getY(), box.getHeight(), box.getX() + box.getWidth()));
        assertEquals(VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP, plus[2], "the [+] at the row's right end");
        DrawnFrame.Text name = frame.findFirst("$prefab0");
        assertEquals(name.x() + DrawnFrame.font().width(name.text()) + ButtonPainter.PENCIL_GAP,
                frame.buttonAround(frame.findFirst("✎"))[0]);
    }

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
        List<Integer> borders = fillTopsAt(Palette.BORDER);
        assertTrue(borders.get(0) > clipTop.getValue(), "Rename top " + borders.get(0) + " vs " + clipTop.getValue());
        assertTrue(borders.get(1) > clipTop.getValue(), "Delete top " + borders.get(1) + " vs " + clipTop.getValue());
    }

    /**
     * The second prefab's Rename and Delete borders start at least two rows below the separator above it, leaving an
     * empty row between them; each block draws its Rename, its Delete, then its value's frame and pencil.
     */
    @Test
    void laterPrefabButtonsPadBelowTheirSeparator() {
        draw();
        int separator = fillTopsAt(Palette.SEPARATOR).get(0);
        List<Integer> borders = fillTopsAt(Palette.BORDER);
        int bordersPerBlock = 4;
        int rename = borders.get(bordersPerBlock);
        int delete = borders.get(bordersPerBlock + 1);
        assertTrue(rename >= separator + 2, "Rename top " + rename + " vs " + separator);
        assertTrue(delete >= separator + 2, "Delete top " + delete + " vs " + separator);
    }

    /**
     * Every fill, text and sprite a prefab draws stands wholly on one side of each separator rule, a text taking the
     * game font's nine-pixel line and a sprite sixteen, so no rule crosses a prefab's contents (decision
     * prefab-lines-tighten-their-padding).
     */
    @Test
    void noDrawnElementCrossesASeparator() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> colors = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), anyInt(), bottoms.capture(),
                colors.capture());
        List<Integer> separators = new java.util.ArrayList<>();
        List<int[]> spans = new java.util.ArrayList<>();
        int listRight = TALL_VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        for (int index = 0; index < tops.getAllValues().size(); index++) {
            if (colors.getAllValues().get(index) == Palette.SEPARATOR) {
                separators.add(tops.getAllValues().get(index));
            } else if (lefts.getAllValues().get(index) < listRight) {
                spans.add(new int[] {tops.getAllValues().get(index), bottoms.getAllValues().get(index)});
            }
        }
        frame.drawn().forEach(text -> spans.add(new int[] {text.y(), text.y() + GAME_LINE_HEIGHT}));
        ArgumentCaptor<Integer> itemTops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).item(any(), anyInt(), itemTops.capture());
        itemTops.getAllValues().forEach(top -> spans.add(new int[] {top, top + SPRITE}));
        assertFalse(separators.isEmpty());
        for (int separator : separators) {
            for (int[] span : spans) {
                assertFalse(span[0] <= separator && separator < span[1],
                        "separator at " + separator + " crosses " + span[0] + ".." + span[1]);
            }
        }
    }

    /**
     * A prefab's head reads its name with a pencil just past it and a red x at the row's right end (the operator's
     * rulings).
     */
    @Test
    void headDrawsPencilByTheNameAndARedXAtTheEnd() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.drawn().stream().filter(text -> text.text().equals("$prefab0")).findFirst()
                .orElseThrow();
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")).findFirst()
                .orElseThrow();
        int pastName = name.x() + "$prefab0".length() * 6;
        assertEquals(name.y(), pencil.y());
        assertTrue(pencil.x() > pastName && pencil.x() < pastName + 12, pencil + " vs " + name);
        assertFalse(frame.texts().contains("scalar") || frame.texts().contains("Rename")
                || frame.texts().contains("Delete"), frame.texts().toString());
        DrawnFrame.Text cross = frame.drawn().stream().filter(text -> text.text().equals("✗")).findFirst()
                .orElseThrow();
        assertEquals(Palette.DANGER, cross.color());
        int listRight = TALL_VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x());
    }

    /**
     * Every tooltip hint a drawn Prefabs frame registers reads Rename or Delete
     * (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
     */
    @Test
    void everyTooltipHintIsRenameOrDelete() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        List<String> hints = frame.targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                .flatMap(java.util.Optional::stream).map(component -> component.getString()).distinct().toList();
        assertEquals(List.of("Rename", "Delete"), hints.stream().sorted(java.util.Comparator.reverseOrder()).toList());
    }

    private static final int GAME_LINE_HEIGHT = 9;

    // A small amount stands a pixel or three lower than a full-size label on its line (decision
    // goo-amounts-draw-smaller-and-a-pixel-lower-everywhere), so one line holds texts within half a line's height.
    private static boolean onLine(int labelY, int amountY) {
        return Math.abs(labelY - amountY) < ExpressionLines.TEXT_LINE_HEIGHT / 2;
    }
    private static final int SPRITE = 16;

    private static final GroupsPane.Bounds TALL_VIEW = new GroupsPane.Bounds(0, 20, 400, 6000);
    private static final GroupsPane.Bounds FITS_THE_EDITED_BLOCK = new GroupsPane.Bounds(0, 20, 400, 320);

    // A Prefabs pane over the twelve prefabs model() names, built fresh with no draw yet.
    private static VariablesPane twelvePrefabs(VariablesTab prefabs, ManagerDraft draft) {
        return twelvePrefabs(prefabs, draft, mock(ManagerActions.class));
    }

    private static VariablesPane twelvePrefabs(VariablesTab prefabs, ManagerDraft draft, ManagerActions actions) {
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    /**
     * A long prefab name cuts with the ellipsis and hovers whole (decision
     * one-name-primitive-cuts-and-hovers-the-whole-name-on-every-pane).
     */
    @Test
    void longPrefabNameCutsAndHoversWhole() {
        String name = "a_really_long_prefab_name_that_runs_on";
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(name));
        VariablesPane pane = twelvePrefabs(new VariablesTab(draft), draft);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text cut = frame.drawn().stream().filter(text -> text.text().contains(ButtonPainter.ELLIPSIS))
                .findFirst().orElseThrow(() -> new AssertionError(frame.texts().toString()));
        String key = frame.targets.hoveredArea(cut.x() + 1, cut.y() + 1).orElseThrow().hoverKey();
        assertEquals(Optional.of(new TooltipLine.Text(Component.literal("$" + name))),
                ButtonPainter.wholeNameTooltip(key));
    }

    /**
     * A prefab's name centers on its row under its separator through the one centering helper, as every list pane's
     * does (decision one-lettering-primitive-names-three-text-roles).
     */
    @Test
    void prefabNameCentersOnItsRow() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesPane pane = twelvePrefabs(new VariablesTab(draft), draft);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text second = frame.findFirst("$prefab1");
        assertEquals(Lettering.full(DrawnFrame.font()).centeredTop(frame.rowTopAbove(second),
                RowLayout.SPRITE_ROW_HEIGHT), second.y());
    }

    // Each block's top inside the list's content once the named block edits, read off a view tall enough to draw
    // every block unscrolled.
    private static List<Integer> blockTopsEditing(String name) {
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesPane tall = twelvePrefabs(new VariablesTab(draft), draft);
        tall.jumpTo(name);
        DrawnFrame frame = new DrawnFrame();
        tall.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame settled = new DrawnFrame();
        tall.extract(settled.graphics, DrawnFrame.font(), TALL_VIEW, settled.targets, 0, 0);
        List<Integer> names = settled.topsOf(IntStream.range(0, PREFABS).mapToObj(index -> "$prefab" + index)
                .toList());
        return names.stream().map(y -> y - names.getFirst()).toList();
    }

    /**
     * A shift + left follow to a prefab well below the fold opens its block for editing and centers the block in the
     * list (decision shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void jumpCentersTheEditedBlockInTheList() {
        int target = 6;
        List<Integer> tops = blockTopsEditing("prefab" + target);
        DrawnFrame before = new DrawnFrame();
        pane.extract(before.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, before.targets, 0, 0);
        pane.jumpTo("prefab" + target);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, frame.targets, 0, 0);
        int[] list = frame.firstScissor();
        int blockHeight = tops.get(target + 1) - tops.get(target);
        assertTrue(blockHeight <= list[3] - list[1], "the edited block outgrows the list");
        assertEquals(tops.get(target) + blockHeight / 2 - (list[3] - list[1]) / 2, pane.listBar().offset());
        assertEquals(java.util.Optional.of("prefab" + target), variables.editing());
    }

    /**
     * A shift + left follow to a prefab whose editing block outgrows the list stands the block's top at the list's
     * top, so its name and the builder's start stay in view (decision tall-follow-target-shows-from-its-top).
     */
    @Test
    void jumpToABlockTallerThanTheListShowsItFromItsTop() {
        int target = 5;
        List<Integer> tops = blockTopsEditing("prefab" + target);
        GroupsPane.Bounds shortView = new GroupsPane.Bounds(0, 20, 400, 80);
        pane.extract(graphics, font, shortView, new ClickTargets(), 0, 0);
        pane.jumpTo("prefab" + target);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), shortView, frame.targets, 0, 0);
        int[] list = frame.firstScissor();
        assertTrue(tops.get(target + 1) - tops.get(target) > list[3] - list[1], "the edited block fits the list");
        assertEquals(tops.get(target), pane.listBar().offset());
        assertEquals(java.util.Optional.of("prefab" + target), variables.editing());
    }

    /**
     * A shift + left follow to the last prefab stops the list at its end (decision
     * shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void jumpToTheLastPrefabStopsAtTheListsEnd() {
        DrawnFrame before = new DrawnFrame();
        pane.extract(before.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, before.targets, 0, 0);
        pane.jumpTo("prefab" + (PREFABS - 1));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), FITS_THE_EDITED_BLOCK, frame.targets, 0, 0);
        assertTrue(pane.listBar().maximum() > 0);
        assertEquals(pane.listBar().maximum(), pane.listBar().offset());
    }

    /**
     * With shift held, the delete confirm's $name reader draws underlined, while the confirm's Delete and the block's
     * Rename and Delete, which modify, never do (decision holding-shift-underlines-every-link).
     */
    @Test
    void cascadeReaderUnderlinesWhileShiftIsHeldAndModifyButtonsNever() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame().holdingShift();
        read.extract(list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        assertFalse(list.underlined("✎"));
        assertFalse(list.underlined("✗"));
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame().holdingShift();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        assertTrue(confirm.underlined("$egg"));
        assertFalse(confirm.underlined("Delete"));
    }

    /**
     * The delete confirm's prefab and group readers draw as the head's Used by line draws them: the same $chip and
     * #chip with the same hovers (decision one-used-by-primitive-draws-the-row-line-and-the-dialog).
     */
    @Test
    void deleteConfirmsReadersDrawAsTheUsedByLineDrawsThem() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "384", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "base", "kind": "scalar", "value": "384", "error": null, "usedBy": [],
                                "readers": [{"id": "egg", "kind": "constant"}, {"id": "logs", "kind": "group"}]}]}
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
        List<Optional<String>> head = List.of(hoverOf(list, "$egg"), hoverOf(list, "#logs"));
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        assertEquals(List.of(Optional.of(PrefabHovers.hoverKey("egg")), Optional.of(GroupHovers.hoverKey("logs"))),
                head, "the head's Used by line");
        assertEquals(head, List.of(hoverOf(confirm, "$egg"), hoverOf(confirm, "#logs")), "the dialog's readers");
    }

    private static Optional<String> hoverOf(DrawnFrame frame, String label) {
        DrawnFrame.Text text = frame.find(label);
        return frame.targets.hoveredArea(text.x() + 1, text.y() + 1).map(ClickTargets.HoveredArea::hoverKey);
    }

    /**
     * The delete confirm's item reader hovers the item's value and follows to Items on shift + left click, as every
     * item icon does (decision one-item-icon-primitive-hovers-and-links-every-icon-but-the-rows-own).
     */
    @Test
    void deleteConfirmsItemReaderHoversAndLinksAsEveryItemIcon() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "384", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "base", "kind": "scalar", "value": "384", "error": null, "usedBy": [],
                                "readers": [{"id": "minecraft:stick", "kind": "item"}]}]}
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
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(confirm.graphics, atLeastOnce()).item(any(), xs.capture(), ys.capture());
        int x = xs.getAllValues().getLast() + 1;
        int y = ys.getAllValues().getLast() + 1;
        assertEquals(Optional.of(ItemIcons.hoverKey("minecraft:stick")),
                confirm.targets.hoveredArea(x, y).map(ClickTargets.HoveredArea::hoverKey));
        assertTrue(confirm.targets.click(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT));
        verify(actions).openItem("minecraft:stick");
    }

    /**
     * The delete confirm dims the whole screen through Dialog, as the draft's confirm does, its card on Dialog's card
     * (decision one-wheel-step-and-one-dialog-primitive).
     */
    @Test
    void deleteConfirmDimsTheWholeScreenThroughDialog() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        assertTrue(confirm.fillsOf(Dialog.DIM).stream().anyMatch(fill -> fill[0] == 0 && fill[1] == 0
                && fill[2] == DrawnFrame.SCREEN_WIDTH && fill[3] == DrawnFrame.SCREEN_HEIGHT), "the screen dims");
        assertFalse(confirm.fillsOf(Dialog.CARD).isEmpty(), "the card draws");
    }

    /**
     * A scalar prefab's name line reads its name, ✎, Used by with its links and the ✗ at one y in that order; the
     * next line reads Value: one indent in, the expression, and the evaluated amount in parentheses right of it; its
     * pencil opens the rename box in the name's place (decision
     * prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void scalarHeadReadsNameUsedByThenValueWithItsAmount() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.find("$base");
        DrawnFrame.Text pencil = frame.findFirst("✎");
        DrawnFrame.Text usedBy = frame.find("Used by ");
        DrawnFrame.Text egg = linkText(frame, "$egg");
        DrawnFrame.Text cross = frame.find("✗");
        assertEquals(List.of(name.y(), name.y(), name.y(), name.y()),
                List.of(pencil.y(), usedBy.y(), egg.y(), cross.y()), frame.drawn().toString());
        assertTrue(name.x() < pencil.x() && pencil.x() < usedBy.x() && usedBy.x() < egg.x() && egg.x() < cross.x(),
                frame.drawn().toString());
        DrawnFrame.Text value = frame.find("Value:");
        assertEquals(name.x() + ControlFlow.INDENT, value.x());
        assertTrue(value.y() > name.y() && value.y() - name.y() < 2 * ButtonPainter.HEIGHT, value + " vs " + name);
        DrawnFrame.Text expression = frame.drawn().stream().filter(text -> text.text().equals("384")
                && text.x() > value.x()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
        assertEquals(value.y(), expression.y(), frame.drawn().toString());
        assertTrue(value.x() < expression.x(), frame.drawn().toString());
        // Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount: a plain number previews nothing.
        assertEquals(1, frame.count("384"), frame.texts().toString());
        assertTrue(frame.drawn().stream().noneMatch(text -> text.y() == value.y() && text.text().contains("(")),
                frame.drawn().toString());
        assertFalse(frame.texts().contains("="), frame.texts().toString());
        frame.clickFirst("✎");
        DrawnFrame renaming = new DrawnFrame();
        read.extract(renaming.graphics, DrawnFrame.font(), VIEW, renaming.targets, 0, 0);
        assertFalse(renaming.texts().contains("$base"), renaming.texts().toString());
        assertEquals(frame.count("✎") - 1, renaming.count("✎"), "the rename box stands in the pencil's place");
    }

    /**
     * A prefab's value draws framed with a pencil segment at its right, a click on the pencil opening the builder,
     * and a prefab with no expression reads Value: Not explicitly set as that button (decisions
     * read-mode-builder-is-a-button-with-a-pencil-segment,
     * no-value-reads-value-not-explicitly-set-as-a-pencil-button).
     */
    @Test
    void valueIsAPencilButtonAndAnEmptyOneReadsNotExplicitlySet() {
        ManagerActions actions = mock(ManagerActions.class);
        VariablesPane read = readByEgg(actions);
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        DrawnFrame.Text value = frame.find("Value:");
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals(ReadValueButton.PENCIL)
                && text.y() == value.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
        assertTrue(pencil.x() > frame.find("384").x(), pencil.toString());
        assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1));
        DrawnFrame editing = new DrawnFrame();
        read.extract(editing.graphics, DrawnFrame.font(), VIEW, editing.targets, 0, 0);
        assertTrue(editing.texts().stream().anyMatch(text -> text.endsWith(ControlFlow.dropdownLabel(""))),
                editing.texts().toString());

        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "blank", "keySeparator": null, "kind": "VALUE", "text": "\\"\\"", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesPane unset = twelvePrefabs(new VariablesTab(draft), draft);
        DrawnFrame blank = new DrawnFrame();
        unset.extract(blank.graphics, DrawnFrame.font(), VIEW, blank.targets, 0, 0);
        assertTrue(blank.texts().contains(ReadValueButton.NOT_SET), blank.texts().toString());
        blank.click(ReadValueButton.NOT_SET);
        DrawnFrame opened = new DrawnFrame();
        unset.extract(opened.graphics, DrawnFrame.font(), VIEW, opened.targets, 0, 0);
        assertTrue(opened.texts().stream().anyMatch(text -> text.endsWith(ControlFlow.dropdownLabel(""))),
                opened.texts().toString());
    }

    private static final String READ_PREFABS = """
            {"path": "base_values.json", "members": [
              {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null},
                {"key": "plank", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood / 4\\"", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}],
               "leading": null, "trailing": null, "closingGap": null}]}
            """;
    // wood is named by the stick directly, by the prefab plank and by the group logs, and its cascade reaches the
    // terminus item cake, which no expression of its own names wood.
    private static final String WOOD_READ = """
            "name": "wood", "kind": "scalar", "value": "8", "error": null,
            "direct": ["minecraft:stick"], "usedBy": ["minecraft:stick", "minecraft:cake"],
            "readers": [{"id": "plank", "kind": "constant"}, {"id": "#logs", "kind": "group"},
                        {"id": "minecraft:stick", "kind": "item"}]""";
    private static final String READ_EVALUATED = """
            {"constants": [{%s},
              {"name": "plank", "kind": "scalar", "value": "2", "error": null, "direct": [], "usedBy": [],
               "readers": []}]}
            """.formatted(WOOD_READ);

    // A Prefabs pane over wood and plank, read by plank and the group logs.
    private static VariablesPane woodReadByPlankAndLogs(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        draft.read(READ_PREFABS);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated(READ_EVALUATED);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    private static DrawnFrame.Text linkText(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label) && text.color() == Palette.LINK)
                .reduce((first, last) -> last).orElseThrow(() -> new AssertionError(label + " in "
                        + frame.drawn()));
    }

    private static List<Integer> itemXs(DrawnFrame frame) {
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, org.mockito.Mockito.atLeast(0)).item(any(), xs.capture(), anyInt());
        return xs.getAllValues();
    }

    /**
     * wood's used-by run draws the stick's icon, then the prefab plank's chip and the group logs' chip in the link
     * color, and the terminus item cake draws nowhere (decision
     * used-by-lists-direct-items-and-links-to-prefabs-and-groups).
     */
    @Test
    void usedByDrawsDirectItemsThenPrefabAndGroupLinks() {
        VariablesPane read = woodReadByPlankAndLogs(mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        assertTrue(frame.targets.hoverKeys().contains(ItemIcons.hoverKey("minecraft:stick")));
        assertFalse(frame.targets.hoverKeys().contains(ItemIcons.hoverKey("minecraft:cake")),
                frame.targets.hoverKeys().toString());
        assertEquals(1, itemXs(frame).size(), "one item icon, the stick's");
        DrawnFrame.Text plank = linkText(frame, "$plank");
        DrawnFrame.Text logs = linkText(frame, "#logs");
        assertTrue(itemXs(frame).getFirst() < plank.x(), "stick before plank");
        assertTrue(plank.x() < logs.x(), plank + " before " + logs);
        assertEquals(plank.y(), logs.y());
    }

    /**
     * plank's read view and its builder key the $wood ref by one hover key, PrefabHovers.hoverKey("wood"), so both
     * show the prefab's tooltip (decision value-hover-reads-the-same-editing-and-read-only).
     */
    @Test
    void readViewRefHoversTheKeyTheBuildersRefHovers() {
        VariablesPane read = woodReadByPlankAndLogs(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), TALL_VIEW, list.targets, 0, 0);
        DrawnFrame.Text readRef = linkText(list, "$wood");
        assertEquals(java.util.Optional.of(PrefabHovers.hoverKey("wood")),
                list.targets.hovered(readRef.x() + 1, readRef.y() + 1));
        list.click("$wood");
        DrawnFrame editing = new DrawnFrame();
        read.extract(editing.graphics, DrawnFrame.font(), TALL_VIEW, editing.targets, 0, 0);
        DrawnFrame building = new DrawnFrame();
        read.extract(building.graphics, DrawnFrame.font(), TALL_VIEW, building.targets, 0, 0);
        DrawnFrame.Text builderRef = linkText(building, "$wood");
        assertEquals(java.util.Optional.of(PrefabHovers.hoverKey("wood")),
                building.targets.hovered(builderRef.x() + 1, builderRef.y() + 1));
    }

    /**
     * A shift + left click on plank's chip opens plank and on logs' chip opens the group logs; plank's chip hovers
     * its value (decision used-by-lists-direct-items-and-links-to-prefabs-and-groups).
     */
    @Test
    void usedByLinksFollowOnShiftClickAndThePrefabChipHoversItsValue() {
        ManagerActions actions = mock(ManagerActions.class);
        VariablesPane read = woodReadByPlankAndLogs(actions);
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text plank = linkText(frame, "$plank");
        assertEquals(java.util.Optional.of(PrefabHovers.hoverKey("plank")),
                frame.targets.hovered(plank.x() + 1, plank.y() + 1));
        assertTrue(frame.targets.click(plank.x() + 1, plank.y() + 1, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        verify(actions).openVariable("plank");
        DrawnFrame.Text logs = linkText(frame, "#logs");
        assertTrue(frame.targets.click(logs.x() + 1, logs.y() + 1, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,
                org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT));
        verify(actions).openGroup("logs");
    }

    /**
     * A typed prefab's name line carries Used by with the stick's icon and the ✗ at the line's right end; below it
     * Value: stands beside the amount line, highest first as (6, 4), and each type row below reads its terms with no
     * amount at its end (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void typedHeadCarriesUsedByAndEachTypeRowItsOwnAmount() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": 4, \\"glow\\": 6}",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "wood", "kind": "tree", "tree": {"leaf": "4", "glow": "6"}, "value": null,
                                "error": null, "direct": ["minecraft:stick"], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.find("$wood");
        DrawnFrame.Text usedBy = frame.find("Used by ");
        DrawnFrame.Text cross = frame.find("✗");
        assertEquals(List.of(name.y(), name.y()), List.of(usedBy.y(), cross.y()), frame.drawn().toString());
        int listRight = TALL_VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x());
        ArgumentCaptor<Integer> itemXs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics).item(any(), itemXs.capture(), anyInt());
        assertTrue(itemXs.getValue() > usedBy.x(), "the stick's icon follows Used by");
        DrawnFrame.Text value = frame.find("Value:");
        assertTrue(value.y() > name.y(), value + " below " + name);
        // Decision one-lettering-primitive-names-three-text-roles: the amount line letters at the amount role.
        List<DrawnFrame.Placed> amounts = frame.placements().stream()
                .filter(placed -> placed.scale() == Lettering.AMOUNT_SCALE).toList();
        assertEquals(List.of("6", "4"), amounts.stream().map(DrawnFrame.Placed::text).toList(), amounts.toString());
        DrawnFrame.Placed glowAmount = amounts.getFirst();
        assertTrue(amounts.stream().allMatch(amount -> onLine(value.y(), amount.y())), frame.drawn().toString());
        // Decision one-amount-grid-draws-every-amount-list: the line is GooAmountGrid's, its cells a column apart and
        // its parens gold flush symbols round the digits, no paren or comma riding a number.
        GooAmountGrid grid = GooAmountGrid.reduced(Map.of("glow", "6", "leaf", "4"));
        assertEquals(grid.cellLeft(DrawnFrame.font(), 1) - grid.cellLeft(DrawnFrame.font(), 0),
                amounts.get(1).x() - glowAmount.x(), amounts.toString());
        assertEquals(List.of(Palette.GOLD, Palette.GOLD), List.of(frame.find("(").color(), frame.find(")").color()));
        assertTrue(frame.drawn().stream().noneMatch(text -> text.text().length() > 1
                && (text.text().startsWith("(") || text.text().endsWith(")") || text.text().endsWith(","))),
                frame.drawn().toString());
        DrawnFrame.Text leafRow = frame.find("4");
        DrawnFrame.Text glowRow = frame.find("6");
        assertTrue(value.y() < leafRow.y() && leafRow.y() < glowRow.y(), frame.drawn().toString());
        assertTrue(frame.drawn().stream().noneMatch(text -> text.y() > glowAmount.y() + 4
                && (text.text().contains("(") || text.text().contains(")"))), frame.drawn().toString());
    }

    /**
     * An edited prefab keeps its Value: label and its amount line above the builder, and the builder opens in the read
     * box's place, short of the delete x, its panel's overhang reserved (decision one-builder-frame-on-every-pane).
     */
    @Test
    void editedBlockKeepsItsLabelAndAmountLineAboveTheBuilder() {
        VariablesPane pane = fourTypeRows();
        GroupsPane.Bounds view = new GroupsPane.Bounds(0, 20, 2000, 400);
        DrawnFrame read = new DrawnFrame();
        pane.extract(read.graphics, DrawnFrame.font(), view, read.targets, 0, 0);
        int[] box = read.buttonAround(read.find(":"));
        int listRight = view.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        assertEquals(listRight - BuilderFrame.deleteInset(DrawnFrame.font()),
                read.buttonAround(read.find(ReadValueButton.PENCIL))[2], "the box short of the x");
        read.click(ReadValueButton.PENCIL);
        DrawnFrame open = new DrawnFrame();
        pane.extract(open.graphics, DrawnFrame.font(), view, open.targets, 0, 0);
        int[] panel = open.fillsOf(Palette.PANEL_FILL).getFirst();
        assertEquals(List.of(box[0] - RowLayout.BOX_MARGIN, box[1],
                listRight - BuilderFrame.deleteInset(DrawnFrame.font())), List.of(panel[0], panel[1], panel[2]),
                "the builder's panel in the read box's place");
        DrawnFrame.Text label = open.find("Value:");
        List<DrawnFrame.Placed> amounts = open.placements().stream()
                .filter(placed -> placed.scale() == Lettering.AMOUNT_SCALE && placed.y() < panel[1]).toList();
        assertEquals(List.of("96768", "32256", "336", "192"), amounts.stream().map(DrawnFrame.Placed::text).toList(),
                "the amount line above the builder");
        assertTrue(label.y() < panel[1], "the label above the builder");
    }

    /**
     * A prefab whose value fails draws its error as one red line under its box, not inside it (decision
     * one-red-error-line-under-every-value).
     */
    @Test
    void failingPrefabDrawsOneRedLineUnderItsBox() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "broken", "keySeparator": null, "kind": "VALUE", "text": "\\"$missing + 1\\"",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "broken", "kind": "scalar", "value": null,
                                "error": "Unknown constant $missing", "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane pane = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        int[] box = frame.buttonAround(frame.find("$missing"));
        List<DrawnFrame.Text> red = frame.drawn().stream().filter(text -> text.color() == Palette.DANGER
                && text.text().contains("Unknown constant")).toList();
        assertEquals(1, red.size(), frame.drawn().toString());
        assertEquals(box[3], red.getFirst().y());
    }

    // A prefab of four type rows, each a plain amount, resolving to the four amounts typedPreview's does.
    private static VariablesPane fourTypeRows() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "trim", "keySeparator": null, "kind": "VALUE",
                     "text": "{\\"rock\\": 192, \\"yore\\": 336, \\"opulent\\": 32256, \\"crystal\\": 96768}",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "trim", "kind": "tree", "tree": {"rock": "192", "yore": "336",
                                "opulent": "32256", "crystal": "96768"}, "value": null, "error": null,
                                "direct": [], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        return new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    // Decision one-amount-grid-draws-every-amount-list: digits alone, the parens standing round the grid.
    private static final List<String> FOUR_AMOUNTS = List.of("96768", "32256", "336", "192");

    /**
     * A typed prefab's amounts stand on Value:'s line above its type rows, each type's icon beside its amount, and
     * no type row's line carries a paren (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount; the
     * operator's ruling of 2026-11-04 stands Value: beside the amounts).
     */
    @Test
    void typedValueDrawsItsAmountsOnTheirOwnLineAboveTheExpression() {
        DrawnFrame frame = new DrawnFrame();
        fourTypeRows().extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 20, 2000, 400),
                frame.targets, 0, 0);
        DrawnFrame.Text value = frame.find("Value:");
        List<DrawnFrame.Text> amounts = FOUR_AMOUNTS.stream().map(frame::findFirst).toList();
        assertTrue(onLine(value.y(), amounts.getFirst().y()), frame.drawn().toString());
        List<Integer> rowLines = frame.drawn().stream().filter(text -> text.text().equals(":"))
                .map(DrawnFrame.Text::y).toList();
        assertEquals(4, rowLines.size(), frame.drawn().toString());
        int firstRow = rowLines.stream().mapToInt(Integer::intValue).min().orElseThrow();
        assertTrue(amounts.stream().allMatch(amount -> amount.y() < firstRow), amounts + " vs row " + firstRow);
        assertEquals(PREVIEWED_TYPES.size(), blitsBetween(frame, value.y() - ButtonPainter.HEIGHT,
                firstRow - ExpressionLines.ICON_LINE_HEIGHT / 2), frame.drawn().toString());
        assertTrue(frame.drawn().stream().noneMatch(text -> rowLines.contains(text.y())
                && (text.text().contains("(") || text.text().contains(")"))), frame.drawn().toString());
        // The operator's ruling of 2026-11-04: the amounts stand above the value's box, outside it.
        int boxTop = frame.buttonAround(frame.drawn().stream().filter(text -> text.text().equals(":")).findFirst()
                .orElseThrow())[1];
        assertTrue(amounts.stream().allMatch(amount -> amount.y() + GAME_LINE_HEIGHT <= boxTop),
                amounts + " vs the box's top " + boxTop);
        assertEquals(PREVIEWED_TYPES.size(), blitsBetween(frame, value.y() - ButtonPainter.HEIGHT, boxTop),
                "the amount line's icons stand above the box");
    }

    /**
     * An expression prefab's amount draws in parentheses on Value:'s line and the expression on the line below,
     * which holds no paren (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void scalarExpressionDrawsItsAmountOnItsOwnLineAboveTheExpression() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "8", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null},
                    {"key": "beam", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood * 24\\"", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "wood", "kind": "scalar", "value": "8", "error": null},
                               {"name": "beam", "kind": "scalar", "value": "192", "error": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text beamValue = frame.drawn().stream().filter(text -> text.text().equals("Value:"))
                .reduce((first, last) -> last).orElseThrow();
        DrawnFrame.Text amount = frame.find("192");
        DrawnFrame.Text wood = linkText(frame, "$wood");
        assertTrue(onLine(beamValue.y(), amount.y()), frame.drawn().toString());
        assertTrue(wood.y() > amount.y(), wood + " below " + amount);
        assertTrue(frame.drawn().stream().noneMatch(text -> text.y() == wood.y() && text.text().contains("(")),
                frame.drawn().toString());
        // The operator's ruling of 2026-11-04: the amount stands above the value's box, outside it.
        assertTrue(amount.y() + GAME_LINE_HEIGHT <= frame.buttonAround(wood)[1], amount + " inside the box");
    }

    /**
     * A prefab's rename pencil stands the button gap past its name (decision
     * spacing-rules-are-one-set-across-every-pane).
     */
    @Test
    void prefabPencilStandsTheButtonGapPastItsName() {
        DrawnFrame frame = new DrawnFrame();
        readByEgg(mock(ManagerActions.class)).extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.find("$base");
        DrawnFrame.Text pencil = frame.findFirst("✎");
        assertEquals(name.x() + DrawnFrame.font().width(name.text()) + RowLayout.BUTTON_GAP,
                pencil.x() - ButtonPainter.PADDING, frame.drawn().toString());
    }

    private static final List<String> PREVIEWED_TYPES = List.of("rock", "yore", "opulent", "crystal");

    // A prefab summing two others whose preview reads four goo types, the screenshot's $mundane_trim.
    private static VariablesPane typedPreview() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "trim", "keySeparator": null, "kind": "VALUE", "text": "\\"$diamond + $block\\"",
                     "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "trim", "kind": "tree", "tree": {"rock": "192", "yore": "336",
                                "opulent": "32256", "crystal": "96768"}, "value": null, "error": null,
                                "direct": [], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        return new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    private static int blitsBetween(DrawnFrame frame, int top, int bottom) {
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(0)).blit(any(), any(), anyInt(), ys.capture(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return (int) ys.getAllValues().stream().filter(y -> y >= top && y < bottom).count();
    }

    /**
     * A typed preview draws each goo type's icon where its name stood, the amount beside it, and no type name
     * (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     */
    @Test
    void typedPreviewDrawsIconsInPlaceOfTypeNames() {
        DrawnFrame frame = new DrawnFrame();
        typedPreview().extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 20, 2000, 400),
                frame.targets, 0, 0);
        DrawnFrame.Text value = frame.find("Value:");
        assertTrue(frame.texts().stream().noneMatch(text -> PREVIEWED_TYPES.stream().anyMatch(text::contains)),
                frame.texts().toString());
        // The amount line lays its four icons in two rows (decision
        // prefab-head-is-name-pencil-used-by-then-value-with-its-amount), so the second row stands an icon line down.
        assertEquals(PREVIEWED_TYPES.size(), blitsBetween(frame, value.y() - ButtonPainter.HEIGHT,
                value.y() + ExpressionLines.ICON_LINE_HEIGHT + ButtonPainter.HEIGHT), frame.drawn().toString());
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals("96768") && onLine(text.y(), value.y())),
                frame.drawn().toString());
    }

    /**
     * At every width the four amounts lay as the tooltip's grid, ceil(sqrt(4)) = 2 columns filled row by row, the
     * second row's icons under the first row's, the open paren on the first amount and the close paren on the last,
     * never a paren alone (decisions prefab-head-is-name-pencil-used-by-then-value-with-its-amount,
     * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
     */
    @Test
    void typedAmountLineLaysAsTheTooltipGrid() {
        for (int width = 200; width <= 700; width++) {
            int right = width;
            DrawnFrame frame = new DrawnFrame();
            typedPreview().extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 20, right, 4000),
                    frame.targets, 0, 0);
            List<DrawnFrame.Text> amounts = FOUR_AMOUNTS.stream().map(frame::findFirst).toList();
            assertEquals(List.of(amounts.get(0).y(), amounts.get(2).y()), List.of(amounts.get(1).y(),
                    amounts.get(3).y()), "at " + right + ": " + amounts);
            assertTrue(amounts.get(2).y() > amounts.get(0).y(), "at " + right + ": " + amounts);
            List<int[]> icons = blitsAt(frame);
            assertEquals(PREVIEWED_TYPES.size(), icons.size(), "at " + right);
            assertEquals(List.of(icons.get(0)[0], icons.get(1)[0]), List.of(icons.get(2)[0], icons.get(3)[0]),
                    "at " + right + ": the second row's icons stand under the first's");
            // Decision one-amount-grid-draws-every-amount-list: the open paren stands before the first cell and the
            // close paren flush after the last, on its row.
            DrawnFrame.Text open = frame.find("(");
            DrawnFrame.Text close = frame.find(")");
            assertTrue(open.x() < icons.get(0)[0] && onLine(open.y(), amounts.get(0).y())
                    && close.x() > amounts.get(3).x() && onLine(close.y(), amounts.get(3).y()),
                    "at " + right + ": " + frame.drawn());
        }
    }

    // Each goo icon's x and y, in draw order.
    private static List<int[]> blitsAt(DrawnFrame frame) {
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeast(0)).blit(any(), any(), xs.capture(), ys.capture(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        return IntStream.range(0, xs.getAllValues().size())
                .mapToObj(index -> new int[] {xs.getAllValues().get(index), ys.getAllValues().get(index)}).toList();
    }

    private static DrawnFrame.Text greenText(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label) && text.color() == 0xFF80E080)
                .findFirst().orElseThrow(() -> new AssertionError(label + " in " + frame.drawn()));
    }

    private static List<Integer> fillTopsAt(DrawnFrame frame, int color) {
        ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(), eq(color));
        return tops.getAllValues();
    }

    @org.junit.jupiter.api.Nested
    class RemovalMarking {
        private final ManagerActions actions = mock(ManagerActions.class);
        private final ManagerDraft marked = new ManagerDraft();
        private VariablesPane read;

        @BeforeEach
        void standTwelvePrefabs() {
            marked.read(model());
            read = twelvePrefabs(new VariablesTab(marked), marked, actions);
        }

        private DrawnFrame drawn(VariablesPane drawnPane) {
            DrawnFrame frame = new DrawnFrame();
            drawnPane.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
            return frame;
        }

        private void verifyNoDelete(ManagerActions recorded) {
            verify(recorded, never()).request(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.DELETE_CONSTANT),
                    anyString(), any());
            verify(recorded, never()).stream(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.DELETE_CONSTANT),
                    anyString(), any(), any(), any());
        }

        /**
         * Clicking a prefab's ✗ marks it: the block still draws, a gold ↺ hinting Restore stands where the ✗ stood,
         * the draft reads unsaved and no delete request goes out (decision
         * every-delete-marks-for-removal-and-a-gold-arrow-restores).
         */
        @Test
        void xMarksThePrefabAndAGoldArrowStandsInItsPlace() {
            DrawnFrame list = drawn(read);
            DrawnFrame.Text cross = list.find("✗");
            list.click("✗");
            DrawnFrame after = drawn(read);
            assertTrue(after.texts().contains("$prefab" + (PREFABS - 1)), after.texts().toString());
            DrawnFrame.Text arrow = after.drawn().stream().filter(text -> text.text().equals("↺")).findFirst()
                    .orElseThrow(() -> new AssertionError(after.texts().toString()));
            assertEquals(cross.x(), arrow.x());
            assertEquals(cross.y(), arrow.y());
            assertEquals(Palette.GOLD, arrow.color());
            assertEquals(java.util.Optional.of(BuilderOverlays.hintKey("Restore")),
                    after.targets.hovered(arrow.x() + 1, arrow.y() + 1));
            verify(actions).markUnsaved();
            verifyNoDelete(actions);
        }

        /**
         * Clicking the ↺ of a marked prefab clears its mark: the ✗ stands again and the save path sends no delete
         * (decision every-delete-marks-for-removal-and-a-gold-arrow-restores).
         */
        @Test
        void arrowRestoresAndTheSaveRemovesNothing() {
            drawn(read).click("✗");
            drawn(read).click("↺");
            DrawnFrame after = drawn(read);
            assertEquals(PREFABS, after.texts().stream().filter("✗"::equals).count(), after.texts().toString());
            assertTrue(marked.removals().marked(RemovalMarks.Tab.PREFABS).isEmpty());
            Runnable write = mock(Runnable.class);
            read.applyRemovals(write);
            verify(write).run();
            verifyNoDelete(actions);
        }
    }

    /**
     * A prefab with a cascade opens the confirm listing its readers; the confirm's Delete marks the prefab and sends
     * nothing, and the save path then sends one delete naming it before the draft write (decision
     * every-delete-marks-for-removal-and-a-gold-arrow-restores).
     */
    @Test
    void confirmMarksAndTheSaveDeletesBeforeTheWrite() {
        ManagerActions actions = mock(ManagerActions.class);
        org.mockito.Mockito.doAnswer(call -> {
            call.<java.util.function.Consumer<String>>getArgument(3).accept("""
                    {"path": "base_values.json", "members": []}""");
            return null;
        }).when(actions).stream(anyString(), anyString(), any(), any(), any());
        VariablesPane read = readByEgg(actions);
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        assertTrue(confirm.texts().contains("$egg"), confirm.texts().toString());
        confirm.click("Delete");
        DrawnFrame after = new DrawnFrame();
        read.extract(after.graphics, DrawnFrame.font(), VIEW, after.targets, 0, 0);
        assertTrue(after.texts().contains("↺"), after.texts().toString());
        verify(actions, never()).stream(anyString(), anyString(), any(), any(), any());
        read.applyRemovals(() -> actions.request(com.mercuriusxeno.goo.workbench.WorkbenchOperations.SAVE_MODEL, "",
                reply -> { }));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(actions);
        order.verify(actions).stream(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.DELETE_CONSTANT),
                org.mockito.ArgumentMatchers.contains("\"name\":\"base\""), any(), any(), any());
        order.verify(actions).request(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.SAVE_MODEL),
                anyString(), any());
    }

    /**
     * An open rename box takes a ✓ and a gold ↺ right of it on its row; ✓ sends the rename with the typed name, as
     * Enter does, and ↺ closes the box leaving the name, as Escape does (decisions
     * open-name-box-takes-a-done-checkmark, open-name-box-takes-a-gold-arrow-that-cancels).
     */
    @Test
    void renameBoxTakesADoneCheckAndANevermindArrow() {
        List<AbstractWidget> widgets = new java.util.ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(new VariablesTab(draft), draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        String last = "prefab" + (PREFABS - 1);
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), TALL_VIEW, list.targets, 0, 0);
        clickRenameOf(list, last);
        DrawnFrame open = new DrawnFrame();
        read.extract(open.graphics, DrawnFrame.font(), TALL_VIEW, open.targets, 0, 0);
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && last.equals(each.getValue())).findFirst().orElseThrow();
        assertNameBoxButtons(open, box);
        box.setValue("renamed");
        open.click("✓");
        verify(actions).request(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.RENAME_CONSTANT),
                org.mockito.ArgumentMatchers.contains("\"to\":\"renamed\""), any());
        DrawnFrame again = new DrawnFrame();
        read.extract(again.graphics, DrawnFrame.font(), TALL_VIEW, again.targets, 0, 0);
        clickRenameOf(again, last);
        DrawnFrame reopened = new DrawnFrame();
        read.extract(reopened.graphics, DrawnFrame.font(), TALL_VIEW, reopened.targets, 0, 0);
        box.setValue("other");
        reopened.click("↺");
        assertFalse(box.visible);
        verify(actions, never()).request(anyString(), org.mockito.ArgumentMatchers.contains("other"), any());
        DrawnFrame closed = new DrawnFrame();
        read.extract(closed.graphics, DrawnFrame.font(), TALL_VIEW, closed.targets, 0, 0);
        assertTrue(closed.texts().contains("$" + last), closed.texts().toString());
    }

    // The rename pencil on a prefab's name line, apart from its value's pencil below.
    private static void clickRenameOf(DrawnFrame frame, String prefab) {
        DrawnFrame.Text name = frame.find("$" + prefab);
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")
                && text.y() == name.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
        assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1), pencil + " took no click");
    }

    /**
     * A ✓ and a gold ↺ stand right of the open box on its row.
     *
     * @param frame the frame drawn with the box open
     * @param box   the open box
     */
    static void assertNameBoxButtons(DrawnFrame frame, EditBox box) {
        DrawnFrame.Text done = frame.find("✓");
        DrawnFrame.Text arrow = frame.find("↺");
        int boxRight = box.getX() + box.getWidth();
        assertTrue(done.x() > boxRight && arrow.x() > boxRight, done + " " + arrow + " vs box right " + boxRight);
        assertTrue(Math.abs(done.y() - box.getY()) < ExpressionLines.TEXT_LINE_HEIGHT, done + " vs box " + box.getY());
        assertEquals(done.y(), arrow.y());
        assertEquals(Palette.GOLD, arrow.color());
        assertEquals(java.util.Optional.of(BuilderOverlays.hintKey("Done")),
                frame.targets.hovered(done.x() + 1, done.y() + 1));
        assertEquals(java.util.Optional.of(BuilderOverlays.hintKey("Nevermind")),
                frame.targets.hovered(arrow.x() + 1, arrow.y() + 1));
    }

    // A Prefabs pane over one prefab, base, that the prefab egg reads.
    private static VariablesPane readByEgg(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "384", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "base", "kind": "scalar", "value": "384", "error": null, "usedBy": [],
                                "readers": [{"id": "egg", "kind": "constant"}]}]}
                """);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
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
        list.click("✗");
        DrawnFrame confirm = new DrawnFrame();
        read.extract(confirm.graphics, DrawnFrame.font(), VIEW, confirm.targets, 0, 0);
        confirm.click("$egg");
        verify(actions, never()).openVariable(anyString());
        DrawnFrame standing = new DrawnFrame();
        read.extract(standing.graphics, DrawnFrame.font(), VIEW, standing.targets, 0, 0);
        standing.shiftClick("$egg");
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
        assertFalse(frame.rightClick("$wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(prefabs.editing().isEmpty());
        frame.shiftClick("$wood");
        verify(actions).openVariable("wood");
        assertTrue(prefabs.editing().isEmpty());
        frame.click("$wood");
        assertEquals(java.util.Optional.of("plank"), prefabs.editing());
    }

    /**
     * Each change the builder writes over a prefab evaluates the draft afresh while the builder stays open, so the
     * value the block shows is the value the builder holds.
     */
    @Test
    void eachBuilderChangeEvaluatesThePrefabsAfresh() {
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesTab prefabs = new VariablesTab(draft);
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        ExpressionBuilderPane builder = mock(ExpressionBuilderPane.class);
        VariablesPane editing = new VariablesPane(prefabs, draft, mock(ItemIcons.class), actions, builder);
        editing.init(DrawnFrame.font());
        editing.show(true);
        prefabs.startEditing("prefab0");
        DrawnFrame frame = new DrawnFrame();
        editing.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Consumer<String>> change =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(builder, atLeastOnce()).openPrefab(eq("prefab0"), anyString(), change.capture(), any());
        change.getValue().accept("\"$base / 8\"");
        verify(actions).draftChanged();
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

    /**
     * A follow into the Prefabs pane empties a search that hides its target and draws the target's block (decision
     * following-a-link-clears-the-panes-search-bar).
     */
    @Test
    void jumpToClearsTheSearchAndDrawsTheTarget() {
        searchBox().setValue("prefab1");
        DrawnFrame searched = new DrawnFrame();
        pane.extract(searched.graphics, font, TALL_VIEW, searched.targets, 0, 0);
        assertFalse(searched.texts().contains("$prefab5"), "the search hides the target");
        pane.jumpTo("prefab5");
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        assertEquals("", searchBox().getValue());
        assertTrue(frame.texts().contains("$prefab5"), frame.texts().toString());
    }

    /**
     * The [+] right of the search box opens a name box with its done check and gold arrow; Enter on a free name adds
     * $name to the draft with no value, lists it and opens its builder, and a name the list holds adds nothing
     * (decision goo-workbench-opens-the-in-game-manager-screen: the page's Variables pane added constants).
     */
    @Test
    void plusRightOfTheSearchNamesANewPrefabAndOpensItsBuilder() {
        List<AbstractWidget> widgets = new java.util.ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ManagerDraft draft = new ManagerDraft();
        draft.read(model());
        VariablesTab prefabs = new VariablesTab(draft);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), VIEW, list.targets, 0, 0);
        DrawnFrame.Text plus = list.find("+");
        assertTrue(plus.x() > VIEW.left() + 180, plus + " stands right of the search box");
        list.click("+");
        DrawnFrame naming = new DrawnFrame();
        read.extract(naming.graphics, DrawnFrame.font(), VIEW, naming.targets, 0, 0);
        EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(each -> each.visible && each != read.searchBox()).findFirst()
                .orElseThrow(() -> new AssertionError("no name box stood open"));
        assertNameBoxButtons(naming, box);
        verify(actions).focus(box);
        GroupsPaneTest.GroupCards.focus(box);
        box.setValue("fresh_prefab");
        assertTrue(read.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER));
        assertEquals(java.util.Optional.of(""), prefabs.expressionOf("fresh_prefab").map(ManagerDraft::shownText));
        assertEquals(java.util.Optional.of("fresh_prefab"), prefabs.editing());
        verify(actions).draftChanged();
        DrawnFrame opened = new DrawnFrame();
        read.extract(opened.graphics, DrawnFrame.font(), VIEW, opened.targets, 0, 0);
        DrawnFrame settled = new DrawnFrame();
        read.extract(settled.graphics, DrawnFrame.font(), VIEW, settled.targets, 0, 0);
        assertTrue(settled.texts().contains("$fresh_prefab"), settled.texts().toString());
        assertTrue(settled.texts().stream().anyMatch(text -> text.endsWith(ControlFlow.dropdownLabel(""))),
                settled.texts().toString());

        assertTrue(settled.targets.click(plus.x() + 1, plus.y() + 1), "the + stands where it stood");
        read.extract(new DrawnFrame().graphics, DrawnFrame.font(), VIEW, new ClickTargets(), 0, 0);
        box.setValue("prefab0");
        assertTrue(read.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER));
        assertEquals(PREFABS + 1, prefabs.blocks().size(), "a taken name adds nothing");
    }

    /**
     * A prefab being edited draws its builder's gray panel wholly below its Used by item icons, so the panel never
     * covers an icon's bottom row; it covered one row before.
     */
    @Test
    void builderPanelStandsBelowTheUsedByIcons() {
        variables.readEvaluated("""
                {"constants": [{"name": "prefab0", "kind": "scalar", "value": "8",
                                "direct": ["minecraft:apple", "minecraft:bread"]}]}
                """);
        variables.startEditing("prefab0");
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        ArgumentCaptor<Integer> itemTops = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).item(any(), anyInt(), itemTops.capture());
        int panelTop = fillTopsAt(frame, Palette.PANEL_FILL).getFirst();
        for (int top : itemTops.getAllValues()) {
            assertTrue(top + SPRITE <= panelTop, "icon " + top + ".." + (top + SPRITE) + " vs panel top " + panelTop);
        }
    }
}
