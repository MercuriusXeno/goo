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
import static org.mockito.ArgumentMatchers.eq;
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
     * Once a follow centers a group's row, the next frame fills the row's bounds in the glow color, and once the
     * glow's frames have passed no frame fills it (decision followed-target-glows-briefly).
     */
    @Test
    void revealedGroupsRowGlowsThenStops() {
        List<Integer> tops = rowTops();
        GroupsPane pane = manyGroups(new GroupsTab(new ManagerDraft()));
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int target = 25;
        pane.reveal(groupName(target));
        DrawnFrame centering = new DrawnFrame();
        pane.extract(centering.graphics, DrawnFrame.font(), SHORT_VIEW, centering.targets);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        ValuesPaneTest.assertGlowCovers(frame, frame.find("#" + groupName(target)),
                tops.get(target + 1) - tops.get(target));
        for (int extracted = 1; extracted < RevealGlow.FRAMES; extracted++) {
            DrawnFrame fading = new DrawnFrame();
            pane.extract(fading.graphics, DrawnFrame.font(), SHORT_VIEW, fading.targets);
        }
        DrawnFrame after = new DrawnFrame();
        pane.extract(after.graphics, DrawnFrame.font(), SHORT_VIEW, after.targets);
        ValuesPaneTest.assertNoGlow(after);
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
     * The list takes 70% of the pane, its names and the detail's member labels drawing more characters at the same
     * font, the member labels reaching the detail's right edge with their text centered on the row; [+] and Rename
     * stand right of the group's name, [+] opening the member picker (decisions
     * member-list-takes-30-percent-with-no-indent, group-detail-puts-plus-and-rename-beside-the-name).
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

        /**
         * Every tooltip hint a drawn Groups frame registers reads Rename, Delete or a cut group name in full
         * (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
         */
        @Test
        void everyTooltipHintIsRenameDeleteOrACutName() {
            List<String> hints = frame().targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                    .flatMap(java.util.Optional::stream).map(component -> component.getString()).distinct().toList();
            assertTrue(hints.containsAll(List.of("Rename", "Delete")), hints.toString());
            assertTrue(hints.stream().allMatch(hint -> hint.equals("Rename") || hint.equals("Delete")
                    || hint.contains(LONG_GROUP)), hints.toString());
        }

        // The detail draws its scissor after the list's, so the last scissor set is the detail's.
        private static int detailScissorLeft(DrawnFrame frame) {
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).enableScissor(lefts.capture(), anyInt(), anyInt(), anyInt());
            return lefts.getAllValues().getLast();
        }

        private List<DrawnFrame.Text> startingWith(DrawnFrame frame, String prefix) {
            return frame.drawn().stream().filter(text -> !text.text().isEmpty() && prefix.startsWith(text.text()))
                    .toList();
        }

        // Decision member-list-takes-30-percent-with-no-indent.
        @Test
        void listTakesSixtyFivePercentAndItsNamesDrawMoreCharacters() {
            DrawnFrame frame = frame();
            DrawnFrame.Text listed = startingWith(frame, "#" + LONG_GROUP).getFirst();
            int sixtyFivePercent = PANE.left() + (PANE.right() - PANE.left()) * 13 / 20;
            assertEquals(sixtyFivePercent, GroupsPane.listWidth(PANE));
            assertEquals(sixtyFivePercent + GroupsPane.GAP, detailScissorLeft(frame));
            assertTrue(listed.x() < GroupsPane.listWidth(PANE), listed.toString());
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
            DrawnFrame.Text name = startingWith(frame, "#" + LONG_GROUP).getFirst();
            DrawnFrame.Text plus = frame.find("+");
            DrawnFrame.Text pencil = frame.findFirst("✎");
            assertTrue(pencil.x() > name.x() + name.text().length() * 6, pencil + " vs " + name);
            assertTrue(Math.abs(pencil.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT, pencil + " vs " + name);
            assertTrue(frame.drawn().stream().filter(text -> text.text().startsWith("minecraft:"))
                    .allMatch(member -> member.y() > plus.y()), frame.drawn().toString());
            assertFalse(frame.texts().contains("Rename"), frame.texts().toString());
            assertFalse(frame.texts().contains("Add member"), frame.texts().toString());
            DrawnFrame.Text cross = frame.find("✗");
            int listRight = GroupsPane.listWidth(PANE) - ScrollBar.WIDTH - ScrollBar.GAP;
            assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x(), "the x stands at the card's right end");
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
            DrawnFrame named = frame();
            named.clickFirst("✎");
            DrawnFrame renaming = frame();
            assertEquals(named.count("✎") - 1, renaming.count("✎"), "the rename box stands in the pencil's place");
            assertTrue(widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .anyMatch(box -> box.visible && LONG_GROUP.equals(box.getValue())
                            && box.getX() < GroupsPane.listWidth(PANE)), "a box stands on the card");
        }

        /**
         * The open rename box takes a ✓ and a gold ↺ right of it on its row; ✓ writes the typed name, as Enter does,
         * and ↺ closes the box leaving the name, as Escape does (decisions open-name-box-takes-a-done-checkmark,
         * open-name-box-takes-a-gold-arrow-that-cancels).
         */
        @Test
        void renameBoxTakesADoneCheckAndANevermindArrow() {
            draft.read("""
                    {"path": "base_values.json", "members": [
                      {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                         {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "[]",
                          "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                       "leading": null, "trailing": null, "closingGap": null}]}""".formatted(LONG_GROUP));
            frame().clickFirst("✎");
            DrawnFrame reopenedFirst = frame();
            EditBox box = openBox();
            box.setValue("other");
            reopenedFirst.click("↺");
            assertFalse(box.visible);
            assertEquals(LONG_GROUP, groupKey());
            frame().clickFirst("✎");
            DrawnFrame open = frame();
            VariablesPaneTest.assertNameBoxButtons(open, openBox());
            openBox().setValue("renamed");
            open.click("✓");
            assertEquals("renamed", groupKey());
        }

        private EditBox openBox() {
            return widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .filter(each -> each.visible && !"Search groups".equals(each.getMessage().getString()))
                    .findFirst().orElseThrow();
        }

        private String groupKey() {
            return draft.sectionEntries("_groups").get(0).getAsJsonObject().get("key").getAsString();
        }

        /**
         * The red x marks the group: its row still draws with a gold ↺ hinting Restore where the x stood and the
         * draft still holds it; the ↺ restores the x; with the mark standing, the save path removes the group from
         * the draft (decision every-delete-marks-for-removal-and-a-gold-arrow-restores).
         */
        @Test
        void redXMarksTheGroupAndTheArrowRestoresIt() {
            draft.read("""
                    {"path": "base_values.json", "members": [
                      {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                         {"key": "%s", "keySeparator": null, "kind": "VALUE", "text": "[]",
                          "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                       "leading": null, "trailing": null, "closingGap": null}]}""".formatted(LONG_GROUP));
            DrawnFrame frame = frame();
            DrawnFrame.Text cross = frame.find("✗");
            assertEquals(ProcessesPane.RED, cross.color());
            frame.click("✗");
            DrawnFrame marked = frame();
            DrawnFrame.Text arrow = marked.find("↺");
            assertEquals(cross.x(), arrow.x());
            assertEquals(cross.y(), arrow.y());
            assertEquals(ButtonPainter.GOLD, arrow.color());
            assertEquals(Optional.of(BuilderOverlays.hintKey("Restore")),
                    marked.targets.hovered(arrow.x() + 1, arrow.y() + 1));
            assertFalse(startingWith(marked, "#" + LONG_GROUP).isEmpty(), marked.texts().toString());
            assertEquals(1, draft.sectionEntries("_groups").size());
            marked.click("↺");
            DrawnFrame restored = frame();
            assertEquals(ProcessesPane.RED, restored.find("✗").color());
            restored.click("✗");
            pane.applyRemovals();
            assertEquals(0, draft.sectionEntries("_groups").size());
        }
    }

    /**
     * A group card reads #name with its pencil, or "tag" for a tag referent, and its x on line 1, "Members:" and the
     * member icons one indent in on line 2, then the value one indent in with no label; the detail column holds the
     * membership alone (decisions group-card-is-name-then-members-then-value,
     * detail-is-members-plus-or-tag-members-with-the-tag-id).
     */
    @Nested
    class GroupCards {
        private static final GroupsPane.Bounds PANE = new GroupsPane.Bounds(0, 0, 500, 300);
        private static final int LIST_RIGHT = GroupsPane.listWidth(PANE) - ScrollBar.WIDTH - ScrollBar.GAP;
        private static final int DETAIL_LEFT = GroupsPane.listWidth(PANE) + 12;
        private final ManagerDraft draft = new ManagerDraft();
        private final GroupsTab groups = new GroupsTab(draft);
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final GroupsPane pane;

        GroupCards() {
            draft.read("""
                    {"path": "base_values.json", "members": [
                      {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                         {"key": "planks", "keySeparator": null, "kind": "VALUE", "text": "[\\"oak_planks\\"]",
                          "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                       "leading": null, "trailing": null, "closingGap": null}]}""");
            groups.readDescribed("""
                    {"groups": [
                      {"name": "planks", "kind": "group", "members": [{"id": "minecraft:oak_planks", "known": true}],
                       "base": "8", "tweaks": [], "preview": []},
                      {"name": "logs", "kind": "tag", "members": [{"id": "minecraft:oak_log", "known": true}],
                       "base": "4", "tweaks": [], "preview": []}]}""");
            ValuesTab values = new ValuesTab(draft);
            values.readEntries(new ListedEntrySource(List.of(), List.of(ListedEntrySource.sizedTag("minecraft:logs",
                    40))));
            ManagerActions actions = mock(ManagerActions.class);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
                widgets.add(call.getArgument(0));
                return call.getArgument(0);
            });
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            pane = new GroupsPane(groups, values, icons, actions,
                    new ExpressionBuilderPane(actions, icons, List::of, List::of));
            pane.init(DrawnFrame.font());
            pane.show(true);
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), PANE, frame.targets);
            return frame;
        }

        private static List<DrawnFrame.Text> inList(DrawnFrame frame, String label) {
            return frame.drawn().stream().filter(text -> text.text().equals(label) && text.x() < LIST_RIGHT).toList();
        }

        private static List<DrawnFrame.Text> inDetail(DrawnFrame frame) {
            return frame.drawn().stream().filter(text -> text.x() >= DETAIL_LEFT).toList();
        }

        // EditBox.setFocused reaches the game client, which a unit test holds none of, so the flag is set directly.
        static void focus(EditBox box) {
            try {
                java.lang.reflect.Field focused = AbstractWidget.class.getDeclaredField("focused");
                focused.setAccessible(true);
                focused.setBoolean(box, true);
            } catch (ReflectiveOperationException unreachable) {
                throw new AssertionError(unreachable);
            }
        }

        private static boolean sameLine(DrawnFrame.Text one, DrawnFrame.Text other) {
            return Math.abs(one.y() - other.y()) < ExpressionLines.TEXT_LINE_HEIGHT;
        }

        private static List<Integer> itemXsBetween(DrawnFrame frame, int top, int bottom) {
            ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).item(any(), xs.capture(), ys.capture());
            List<Integer> found = new ArrayList<>();
            for (int index = 0; index < xs.getAllValues().size(); index++) {
                int y = ys.getAllValues().get(index);
                if (y >= top && y < bottom) {
                    found.add(xs.getAllValues().get(index));
                }
            }
            return found;
        }

        @Test
        void cardReadsNameLineThenMembersThenTheValueIndented() {
            DrawnFrame frame = frame();
            DrawnFrame.Text planks = inList(frame, "#planks").getFirst();
            DrawnFrame.Text logs = inList(frame, "#logs").getFirst();
            assertTrue(sameLine(planks, inList(frame, "✎").getFirst()), frame.drawn().toString());
            assertTrue(sameLine(logs, inList(frame, "tag").getFirst()), frame.drawn().toString());
            List<DrawnFrame.Text> crosses = inList(frame, "✗");
            assertEquals(2, crosses.size(), frame.drawn().toString());
            crosses.forEach(cross -> assertEquals(LIST_RIGHT - 6 - ButtonPainter.PADDING, cross.x()));
            assertTrue(sameLine(planks, crosses.get(0)) && sameLine(logs, crosses.get(1)), crosses.toString());
            List<DrawnFrame.Text> members = inList(frame, "Members:");
            assertEquals(2, members.size(), frame.drawn().toString());
            DrawnFrame.Text planksMembers = members.getFirst();
            assertTrue(planksMembers.y() > planks.y() && planksMembers.y() < logs.y(), planksMembers.toString());
            assertEquals(ControlFlow.INDENT, planksMembers.x());
            int icon = itemXsBetween(frame, planksMembers.y() - 8, planksMembers.y() + 8).getFirst();
            assertTrue(icon >= planksMembers.x() + "Members:".length() * 6, "icon at " + icon);
            DrawnFrame.Text value = inList(frame, "8").getFirst();
            assertTrue(value.y() > planksMembers.y() && value.y() < logs.y(), value.toString());
            // Decisions read-mode-builder-is-a-button-with-a-pencil-segment,
            // group-card-shows-its-goo-value-above-a-labelled-builder: the value stands inside its frame past Value:.
            assertEquals(ControlFlow.INDENT + "Value:".length() * 6 + BuilderOverlays.GAP + ButtonPainter.PADDING,
                    value.x());
            assertTrue(inList(frame, "4").getFirst().y() > members.getLast().y(), frame.drawn().toString());
            assertFalse(frame.texts().contains("Value"), frame.texts().toString());
        }

        @Test
        void pencilOpensTheRenameBoxOnTheCardAndEnterRenames() {
            DrawnFrame frame = frame();
            DrawnFrame.Text planks = inList(frame, "#planks").getFirst();
            DrawnFrame.Text pencil = inList(frame, "✎").getFirst();
            assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1));
            DrawnFrame open = frame();
            EditBox box = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .filter(each -> each.visible && "planks".equals(each.getValue())).findFirst().orElseThrow();
            assertEquals(planks.x(), box.getX());
            assertTrue(Math.abs(box.getY() - planks.y()) < ExpressionLines.TEXT_LINE_HEIGHT, box.getY() + " vs "
                    + planks);
            VariablesPaneTest.assertNameBoxButtons(open, box);
            box.setValue("boards");
            focus(box);
            assertTrue(pane.keyPressed(GLFW.GLFW_KEY_ENTER));
            assertEquals("boards", draft.sectionEntries("_groups").get(0).getAsJsonObject().get("key").getAsString());
            assertFalse(inList(frame(), "#boards").isEmpty(), frame().texts().toString());
        }

        @Test
        void detailHoldsMembersAndPlusForAGroupAndTagMembersWithTheTagIdForATag() {
            groups.selectGroup("planks");
            DrawnFrame group = frame();
            DrawnFrame.Text members = group.find("Members");
            DrawnFrame.Text plus = inDetail(group).stream().filter(text -> text.text().equals("+")).findFirst()
                    .orElseThrow(() -> new AssertionError(group.drawn().toString()));
            assertTrue(sameLine(members, plus) && plus.x() > members.x(), plus + " vs " + members);
            DrawnFrame.Text card = inDetail(group).stream().filter(text -> text.text().equals("minecraft:oak_planks"))
                    .findFirst().orElseThrow();
            // Decision member-list-takes-30-percent-with-no-indent: the member row stands at the detail's left, a
            // custom group's row opening on its rail and number (decision custom-group-members-drag-by-numbered-rails).
            DrawnFrame.Text number = inDetail(group).stream().filter(text -> text.text().equals("1")).findFirst()
                    .orElseThrow(() -> new AssertionError(group.drawn().toString()));
            assertEquals(DETAIL_LEFT + ControlFlow.RAIL_WIDTH + 2, number.x());
            assertEquals(List.of(number.x() + 6 + 2),
                    itemXsBetween(group, card.y() - 8, card.y() + 8).stream().filter(x -> x >= DETAIL_LEFT).toList());
            groups.selectGroup("logs");
            DrawnFrame tag = frame();
            DrawnFrame.Text log = tag.find("minecraft:oak_log");
            assertEquals(List.of(DETAIL_LEFT),
                    itemXsBetween(tag, log.y() - 8, log.y() + 8).stream().filter(x -> x >= DETAIL_LEFT).toList());
            List<String> detail = inDetail(tag).stream().map(DrawnFrame.Text::text).toList();
            assertTrue(detail.containsAll(List.of("Tag members", "#minecraft:logs")), detail.toString());
            assertTrue(tag.find("#minecraft:logs").y() < tag.find("minecraft:oak_log").y(), tag.drawn().toString());
            assertFalse(detail.contains("+") || detail.contains("Value")
                    || detail.stream().anyMatch(text -> text.startsWith("Tag membership")), detail.toString());
        }
    }

    /**
     * A custom group's detail numbers each member and draws a drag rail at its left, a rail drag reordering the
     * members, while a tag group's members draw neither (decision custom-group-members-drag-by-numbered-rails).
     */
    @Nested
    class MemberRails {
        private static final GroupsPane.Bounds PANE = new GroupsPane.Bounds(0, 0, 500, 300);
        private static final int DETAIL_LEFT = GroupsPane.listWidth(PANE) + 12;
        private static final List<String> TRIO = List.of("minecraft:oak_log", "minecraft:birch_log",
                "minecraft:spruce_log");
        private final ManagerDraft draft = new ManagerDraft();
        private final GroupsTab groups = new GroupsTab(draft);
        private final ManagerActions actions = mock(ManagerActions.class);
        private final GroupsPane pane;

        MemberRails() {
            draft.read("""
                    {"path": "base_values.json", "members": []}""");
            groups.readDescribed("""
                    {"groups": [
                      {"name": "trio", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true},
                        {"id": "minecraft:birch_log", "known": true}, {"id": "minecraft:spruce_log", "known": true}],
                       "base": null, "tweaks": [], "preview": []},
                      {"name": "logs", "kind": "tag", "members": [{"id": "minecraft:oak_log", "known": true},
                        {"id": "minecraft:birch_log", "known": true}, {"id": "minecraft:spruce_log", "known": true}],
                       "base": null, "tweaks": [], "preview": []}]}""");
            ValuesTab values = new ValuesTab(draft);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            pane = new GroupsPane(groups, values, icons, actions,
                    new ExpressionBuilderPane(actions, icons, List::of, List::of));
            pane.init(DrawnFrame.font());
            pane.show(true);
        }

        private DrawnFrame frame(String group) {
            groups.selectGroup(group);
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), PANE, frame.targets);
            return frame;
        }

        // The rails the detail column drew, each as left, top, right and bottom.
        private static List<int[]> detailRails(DrawnFrame frame) {
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, org.mockito.Mockito.atLeast(0)).fill(lefts.capture(), tops.capture(),
                    rights.capture(), bottoms.capture(), eq(ControlFlow.RAIL_COLOR));
            return IntStream.range(0, lefts.getAllValues().size()).filter(index ->
                    lefts.getAllValues().get(index) >= DETAIL_LEFT).mapToObj(index -> new int[] {
                        lefts.getAllValues().get(index), tops.getAllValues().get(index),
                        rights.getAllValues().get(index), bottoms.getAllValues().get(index)}).toList();
        }

        private static List<DrawnFrame.Text> detailNumbers(DrawnFrame frame) {
            return frame.drawn().stream().filter(text -> text.x() >= DETAIL_LEFT && text.text().matches("\\d+"))
                    .toList();
        }

        /**
         * The custom group's three members draw 1, 2 and 3 down the detail, each on its member's line between the
         * rail at the detail's left and the member's name, one rail a member.
         */
        @Test
        void customGroupMembersDrawTheirNumberAndARailEach() {
            DrawnFrame frame = frame("trio");
            List<DrawnFrame.Text> numbers = detailNumbers(frame);
            assertEquals(List.of("1", "2", "3"), numbers.stream().map(DrawnFrame.Text::text).toList());
            List<int[]> rails = detailRails(frame);
            assertEquals(3, rails.size(), "a rail a member");
            for (int index = 0; index < TRIO.size(); index++) {
                DrawnFrame.Text name = frame.find(TRIO.get(index));
                DrawnFrame.Text number = numbers.get(index);
                assertTrue(Math.abs(number.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT, number + " vs " + name);
                assertEquals(DETAIL_LEFT, rails.get(index)[0]);
                assertTrue(rails.get(index)[2] <= number.x() && number.x() < name.x(), number + " vs " + name);
            }
        }

        /**
         * The tag group's members draw no number and no rail.
         */
        @Test
        void tagGroupMembersDrawNoNumberAndNoRail() {
            DrawnFrame frame = frame("logs");
            assertEquals(List.of(), detailNumbers(frame));
            assertEquals(List.of(), detailRails(frame).stream().map(java.util.Arrays::toString).toList());
        }

        /**
         * Dragging the third member's rail onto the first member's row posts the members with the third first.
         */
        @Test
        void railDragOntoTheFirstRowMovesTheMemberThere() {
            DrawnFrame frame = frame("trio");
            int[] third = detailRails(frame).get(2);
            int firstRow = frame.find(TRIO.get(0)).y();
            assertTrue(frame.targets.click(third[0] + 1, third[1] + 1));
            frame.targets.drag(third[0] + 1, firstRow);
            frame.targets.release();
            ArgumentCaptor<String> posted = ArgumentCaptor.forClass(String.class);
            verify(actions).request(eq(com.mercuriusxeno.goo.workbench.WorkbenchOperations.APPLY_TO_GROUP),
                    posted.capture(), any());
            assertEquals(List.of("minecraft:spruce_log", "minecraft:oak_log", "minecraft:birch_log"),
                    com.google.gson.JsonParser.parseString(posted.getValue()).getAsJsonObject()
                            .getAsJsonArray("picks").asList().stream().map(pick -> pick.getAsString()).toList());
        }
    }

    /**
     * Copper's card, which the iterator #exposed sources, draws Used by under its Members line with a link naming the
     * iterator that a shift + left click follows to its row; logs, which nothing iterates, draws no Used by line
     * (decision group-card-lists-its-referents-as-iterator-links).
     */
    @Test
    void cardListsTheIteratorsSourcingItAsLinks() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "#exposed", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"#copper @exposed_metal\\"", "entries": [], "leading": null, "trailing": null,
                      "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}""");
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "copper", "kind": "group", "members": [{"id": "minecraft:copper_block", "known": true}],
                   "base": "8", "tweaks": [], "preview": []},
                  {"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "4", "tweaks": [], "preview": []}]}""");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        List<DrawnFrame.Text> members = frame.drawn().stream().filter(text -> text.text().equals("Members:")).toList();
        List<DrawnFrame.Text> usedBy = frame.drawn().stream().filter(text -> text.text().equals("Used by ")).toList();
        assertEquals(1, usedBy.size(), frame.drawn().toString());
        int logs = frame.find("#logs").y();
        assertTrue(usedBy.getFirst().y() > members.getFirst().y() && usedBy.getFirst().y() < logs, usedBy.toString());
        DrawnFrame.Text link = frame.find("#exposed");
        assertEquals(ButtonPainter.LINK, link.color());
        // Decision group-referent-previews-shorthand-members-then-its-value: the iterator previews its group.
        assertEquals(java.util.Optional.of(GroupHovers.hoverKey("exposed")),
                frame.targets.hovered(link.x() + 1, link.y() + 1));
        assertTrue(Math.abs(link.y() - usedBy.getFirst().y()) < ExpressionLines.TEXT_LINE_HEIGHT, link.toString());
        frame.shiftClick("#exposed");
        verify(actions).openMutator("#exposed");
    }

    /**
     * A group more iterators source than its Used by line fits draws the links that fit, then ..+n counting the
     * rest, whose click draws every link (decisions diagnose-and-correct-group-referents-without-the-ellipsis,
     * more-button-reads-dot-dot-plus-n).
     */
    @Test
    void crowdedUsedByLineEndsOnDotDotPlusNThatExpandsIt() {
        int iterators = 20;
        String entries = IntStream.range(0, iterators).mapToObj(index -> """
                {"key": "#iter%02d", "keySeparator": null, "kind": "VALUE", "text": "\\"#copper @exposed_metal\\"",
                 "entries": [], "leading": null, "trailing": null, "closingGap": null}""".formatted(index))
                .collect(Collectors.joining(", "));
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [%s],
                   "leading": null, "trailing": null, "closingGap": null}]}""".formatted(entries));
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "copper", "kind": "group", "members": [{"id": "minecraft:copper_block", "known": true}],
                   "base": "8", "tweaks": [], "preview": []}]}""");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), bounds, frame.targets);
        long shown = frame.texts().stream().filter(text -> text.matches("^#iter\\d\\d$")).count();
        assertTrue(shown > 0 && shown < iterators, frame.texts().toString());
        String more = UsedByLine.moreLabel(iterators - (int) shown);
        frame.click(more);
        DrawnFrame expanded = new DrawnFrame();
        pane.extract(expanded.graphics, DrawnFrame.font(), bounds, expanded.targets);
        assertEquals(iterators, expanded.texts().stream().filter(text -> text.matches("^#iter\\d\\d$")).count(),
                expanded.texts().toString());
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
     * A valued group's card draws its evaluated goo value on the line above its builder, and Value: stands left of
     * the builder's kind dropdown on its line (decision group-card-shows-its-goo-value-above-a-labelled-builder).
     */
    @Test
    void valuedCardShowsItsGooValueAboveAValueLabelledBuilder() {
        ManagerDraft draft = new ManagerDraft();
        ValuesTab values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:oak_log", "Oak Log")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "logs", "keySeparator": null, "kind": "VALUE", "text": "[\\"oak_log\\"]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 37}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_log", "value": {"glow": "37"}, "denied": false}]}
                """);
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "{\\"glow\\": 37}", "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        GroupsPane pane = new GroupsPane(groups, values, icons, actions, builder);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame listed = new DrawnFrame();
        pane.extract(listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        DrawnFrame.Text worth = listed.findFirst("37");
        DrawnFrame.Text members = listed.find("Members:");
        DrawnFrame.Text pencil = listed.find(ReadValueButton.PENCIL);
        assertTrue(worth.y() > members.y() && worth.y() < pencil.y(), worth + " between " + members + " and "
                + pencil);
        assertTrue(listed.targets.click(pencil.x() + 1, pencil.y() + 1));
        DrawnFrame building = new DrawnFrame();
        pane.extract(building.graphics, DrawnFrame.font(), bounds, building.targets);
        DrawnFrame.Text kind = building.drawn().stream().filter(text -> text.text().endsWith(
                ControlFlow.dropdownLabel(""))).findFirst().orElseThrow(() -> new AssertionError(building.texts()
                .toString()));
        assertTrue(building.findFirst("37").y() < kind.y(), building.drawn().toString());
        DrawnFrame.Text label = building.find("Value:");
        // The full-size label and the builder's smaller kind label center on the builder's first line.
        Font font = DrawnFrame.font();
        int labelCenter = label.y() * 2 + font.lineHeight;
        int kindCenter = kind.y() * 2 + new Lettering(font, ControlFlow.BUILDER_SCALE).lineHeight();
        assertTrue(Math.abs(labelCenter - kindCenter) <= 2, label + " vs " + kind);
        assertTrue(label.x() < kind.x(), label + " vs " + kind);
    }

    /**
     * A valued group card draws its value framed with a pencil segment at its right, and a click on the pencil opens
     * the builder in its place (decision read-mode-builder-is-a-button-with-a-pencil-segment).
     */
    @Test
    void valuedCardFramesItsValueAndItsPencilOpensTheBuilder() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "8", "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions, builder);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame listed = new DrawnFrame();
        pane.extract(listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        DrawnFrame.Text value = listed.find("8");
        DrawnFrame.Text pencil = listed.drawn().stream().filter(text -> text.text().equals(ReadValueButton.PENCIL)
                && Math.abs(text.y() - value.y()) < ExpressionLines.TEXT_LINE_HEIGHT).findFirst()
                .orElseThrow(() -> new AssertionError(listed.drawn().toString()));
        assertTrue(pencil.x() > value.x(), pencil + " vs " + value);
        assertTrue(listed.targets.click(pencil.x() + 1, pencil.y() + 1));
        assertTrue(builder.isOpen());
        DrawnFrame building = new DrawnFrame();
        pane.extract(building.graphics, DrawnFrame.font(), bounds, building.targets);
        assertTrue(building.texts().stream().anyMatch(text -> text.endsWith(ControlFlow.dropdownLabel(""))),
                building.texts().toString());
    }

    /**
     * A tag referent lists in the same list as a custom group, and its card's value opens the expression builder in
     * its place, the builder's first line reading its kind then [+] (decisions
     * tag-referent-lists-the-games-members-and-values-them, group-card-is-name-then-members-then-value,
     * builder-is-kind-plus-then-rows-of-terms-with-term-operators).
     */
    @Test
    void tagReferentListsBesideAGroupAndItsCardValueOpensTheBuilder() {
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
        // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
        assertEquals(2, listed.texts().stream().filter(ReadValueButton.NOT_SET::equals).count(),
                listed.texts().toString());
        assertFalse(listed.texts().contains("empty"), listed.texts().toString());
        listed.click(ReadValueButton.NOT_SET);
        assertTrue(builder.isOpen());
        assertEquals(Optional.of("saplings"), groups.selected().map(GroupsTab.Group::name));
        DrawnFrame building = new DrawnFrame();
        pane.extract(building.graphics, DrawnFrame.font(), bounds, building.targets);
        DrawnFrame.Text kind = building.find(ControlFlow.dropdownLabel("by type"));
        assertTrue(kind.x() < GroupsPane.listWidth(bounds), "the builder stands on the card: " + kind);
        // Decision add-type-is-a-big-square-plus-question-button-below-the-list: the +? stands under the kind.
        assertTrue(building.find("+?").y() > kind.y(), building.drawn().toString());
    }
    /**
     * Between two member cards, the second card's rule stands the rule's padding below the first card's sprite
     * (decision rule-takes-padding-above-it).
     */
    @Test
    void memberCardRuleStandsThePaddingBelowTheSpriteAbove() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true},
                    {"id": "minecraft:birch_log", "known": true}], "base": null, "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        groups.selectGroup("logs");
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        int oak = frame.find("minecraft:oak_log").y();
        int birch = frame.find("minecraft:birch_log").y();
        ArgumentCaptor<Integer> itemYs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).item(any(), anyInt(), itemYs.capture());
        int oakSprite = itemYs.getAllValues().stream().filter(y -> y <= oak && y + RowLayout.SPRITE_SIZE > oak)
                .reduce((first, last) -> last).orElseThrow();
        ArgumentCaptor<Integer> fillYs = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).fill(anyInt(), fillYs.capture(), anyInt(), anyInt(),
                eq(RowLayout.SEPARATOR_COLOR));
        int rule = fillYs.getAllValues().stream().filter(y -> y > oak && y < birch).findFirst().orElseThrow();
        assertEquals(RowLayout.RULE_PADDING, rule - (oakSprite + RowLayout.SPRITE_SIZE));
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
        assertTrue(!frame.rightClick("$wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(!builder.isOpen());
        frame.shiftClick("$wood");
        verify(actions).openVariable("wood");
        assertTrue(!builder.isOpen());
        frame.click("$wood");
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
