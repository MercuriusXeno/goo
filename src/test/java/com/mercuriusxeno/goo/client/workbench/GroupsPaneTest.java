package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Groups list's truncated-name hover through its static seams, with a mocked font 6px per character and
 * a name column 78px wide (decision hovering-a-truncated-group-name-shows-the-full-name).
 */
class GroupsPaneTest {
    private static final String WIDE = "#copper_and_waxed_copper_blocks";

    private static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        return font;
    }

    private static final int FOLLOWED_GROUPS = 40;
    private static final GroupsPane.Bounds SHORT_VIEW = new GroupsPane.Bounds(0, 0, 500, 120);
    private static final GroupsPane.Bounds TALL_VIEW = new GroupsPane.Bounds(0, 0, 500, 4000);

    private static String groupName(int index) {
        return "g%02d".formatted(index);
    }

    // A Groups pane over forty groups of one member each, none selected.
    private static GroupsPane manyGroups(GroupsTab groups) {
        String described = IntStream.range(0, FOLLOWED_GROUPS).mapToObj(index -> """
                {"name": "%s", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                 "base": "8", "tweaks": [], "preview": []}""".formatted(groupName(index)))
                .collect(Collectors.joining(", "));
        groups.readDescribed("{\"groups\": [" + described + "]}");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ManagerDraft draft = new ManagerDraft();
        return new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    // Each group row's top inside the list's content, read off a view tall enough to draw every row unscrolled.
    private static List<Integer> rowTops() {
        DrawnFrame tall = new DrawnFrame();
        manyGroups(new GroupsTab(new ManagerDraft())).extract(tall.graphics, DrawnFrame.font(), TALL_VIEW,
                tall.targets);
        List<Integer> names = tall.topsOf(IntStream.range(0, FOLLOWED_GROUPS).mapToObj(index -> "#" + groupName(index))
                .toList());
        return names.stream().map(y -> y - names.getFirst()).toList();
    }

    /**
     * A shift + left follow to a group well below the fold centers its row in the list and selects it (decision
     * shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void revealCentersTheGroupsRowInTheList() {
        List<Integer> tops = rowTops();
        GroupsTab groups = new GroupsTab(new ManagerDraft());
        GroupsPane pane = manyGroups(groups);
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int target = 25;
        pane.reveal(groupName(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        int[] list = frame.firstScissor();
        int rowHeight = tops.get(target + 1) - tops.get(target);
        assertEquals(tops.get(target) + rowHeight / 2 - (list[3] - list[1]) / 2, pane.listBar().offset());
        assertEquals(groupName(target), groups.selected().orElseThrow().name());
    }

    /**
     * A shift + left follow to the last group stops the list at its end (decision
     * shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void revealOfTheLastGroupStopsAtTheListsEnd() {
        GroupsPane pane = manyGroups(new GroupsTab(new ManagerDraft()));
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        pane.reveal(groupName(FOLLOWED_GROUPS - 1));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertTrue(pane.listBar().maximum() > 0);
        assertEquals(pane.listBar().maximum(), pane.listBar().offset());
    }

    /**
     * The Groups pane stands a hinted search box shown with the tab, left of + Custom on one row above the list; typing in it
     * filters the list and returns the list's bar to its top, and a right click on it lists every group again
     * (decisions groups-search-matches-a-name-or-any-member, right-click-empties-every-text-box-in-the-manager).
     */
    @Test
    void searchBoxFiltersTheListAndARightClickEmptiesIt() {
        GroupsTab groups = new GroupsTab(new ManagerDraft());
        List<AbstractWidget> widgets = new ArrayList<>();
        GroupsPane pane = manyGroups(groups, widgets);
        pane.init(DrawnFrame.font());
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search groups".equals(box.getMessage().getString())).findFirst().orElseThrow();
        pane.show(false);
        assertFalse(search.visible);
        pane.show(true);
        assertTrue(search.visible);

        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        DrawnFrame.Text custom = first.find("+ Custom");
        int firstRow = first.find("#" + groupName(0)).y();
        assertTrue(Math.abs(search.getY() + search.getHeight() / 2 - (custom.y() + 4)) <= 4
                && search.getX() + search.getWidth() < custom.x() && search.getY() + search.getHeight() <= firstRow,
                "search at " + search.getX() + "," + search.getY() + ", + Custom at " + custom + ", list at " + firstRow);
        pane.listBar().wheel(-3);
        assertTrue(pane.listBar().offset() > 0);

        search.setValue(groupName(7));
        assertEquals(0, pane.listBar().offset());
        assertEquals(List.of(groupName(7)), groups.groupRows().stream().map(GroupsTab.GroupRow::name).toList());

        ValuesManagerScreen.emptyBoxUnder(widgets, new MouseButtonEvent(search.getX() + 1, search.getY() + 1,
                new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)));
        assertEquals(FOLLOWED_GROUPS, groups.groupRows().size());
    }

    // A Groups pane over forty groups whose actions record every widget the pane stands.
    private static GroupsPane manyGroups(GroupsTab groups, List<AbstractWidget> widgets) {
        String described = IntStream.range(0, FOLLOWED_GROUPS).mapToObj(index -> """
                {"name": "%s", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                 "base": "8", "tweaks": [], "preview": []}""".formatted(groupName(index)))
                .collect(Collectors.joining(", "));
        groups.readDescribed("{\"groups\": [" + described + "]}");
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        return new GroupsPane(groups, new ValuesTab(new ManagerDraft()), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    /**
     * The list takes 60% of the pane, its names and the detail's member labels drawing more characters at the same
     * font, the member labels reaching the detail's right edge with their text centered on the row; [+] and Rename
     * stand right of the group's name, [+] opening the member picker (decisions
     * group-list-takes-60-percent-and-labels-span-the-pane, group-detail-puts-plus-and-rename-beside-the-name).
     */
    @Nested
    class WideList {
        private static final String LONG_GROUP = "copper_and_waxed_copper_blocks";
        private static final String LONG_MEMBER = "minecraft:waxed_weathered_cut_copper_stairs_long";
        private static final GroupsPane.Bounds PANE = new GroupsPane.Bounds(0, 0, 500, 300);
        private final ManagerDraft draft = new ManagerDraft();
        private final GroupsTab groups = new GroupsTab(draft);
        private final GroupsPane pane;

        WideList() {
            groups.readDescribed("""
                    {"groups": [{"name": "%s", "kind": "group", "members": [{"id": "%s", "known": true}],
                      "base": "8", "tweaks": [], "preview": []}]}""".formatted(LONG_GROUP, LONG_MEMBER));
            groups.selectGroup(LONG_GROUP);
            ManagerActions actions = mock(ManagerActions.class);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
                widgets.add(call.getArgument(0));
                return call.getArgument(0);
            });
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                    new ExpressionBuilderPane(actions, icons, List::of, List::of));
            pane.init(DrawnFrame.font());
            pane.show(true);
        }

        private final List<AbstractWidget> widgets = new ArrayList<>();

        // A font six pixels a character wide that cuts a string to the width it is given.
        private static Font cuttingFont() {
            Font font = DrawnFrame.font();
            when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
                String text = call.getArgument(0);
                return text.substring(0, Math.min(text.length(), call.<Integer>getArgument(1) / 6));
            });
            return font;
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, cuttingFont(), PANE, frame.targets);
            return frame;
        }

        private List<DrawnFrame.Text> startingWith(DrawnFrame frame, String prefix) {
            return frame.drawn().stream().filter(text -> !text.text().isEmpty() && prefix.startsWith(text.text()))
                    .toList();
        }

        @Test
        void listTakesSixtyPercentAndItsNamesDrawMoreCharacters() {
            DrawnFrame frame = frame();
            List<DrawnFrame.Text> names = startingWith(frame, "#" + LONG_GROUP);
            DrawnFrame.Text listed = names.getFirst();
            DrawnFrame.Text detailName = names.getLast();
            assertEquals((PANE.right() - PANE.left()) * 3 / 5, GroupsPane.listWidth(PANE));
            assertTrue(detailName.x() > GroupsPane.listWidth(PANE), detailName.toString());
            assertTrue(listed.text().length() * 6 > 78, "the list name drew " + listed.text());
        }

        @Test
        void memberLabelSpansTheDetailAndCentersOnItsRow() {
            DrawnFrame frame = frame();
            DrawnFrame.Text label = startingWith(frame, LONG_MEMBER).getLast();
            int detailRight = PANE.right() - ScrollBar.WIDTH - ScrollBar.GAP;
            assertTrue(label.text().length() * 6 > 110, "the label drew " + label.text());
            assertTrue(label.x() + label.text().length() * 6 <= detailRight);
            ArgumentCaptor<Integer> itemTops = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).item(any(), anyInt(), itemTops.capture());
            int rowTop = itemTops.getAllValues().getLast() - 1;
            assertEquals(rowTop + (18 - DrawnFrame.font().lineHeight + 1) / 2, label.y());
        }

        @Test
        void pencilStandsPastTheNameAndPlusAboveTheMembers() {
            DrawnFrame frame = frame();
            DrawnFrame.Text name = startingWith(frame, "#" + LONG_GROUP).getLast();
            DrawnFrame.Text plus = frame.find("+");
            DrawnFrame.Text pencil = frame.find("✎");
            assertTrue(pencil.x() > name.x() + name.text().length() * 6, pencil + " vs " + name);
            assertTrue(Math.abs(pencil.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT, pencil + " vs " + name);
            assertTrue(plus.y() > frame.find("Value").y(), plus + " stands below the value");
            assertTrue(frame.drawn().stream().filter(text -> text.text().startsWith("minecraft:"))
                    .allMatch(member -> member.y() > plus.y()), frame.drawn().toString());
            assertFalse(frame.texts().contains("Rename"), frame.texts().toString());
            assertFalse(frame.texts().contains("Add member"), frame.texts().toString());
            DrawnFrame.Text cross = frame.find("✗");
            int detailRight = PANE.right() - ScrollBar.WIDTH - ScrollBar.GAP;
            assertEquals(detailRight - 6 - ButtonPainter.PADDING, cross.x(), "the x stands at the head's right end");
            assertTrue(Math.abs(cross.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT, cross + " vs " + name);
            frame.click("+");
            assertTrue(pane.pickerOpen(), "the member picker opens");
        }

        /**
         * The pencil opens the rename box in the name's place (decision
         * rename-is-a-pencil-and-delete-a-red-x-everywhere).
         */
        @Test
        void pencilOpensTheRenameBox() {
            frame().click("✎");
            DrawnFrame renaming = frame();
            assertFalse(renaming.texts().contains("✎"), renaming.texts().toString());
            assertTrue(widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .anyMatch(box -> box.visible && box.getX() > GroupsPane.listWidth(PANE)), "a box stands in the detail");
        }

        /**
         * The red x deletes the group from the draft (decision rename-is-a-pencil-and-delete-a-red-x-everywhere).
         */
        @Test
        void redXDeletesTheGroup() {
            draft.read("""
                    {"path": "base_values.json", "members": [
                      {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                         {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "[]",
                          "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                       "leading": null, "trailing": null, "closingGap": null}]}""".formatted(LONG_GROUP));
            DrawnFrame frame = frame();
            assertEquals(ProcessesPane.RED, frame.find("✗").color());
            frame.click("✗");
            assertEquals(0, draft.sectionEntries("_groups").size());
        }
    }

    @Test
    void wideNameHoversWhole() {
        String key = GroupsPane.nameHoverKey(font(), WIDE, 78).orElseThrow();
        assertEquals(Optional.of(new TooltipLine.Text(Component.literal(WIDE))), GroupsPane.nameTooltipFor(key));
    }

    @Test
    void fittingNameLeavesNoHover() {
        assertEquals(Optional.empty(), GroupsPane.nameHoverKey(font(), "#logs", 78));
    }

    /**
     * A group row holding more members than its line fits ends on a ..+n button counting the rest (decision
     * more-button-reads-dot-dot-plus-n).
     */
    @Test
    void cutShortRowEndsOnDotDotPlusN() {
        String members = IntStream.range(0, 30).mapToObj("{\"id\": \"minecraft:item%d\", \"known\": true}"::formatted)
                .collect(Collectors.joining(", "));
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "many", "members": [%s], "base": null, "tweaks": [], "preview": []}]}
                """.formatted(members));
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertTrue(frame.texts().stream().anyMatch(text -> text.matches("^\\.\\.\\+\\d+$")), frame.texts().toString());
    }

    /**
     * A tag referent lists in the same list as a custom group; its detail draws the line saying its membership
     * isn't controlled here where a custom group's draws Add member, and both draw the value control that opens the
     * expression builder (decision tag-referent-lists-the-games-members-and-values-them).
     */
    @Test
    void tagReferentDetailSaysMembershipIsNotControlledWhereAGroupAddsMembers() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": null, "tweaks": [], "preview": []},
                  {"name": "saplings", "kind": "tag", "members": [{"id": "minecraft:oak_sapling", "known": true}],
                   "base": null, "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions, builder);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 300);

        DrawnFrame listed = new DrawnFrame();
        pane.extract(listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        assertTrue(listed.texts().containsAll(List.of("#logs", "#saplings")), listed.texts().toString());

        groups.selectGroup("logs");
        DrawnFrame group = new DrawnFrame();
        pane.extract(group.graphics, DrawnFrame.font(), bounds, group.targets);
        int groupWorth = group.find("Value").y();
        int addMember = group.find("+").y();
        assertTrue(addMember > groupWorth && addMember < group.find("minecraft:oak_log").y(),
                group.drawn().toString());

        groups.selectGroup("saplings");
        DrawnFrame tag = new DrawnFrame();
        pane.extract(tag.graphics, DrawnFrame.font(), bounds, tag.targets);
        int notControlled = tag.find("Tag membership isn't controlled here").y();
        assertTrue(notControlled > tag.find("#saplings").y() && notControlled < tag.find("Value").y(),
                tag.drawn().toString());
        assertEquals(List.of(), tag.texts().stream().filter("+"::equals).toList());
        tag.click("empty");
        assertTrue(builder.isOpen());
        DrawnFrame building = new DrawnFrame();
        pane.extract(building.graphics, DrawnFrame.font(), bounds, building.targets);
        // Decision every-value-builder-reads-value-kind-on-one-line-defaulting-by-type: the open builder's line
        // reads Value and its kind, the label standing once.
        List<DrawnFrame.Text> values = building.drawn().stream().filter(text -> "Value".equals(text.text())).toList();
        assertEquals(1, values.size(), building.drawn().toString());
        assertEquals(values.getFirst().y(), building.find(ControlFlow.dropdownLabel("by type")).y());
    }

    /**
     * In a group's expression, a right click on a prefab ref runs nothing, a shift + left click on it opens the
     * Prefabs tab on that prefab and a plain left click on the same ref opens the builder over the value (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void groupExpressionRefFollowsOnShiftClickAndOpensTheBuilderOnPlainClick() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                             "base": "$wood / 2", "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions, builder);
        groups.selectGroup("logs");
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        assertTrue(!frame.rightClick("wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(!builder.isOpen());
        frame.shiftClick("wood");
        verify(actions).openVariable("wood");
        assertTrue(!builder.isOpen());
        frame.click("wood");
        assertTrue(builder.isOpen());
    }

    /**
     * + Tag stands beside + Custom, and its press opens the dropdown over the inventory's tags no referent
     * names (decision add-tag-picks-a-game-tag-and-writes-its-entry).
     */
    @Test
    void addTagStandsBesideNewGroupAndOpensTheTagDropdown() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "saplings", "kind": "tag", "members": [], "base": null, "tweaks": [],
                             "preview": []}]}
                """);
        ValuesTab values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(List.of(), List.of(
                ListedEntrySource.sizedTag("minecraft:saplings", 9),
                ListedEntrySource.sizedTag("minecraft:leaves", 8),
                ListedEntrySource.sizedTag("minecraft:logs", 40))));
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        pane.init(DrawnFrame.font());
        pane.show(true);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame closed = new DrawnFrame();
        pane.extract(closed.graphics, DrawnFrame.font(), bounds, closed.targets);
        DrawnFrame.Text newGroup = closed.find("+ Custom");
        DrawnFrame.Text addTag = closed.find("+ Tag");
        assertEquals(newGroup.y(), addTag.y());
        assertFalse(closed.texts().contains("New group") || closed.texts().contains("Add tag"), closed.texts().toString());
        assertTrue(addTag.x() > newGroup.x(), closed.drawn().toString());
        closed.click("+ Tag");
        DrawnFrame open = new DrawnFrame();
        pane.extract(open.graphics, DrawnFrame.font(), bounds, open.targets);
        assertTrue(open.texts().containsAll(List.of("#minecraft:leaves, 8 members", "#minecraft:logs, 40 members")),
                open.texts().toString());
        assertEquals(List.of(), open.texts().stream().filter(text -> text.startsWith("#minecraft:saplings")).toList());
    }
}
