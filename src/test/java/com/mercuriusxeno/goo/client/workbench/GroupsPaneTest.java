package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Test
    void wideNameHoversWhole() {
        String key = GroupsPane.nameHoverKey(font(), WIDE).orElseThrow();
        assertEquals(Optional.of(new TooltipLine.Text(Component.literal(WIDE))), GroupsPane.nameTooltipFor(key));
    }

    @Test
    void fittingNameLeavesNoHover() {
        assertEquals(Optional.empty(), GroupsPane.nameHoverKey(font(), "#logs"));
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
        int groupWorth = group.find("What every member is worth:").y();
        int addMember = group.find("Add member").y();
        assertTrue(addMember > group.find("#logs").y() && addMember < groupWorth, group.drawn().toString());

        groups.selectGroup("saplings");
        DrawnFrame tag = new DrawnFrame();
        pane.extract(tag.graphics, DrawnFrame.font(), bounds, tag.targets);
        int notControlled = tag.find("Tag membership isn't controlled here").y();
        assertTrue(notControlled > tag.find("#saplings").y() && notControlled < groupWorth, tag.drawn().toString());
        assertEquals(groupWorth, tag.find("What every member is worth:").y());
        assertEquals(List.of(), tag.texts().stream().filter("Add member"::equals).toList());
        tag.click("nothing yet");
        assertTrue(builder.isOpen());
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
     * Add tag stands beside New group, and its press opens the dropdown over the inventory's tags no referent
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
        DrawnFrame.Text newGroup = closed.find("New group");
        DrawnFrame.Text addTag = closed.find("Add tag");
        assertEquals(newGroup.y(), addTag.y());
        assertTrue(addTag.x() > newGroup.x(), closed.drawn().toString());
        closed.click("Add tag");
        DrawnFrame open = new DrawnFrame();
        pane.extract(open.graphics, DrawnFrame.font(), bounds, open.targets);
        assertTrue(open.texts().containsAll(List.of("#minecraft:leaves, 8 members", "#minecraft:logs, 40 members")),
                open.texts().toString());
        assertEquals(List.of(), open.texts().stream().filter(text -> text.startsWith("#minecraft:saplings")).toList());
    }
}
