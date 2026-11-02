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
     * Consecutive prefabs stand one tighter pitch apart; it was 39 pixels for a number prefab used nowhere before its
     * value took a line of its own (decisions prefab-lines-tighten-their-padding,
     * prefab-head-is-name-type-used-by-and-x-on-one-line).
     */
    @Test
    void prefabRowsTakeTheTighterPitch() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, new GroupsPane.Bounds(0, 20, 400, 4000), frame.targets, 0, 0);
        List<Integer> tops = frame.topsOf(List.of("prefab0", "prefab1"));
        int pitch = tops.get(1) - tops.get(0);
        assertEquals(VariablesPane.UNUSED_PREFAB_PITCH, pitch);
        assertTrue(pitch - ExpressionLines.TEXT_LINE_HEIGHT < 39, "the pitch was 39 before the value's line");
    }

    /**
     * A prefab with type values names its kind typed, and tree draws nowhere on the tab (decision
     * type-valued-prefabs-read-typed).
     */
    @Test
    void typeValuedPrefabReadsTyped() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": 4}", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane typed = new VariablesPane(new VariablesTab(draft), draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        typed.extract(frame.graphics, font, VIEW, frame.targets, 0, 0);
        assertTrue(frame.texts().contains("typed"), frame.texts().toString());
        assertFalse(frame.texts().contains("tree"), frame.texts().toString());
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
            if (colors.getAllValues().get(index) == RowLayout.SEPARATOR_COLOR) {
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
     * A prefab's head reads its name with a pencil just past it, = for a number prefab, and a red x at the row's
     * right end (the operator's rulings).
     */
    @Test
    void headDrawsPencilByTheNameEqualsAndARedXAtTheEnd() {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, TALL_VIEW, frame.targets, 0, 0);
        DrawnFrame.Text name = frame.drawn().stream().filter(text -> text.text().equals("prefab0")).findFirst()
                .orElseThrow();
        DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")).findFirst()
                .orElseThrow();
        int pastName = name.x() + "prefab0".length() * 6;
        assertEquals(name.y(), pencil.y());
        assertTrue(pencil.x() > pastName && pencil.x() < pastName + 12, pencil + " vs " + name);
        assertTrue(frame.texts().contains("="), frame.texts().toString());
        assertFalse(frame.texts().contains("scalar") || frame.texts().contains("Rename")
                || frame.texts().contains("Delete"), frame.texts().toString());
        DrawnFrame.Text cross = frame.drawn().stream().filter(text -> text.text().equals("✗")).findFirst()
                .orElseThrow();
        assertEquals(ProcessesPane.RED, cross.color());
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
        List<Integer> names = settled.topsOf(IntStream.range(0, PREFABS).mapToObj(index -> "prefab" + index)
                .toList());
        return names.stream().map(y -> y - names.getFirst()).toList();
    }

    /**
     * A shift + left follow to a prefab well below the fold opens its block for editing and centers the block in the
     * list (decision shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void jumpCentersTheEditedBlockInTheList() {
        int target = 8;
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
        draw();
        pane.jumpTo("prefab" + target);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
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
        assertTrue(confirm.underlined("egg"));
        assertFalse(confirm.underlined("Delete"));
    }

    /**
     * A scalar prefab's head reads =, its number on the value line below, and its pencil opens the rename box in the
     * name's place (decision prefab-head-is-name-type-used-by-and-x-on-one-line).
     */
    @Test
    void scalarHeadReadsEqualsWithItsNumberBelowAndThePencilRenames() {
        VariablesPane read = readByEgg(mock(ManagerActions.class));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets, 0, 0);
        DrawnFrame.Text equals = frame.find("=");
        DrawnFrame.Text number = frame.drawn().stream().filter(text -> text.text().equals("8")
                && text.color() == 0xFF80E080).findFirst().orElseThrow(() -> new AssertionError(frame.drawn()
                .toString()));
        assertTrue(number.y() > equals.y(), number + " below " + equals);
        assertFalse(frame.texts().stream().anyMatch(text -> text.toLowerCase(java.util.Locale.ROOT)
                .contains("scalar")), frame.texts().toString());
        frame.click("✎");
        DrawnFrame renaming = new DrawnFrame();
        read.extract(renaming.graphics, DrawnFrame.font(), VIEW, renaming.targets, 0, 0);
        assertFalse(renaming.texts().contains("base"), renaming.texts().toString());
        assertFalse(renaming.texts().contains("✎"), renaming.texts().toString());
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

    // A Prefabs pane over wood and plank, every hover request answered at once with wood's card.
    private static VariablesPane woodReadByPlankAndLogs(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        draft.read(READ_PREFABS);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated(READ_EVALUATED);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        org.mockito.Mockito.doAnswer(call -> {
            if (call.<String>getArgument(0).equals(com.mercuriusxeno.goo.workbench.WorkbenchOperations
                    .CONSTANT_HOVER)) {
                call.<java.util.function.Consumer<String>>getArgument(2).accept("{" + WOOD_READ
                        + ", \"expression\": \"8\"}");
            }
            return null;
        }).when(actions).request(anyString(), anyString(), any());
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        read.init(DrawnFrame.font());
        read.show(true);
        return read;
    }

    private static DrawnFrame.Text linkText(DrawnFrame frame, String label) {
        return frame.drawn().stream().filter(text -> text.text().equals(label) && text.color() == ButtonPainter.LINK)
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
        DrawnFrame.Text plank = linkText(frame, "plank");
        DrawnFrame.Text logs = linkText(frame, "#logs");
        assertTrue(itemXs(frame).getFirst() < plank.x(), "stick before plank");
        assertTrue(plank.x() < logs.x(), plank + " before " + logs);
        assertEquals(plank.y(), logs.y());
    }

    /**
     * wood's hover card, opened from plank's $wood link, reads the same run: the stick, then plank's and logs' chips,
     * and no cake (decision used-by-lists-direct-items-and-links-to-prefabs-and-groups).
     */
    @Test
    void hoverCardUsedByDrawsDirectItemsThenPrefabAndGroupLinks() {
        VariablesPane read = woodReadByPlankAndLogs(mock(ManagerActions.class));
        DrawnFrame list = new DrawnFrame();
        read.extract(list.graphics, DrawnFrame.font(), TALL_VIEW, list.targets, 0, 0);
        DrawnFrame.Text woodLink = linkText(list, "wood");
        read.extract(new DrawnFrame().graphics, DrawnFrame.font(), TALL_VIEW, new ClickTargets(), woodLink.x() + 1,
                woodLink.y() + 1);
        DrawnFrame card = new DrawnFrame();
        read.extract(card.graphics, DrawnFrame.font(), TALL_VIEW, card.targets, woodLink.x() + 1, woodLink.y() + 1);
        assertEquals(2, card.texts().stream().filter("Used by "::equals).count(), card.texts().toString());
        assertFalse(card.targets.hoverKeys().contains(ItemIcons.hoverKey("minecraft:cake")));
        assertEquals(2, itemXs(card).size(), "the stick on the block and on the card");
        DrawnFrame.Text plank = linkText(card, "plank");
        DrawnFrame.Text logs = linkText(card, "#logs");
        assertTrue(itemXs(card).getLast() < plank.x(), "stick before plank on the card");
        assertTrue(plank.x() < logs.x(), plank + " before " + logs);
        assertTrue(plank.y() > woodLink.y(), "the card's chips draw on the card");
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
        DrawnFrame.Text plank = linkText(frame, "plank");
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
     * A typed prefab's head holds its name, its kind, its used-by run and its ✗ on one line, and its resolved value
     * draws on its own line below, above the read view (decision prefab-head-is-name-type-used-by-and-x-on-one-line).
     */
    @Test
    void headHoldsNameKindUsedByAndXWithTheValueBelow() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                    {"key": "wood", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": 4}", "entries": [],
                     "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        VariablesTab prefabs = new VariablesTab(draft);
        prefabs.readEvaluated("""
                {"constants": [{"name": "wood", "kind": "tree", "tree": {"leaf": "4"}, "value": null,
                                "error": null, "direct": ["minecraft:stick"], "usedBy": [], "readers": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        VariablesPane read = new VariablesPane(prefabs, draft, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        read.extract(frame.graphics, DrawnFrame.font(), TALL_VIEW, frame.targets, 0, 0);
        int headY = frame.find("wood").y();
        assertEquals(headY, frame.find("typed").y());
        assertEquals(headY, frame.find("✗").y());
        ArgumentCaptor<Integer> itemYs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics).item(any(), anyInt(), itemYs.capture());
        int iconY = itemYs.getValue();
        assertTrue(iconY <= headY && iconY + SPRITE >= headY + GAME_LINE_HEIGHT, "icon " + iconY + " head " + headY);
        ArgumentCaptor<Integer> typeYs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).blit(any(), any(), anyInt(), typeYs.capture(), org.mockito.ArgumentMatchers
                .anyFloat(), org.mockito.ArgumentMatchers.anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        int resolvedY = typeYs.getAllValues().getFirst();
        assertTrue(resolvedY > headY, "resolved " + resolvedY + " head " + headY);
        int readViewY = fillTopsAt(frame, 0x30FFFFFF).getFirst();
        assertTrue(readViewY > resolvedY, "read view " + readViewY + " resolved " + resolvedY);
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
            assertTrue(after.texts().contains("prefab" + (PREFABS - 1)), after.texts().toString());
            DrawnFrame.Text arrow = after.drawn().stream().filter(text -> text.text().equals("↺")).findFirst()
                    .orElseThrow(() -> new AssertionError(after.texts().toString()));
            assertEquals(cross.x(), arrow.x());
            assertEquals(cross.y(), arrow.y());
            assertEquals(ButtonPainter.GOLD, arrow.color());
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
        assertTrue(confirm.texts().contains("egg"), confirm.texts().toString());
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

    // A Prefabs pane over one prefab, base, that the prefab egg reads.
    private static VariablesPane readByEgg(ManagerActions actions) {
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
