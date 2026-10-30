package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
}
