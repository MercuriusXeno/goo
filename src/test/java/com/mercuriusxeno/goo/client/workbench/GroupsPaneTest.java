package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
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
        return manyGroups(groups, mock(ManagerActions.class));
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
     * The Groups pane stands a hinted search box shown with the tab, left of + Custom on one row above the list; typing in it
     * filters the list and returns the list's bar to its top, and a right click on it lists every group again
     * (decisions groups-search-matches-a-name-or-any-member, right-click-empties-every-text-box-in-the-manager).
     */
    @Test
    void searchRowStretchesToCustomAndTagAndThePencilStandsOneGapPastTheName() {
        // Decision one-search-row-per-list-with-its-create-control-beside-it: no 2px offset, 14 tall, one gap short
        // of + Custom, and the rename pencil one pencil gap past the name.
        List<AbstractWidget> widgets = new ArrayList<>();
        GroupsPane pane = manyGroups(new GroupsTab(new ManagerDraft()), widgets);
        pane.init(DrawnFrame.font());
        pane.show(true);
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search groups".equals(box.getMessage().getString())).findFirst().orElseThrow();
        int[] custom = frame.buttonAround(frame.find("+ Custom"));
        assertEquals(List.of(SHORT_VIEW.top(), SearchRow.HEIGHT, custom[0] - SearchRow.GAP),
                List.of(search.getY(), search.getHeight(), search.getX() + search.getWidth()));
        groupsSelectFirst(pane, frame);
    }

    // Selects the first group and checks its card's pencil stands one pencil gap past its name.
    private static void groupsSelectFirst(GroupsPane pane, DrawnFrame list) {
        list.click("#" + groupName(0));
        DrawnFrame card = new DrawnFrame();
        ScreenDraw.extract(pane, card.graphics, DrawnFrame.font(), SHORT_VIEW, card.targets);
        DrawnFrame.Text name = card.drawn().stream().filter(text -> text.text().equals("#" + groupName(0)))
                .reduce((first, last) -> last).orElseThrow();
        DrawnFrame.Text pencil = card.drawn().stream().filter(text -> text.text().equals("✎")
                && Math.abs(text.y() - name.y()) <= 2 && text.x() > name.x()).findFirst()
                .orElseThrow(() -> new AssertionError(card.drawn().toString()));
        assertEquals(name.x() + DrawnFrame.font().width(name.text()) + ButtonPainter.PENCIL_GAP,
                card.buttonAround(pencil)[0]);
    }

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
        ScreenDraw.extract(pane, first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        pane.listBar().wheel(-3);
        assertTrue(pane.listBar().offset() > 0);

        search.setValue(groupName(7));
        assertEquals(0, pane.listBar().offset());
        assertEquals(List.of(groupName(7)), groups.groupRows().stream().map(GroupsTab.GroupRow::name).toList());
    }

    /**
     * A follow into the Groups pane empties a search that hides its target and draws the target's row (decision
     * following-a-link-clears-the-panes-search-bar).
     */
    @Test
    void revealClearsTheSearchAndDrawsTheTarget() {
        GroupsTab groups = new GroupsTab(new ManagerDraft());
        List<AbstractWidget> widgets = new ArrayList<>();
        GroupsPane pane = manyGroups(groups, widgets);
        pane.init(DrawnFrame.font());
        pane.show(true);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search groups".equals(box.getMessage().getString())).findFirst().orElseThrow();
        search.setValue(groupName(7));
        int target = 3;
        DrawnFrame searched = new DrawnFrame();
        ScreenDraw.extract(pane, searched.graphics, DrawnFrame.font(), SHORT_VIEW, searched.targets);
        pane.reveal(groupName(target));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertEquals("", search.getValue());
        assertTrue(frame.texts().contains("#" + groupName(target)), frame.texts().toString());
        // Decision every-follow-lands-selected-and-read-only: the followed group stands selected.
        assertEquals(groupName(target), groups.selected().orElseThrow().name());
    }

    /**
     * The first frame after a follow already draws the group's row centered, where it settles, and glowing at age
     * zero, with the search empty (decision one-reveal-primitive-centers-before-the-first-draw).
     */
    @Test
    void revealDrawsTheRowCenteredAndGlowingOnTheFirstFrame() {
        List<Integer> tops = rowTops();
        List<AbstractWidget> widgets = new ArrayList<>();
        GroupsPane pane = manyGroups(new GroupsTab(new ManagerDraft()), widgets);
        pane.init(DrawnFrame.font());
        pane.show(true);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search groups".equals(box.getMessage().getString())).findFirst().orElseThrow();
        search.setValue(groupName(7));
        DrawnFrame searched = new DrawnFrame();
        ScreenDraw.extract(pane, searched.graphics, DrawnFrame.font(), SHORT_VIEW, searched.targets);
        int target = 25;
        pane.reveal(groupName(target));
        DrawnFrame first = new DrawnFrame();
        ScreenDraw.extract(pane, first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        assertEquals("", search.getValue());
        int[] list = first.firstScissor();
        int rowHeight = tops.get(target + 1) - tops.get(target);
        assertEquals(tops.get(target) + rowHeight / 2 - (list[3] - list[1]) / 2, pane.listBar().offset());
        DrawnFrame.Text name = first.find("#" + groupName(target));
        DrawnFrame settled = new DrawnFrame();
        ScreenDraw.extract(pane, settled.graphics, DrawnFrame.font(), SHORT_VIEW, settled.targets);
        assertEquals(settled.find("#" + groupName(target)).y(), name.y(), "drawn where it settles");
        ValuesPaneTest.assertGlowCovers(first, name, rowHeight);
    }

    /**
     * Enter on the name box with a new group's name empties the search at once, and once the groups list it the
     * created group stands selected (decision create-reveals-the-new-row-like-a-follow).
     */
    @Test
    void createRevealsTheNewGroupOnceTheGroupsListIt() {
        String created = groupName(25) + "a";
        List<String> listed = new ArrayList<>(IntStream.range(0, FOLLOWED_GROUPS).mapToObj(GroupsPaneTest::groupName)
                .toList());
        listed.add(26, created);

        GroupsTab groups = new GroupsTab(new ManagerDraft());
        List<AbstractWidget> widgets = new ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        GroupsPane pane = manyGroups(groups, actions);
        pane.init(DrawnFrame.font());
        pane.show(true);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search groups".equals(box.getMessage().getString())).findFirst().orElseThrow();
        search.setValue(groupName(7));
        DrawnFrame searched = new DrawnFrame();
        ScreenDraw.extract(pane, searched.graphics, DrawnFrame.font(), SHORT_VIEW, searched.targets);
        searched.click("+ Custom");
        EditBox nameBox = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box != search).findFirst().orElseThrow();
        GroupCards.focus(nameBox);
        nameBox.setValue(created);
        assertTrue(pane.keyPressed(GLFW.GLFW_KEY_ENTER));
        assertEquals("", search.getValue(), "the create empties the search at once");

        groups.readDescribed("{\"groups\": [" + describedGroups(listed) + "]}");
        assertEquals(created, groups.selected().orElseThrow().name());
    }

    // The groups reply's entries for each name, one member each.
    private static String describedGroups(List<String> names) {
        return names.stream().map(name -> """
                {"name": "%s", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                 "base": "8", "tweaks": [], "preview": []}""".formatted(name)).collect(Collectors.joining(", "));
    }

    // A Groups pane over forty groups whose actions record every widget the pane stands.
    private static GroupsPane manyGroups(GroupsTab groups, List<AbstractWidget> widgets) {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        return manyGroups(groups, actions);
    }

    // A Groups pane over forty groups answering to the actions given.
    private static GroupsPane manyGroups(GroupsTab groups, ManagerActions actions) {
        groups.readDescribed("{\"groups\": [" + describedGroups(IntStream.range(0, FOLLOWED_GROUPS)
                .mapToObj(GroupsPaneTest::groupName).toList()) + "]}");
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
            ScreenDraw.extract(pane, frame.graphics, cuttingFont(), PANE, frame.targets);
            return frame;
        }

        /**
         * A drawn Groups frame registers the Rename and Delete hints
         * (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
         */
        @Test
        void aGroupsFrameHintsRenameAndDelete() {
            List<String> hints = frame().targets.hoverKeys().stream().map(BuilderOverlays::tooltipFor)
                    .flatMap(Optional::stream).map(component -> component.getString()).distinct().toList();
            assertTrue(hints.containsAll(List.of("Rename", "Delete")), hints.toString());
        }

        private List<DrawnFrame.Text> startingWith(DrawnFrame frame, String prefix) {
            return frame.drawn().stream().filter(text -> !text.text().isEmpty() && prefix.startsWith(text.text()))
                    .toList();
        }

        // Decision member-list-takes-30-percent-with-no-indent.
        @Test
        void listTakesSixtyFivePercent() {
            assertEquals(PANE.left() + (PANE.right() - PANE.left()) * 13 / 20, GroupsPane.listWidth(PANE));
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
            DrawnFrame.Text cross = frame.find("✗");
            int listRight = GroupsPane.listWidth(PANE) - ScrollBar.WIDTH - ScrollBar.GAP;
            assertEquals(listRight - 6 - ButtonPainter.PADDING, cross.x(), "the x stands at the card's right end");
            assertTrue(Math.abs(cross.y() - name.y()) < ExpressionLines.TEXT_LINE_HEIGHT, cross + " vs " + name);
            frame.click("+");
            assertTrue(pane.pickerOpen(), "the member picker opens");
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
            assertEquals(Palette.DANGER, cross.color());
            frame.click("✗");
            DrawnFrame marked = frame();
            DrawnFrame.Text arrow = marked.find("↺");
            assertEquals(cross.x(), arrow.x());
            assertEquals(cross.y(), arrow.y());
            assertEquals(Palette.GOLD, arrow.color());
            assertEquals(Optional.of(BuilderOverlays.hintKey("Restore")),
                    marked.targets.hovered(arrow.x() + 1, arrow.y() + 1));
            assertFalse(startingWith(marked, "#" + LONG_GROUP).isEmpty(), marked.texts().toString());
            assertEquals(1, draft.sectionEntries("_groups").size());
            marked.click("↺");
            DrawnFrame restored = frame();
            assertEquals(Palette.DANGER, restored.find("✗").color());
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
            ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), PANE, frame.targets);
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
                Field focused = AbstractWidget.class.getDeclaredField("focused");
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
            assertEquals(ControlFlow.INDENT + "Value:".length() * 6 + BuilderFrame.LABEL_GAP + ButtonPainter.PADDING,
                    value.x());
            assertTrue(inList(frame, "4").getFirst().y() > members.getLast().y(), frame.drawn().toString());
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
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        List<DrawnFrame.Text> members = frame.drawn().stream().filter(text -> text.text().equals("Members:")).toList();
        List<DrawnFrame.Text> usedBy = frame.drawn().stream().filter(text -> text.text().equals("Used by ")).toList();
        assertEquals(1, usedBy.size(), frame.drawn().toString());
        int logs = frame.find("#logs").y();
        assertTrue(usedBy.getFirst().y() > members.getFirst().y() && usedBy.getFirst().y() < logs, usedBy.toString());
        DrawnFrame.Text link = frame.find("#exposed");
        assertEquals(Palette.LINK, link.color());
        // Decision group-referent-previews-shorthand-members-then-its-value: the iterator previews its group.
        assertEquals(Optional.of(GroupHovers.iteratorHoverKey("#exposed")),
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
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), bounds, frame.targets);
        long shown = frame.texts().stream().filter(text -> text.matches("^#iter\\d\\d$")).count();
        assertTrue(shown > 0 && shown < iterators, frame.texts().toString());
        String more = UsedByLine.moreLabel(iterators - (int) shown);
        frame.click(more);
        DrawnFrame expanded = new DrawnFrame();
        ScreenDraw.extract(pane, expanded.graphics, DrawnFrame.font(), bounds, expanded.targets);
        assertEquals(iterators, expanded.texts().stream().filter(text -> text.matches("^#iter\\d\\d$")).count(),
                expanded.texts().toString());
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
        ScreenDraw.extract(pane, listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        DrawnFrame.Text worth = listed.findFirst("37");
        DrawnFrame.Text members = listed.find("Members:");
        DrawnFrame.Text pencil = listed.find(ReadValueButton.PENCIL);
        assertTrue(worth.y() > members.y() && worth.y() < pencil.y(), worth + " between " + members + " and "
                + pencil);
        assertTrue(listed.targets.click(pencil.x() + 1, pencil.y() + 1));
        DrawnFrame building = new DrawnFrame();
        ScreenDraw.extract(pane, building.graphics, DrawnFrame.font(), bounds, building.targets);
        DrawnFrame.Text kind = building.drawn().stream().filter(text -> text.text().endsWith(
                ControlFlow.dropdownLabel(""))).findFirst().orElseThrow(() -> new AssertionError(building.texts()
                .toString()));
        assertTrue(building.findFirst("37").y() < kind.y(), building.drawn().toString());
        DrawnFrame.Text label = building.find("Value:");
        // Decision one-builder-frame-on-every-pane: Value: stands on the goo value line, above the builder.
        DrawnFrame.Text worthLine = building.findFirst("37");
        assertTrue(Math.abs(label.y() - worthLine.y()) <= ExpressionLines.TEXT_LINE_HEIGHT / 2
                && label.y() < kind.y(), label + " vs " + worthLine + " and " + kind);
        assertTrue(label.x() < kind.x(), label + " vs " + kind);
    }

    /**
     * A group worth 21/2 glow draws its goo value as the Items row draws an amount: small, as the mixed number 10
     * and a half, the half stacked over its bar, and never 21/2 at full size (decision
     * groups-value-draws-as-every-other-panes-value).
     */
    // A Groups pane over logs valued by the base given, oak_log resolving to the value or the error given.
    private static GroupsPane logsValued(String base, String resolved) {
        return logsValued(base, resolved, mock(ManagerActions.class));
    }

    // A Groups pane over logs valued by the base given, its line back to the screen the actions given.
    private static GroupsPane logsValued(String base, String resolved, ManagerActions actions) {
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
                  {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "%s",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(base.replace("\"", "\\\\\\\"")));
        values.readResolved("{\"entries\": [{\"key\": \"oak_log\", " + resolved + ", \"denied\": false}]}");
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "%s", "tweaks": [], "preview": []}]}
                """.formatted(base.replace("\"", "\\\"")));
        groups.selectGroup("logs");
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        return new GroupsPane(groups, values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of,
                List::of));
    }

    /**
     * In a group's read value a shift + left click on an item's name beside its sprite opens Items on the item's
     * whole id, the name and sprite being one link (decision
     * diagnose-and-correct-read-mode-item-icons-that-do-not-follow).
     */
    @Test
    void readValueItemNameFollowsOnShiftClick() {
        ManagerActions actions = mock(ManagerActions.class);
        GroupsPane pane = logsValued("stick / 2", "\"value\": {\"glow\": \"4\"}", actions);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        frame.shiftClick("stick");
        verify(actions).openItem("minecraft:stick");
    }

    /**
     * A change in the Groups builder writes the group's base into the draft and reaches draftChanged on the same call,
     * as the Items and Prefabs builders do (decision one-red-error-line-under-every-value).
     */
    @Test
    void builderChangeReachesDraftChangedOnTheSameCall() {
        ManagerDraft draft = new ManagerDraft();
        draft.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "logs", "keySeparator": null, "kind": "VALUE", "text": "[\\"oak_log\\"]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "4", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        GroupsPane pane = new GroupsPane(new GroupsTab(draft), new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        pane.writeBase("logs", "9");
        verify(actions).draftChanged();
        String base = draft.members().asList().stream().map(JsonElement::getAsJsonObject)
                .filter(member -> member.get("key").getAsString().equals("#logs")).findFirst().orElseThrow()
                .get("text").getAsString();
        assertEquals("9", base);
    }

    /**
     * A group's builder opens in the read box's place below its goo value line, short of the delete x, its panel's
     * overhang reserved, and Value: and the goo value stay above it (decision one-builder-frame-on-every-pane).
     */
    @Test
    void builderOpensInTheFrameBelowTheGooValueLine() {
        GroupsPane pane = logsValued("{\"glow\": \"4\"}", "\"value\": {\"glow\": \"4\"}");
        GroupsPane.Bounds view = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame read = new DrawnFrame();
        ScreenDraw.extract(pane, read.graphics, DrawnFrame.font(), view, read.targets);
        int[] box = read.buttonAround(read.find(ReadValueButton.PENCIL));
        DrawnFrame.Placed worth = read.placements().stream().filter(placed -> placed.scale() == Lettering.AMOUNT_SCALE)
                .findFirst().orElseThrow(() -> new AssertionError(read.placements().toString()));
        assertTrue(worth.y() < box[1], "the goo value line above the box");
        read.click(ReadValueButton.PENCIL);
        DrawnFrame open = new DrawnFrame();
        ScreenDraw.extract(pane, open.graphics, DrawnFrame.font(), view, open.targets);
        int[] panel = open.fillsOf(Palette.PANEL_FILL).getFirst();
        int[] readBox = read.fillsOf(Palette.BORDER).stream().filter(border -> border[1] == box[1]
                && border[0] < box[0]).findFirst().orElse(box);
        assertEquals(List.of(readBox[0] - RowLayout.BOX_MARGIN, box[1]), List.of(panel[0], panel[1]),
                "the builder's panel in the read box's place");
        assertTrue(open.texts().contains("Value:"), open.texts().toString());
        assertTrue(open.placements().stream().anyMatch(placed -> placed.scale() == Lettering.AMOUNT_SCALE
                && placed.y() < panel[1]), "the goo value stays above the builder");
    }

    /**
     * A group whose value fails draws one red line under its box carrying the error (decision
     * one-red-error-line-under-every-value).
     */
    @Test
    void failingGroupValueDrawsOneRedLineUnderItsBox() {
        GroupsPane pane = logsValued("$missing", "\"value\": null, \"error\": \"Unknown constant $missing\"");
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        int[] box = frame.buttonAround(frame.find("$missing"));
        List<DrawnFrame.Text> red = frame.drawn().stream().filter(text -> text.color() == Palette.DANGER
                && text.text().contains("Unknown constant")).toList();
        assertEquals(1, red.size(), frame.drawn().toString());
        assertEquals(box[3], red.getFirst().y());
    }

    @Test
    void cardValueDrawsSmallAsAMixedNumber() {
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
                  {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": \\"21/2\\"}",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_log", "value": {"glow": "21/2"}, "denied": false}]}
                """);
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "{\\"glow\\": \\"21/2\\"}", "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        assertEquals(Lettering.AMOUNT_SCALE, frame.placed("10").scale(), frame.placements().toString());
        assertTrue(frame.placements().stream().anyMatch(placed -> placed.text().equals("2")
                && placed.scale() < Lettering.AMOUNT_SCALE), "the half stacks: " + frame.placements());
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
        ScreenDraw.extract(pane, listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        DrawnFrame.Text value = listed.find("8");
        DrawnFrame.Text pencil = listed.drawn().stream().filter(text -> text.text().equals(ReadValueButton.PENCIL)
                && Math.abs(text.y() - value.y()) < ExpressionLines.TEXT_LINE_HEIGHT).findFirst()
                .orElseThrow(() -> new AssertionError(listed.drawn().toString()));
        assertTrue(pencil.x() > value.x(), pencil + " vs " + value);
        assertTrue(listed.targets.click(pencil.x() + 1, pencil.y() + 1));
        assertTrue(builder.isOpen());
        DrawnFrame building = new DrawnFrame();
        ScreenDraw.extract(pane, building.graphics, DrawnFrame.font(), bounds, building.targets);
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
        ScreenDraw.extract(pane, listed.graphics, DrawnFrame.font(), bounds, listed.targets);
        assertTrue(listed.texts().containsAll(List.of("#logs", "#saplings")), listed.texts().toString());
        // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
        assertEquals(2, listed.texts().stream().filter(ReadValueButton.NOT_SET::equals).count(),
                listed.texts().toString());
        listed.click(ReadValueButton.NOT_SET);
        assertTrue(builder.isOpen());
        assertEquals(Optional.of("saplings"), groups.selected().map(GroupsTab.Group::name));
        DrawnFrame building = new DrawnFrame();
        ScreenDraw.extract(pane, building.graphics, DrawnFrame.font(), bounds, building.targets);
        DrawnFrame.Text kind = building.find(ControlFlow.dropdownLabel("by type"));
        assertTrue(kind.x() < GroupsPane.listWidth(bounds), "the builder stands on the card: " + kind);
        // Decision add-type-is-a-big-square-plus-question-button-below-the-list: the +? stands under the kind.
        assertTrue(building.find("+?").y() > kind.y(), building.drawn().toString());
    }
    /**
     * One wheel notch over the group list moves it one wheel step (decision one-wheel-step-and-one-dialog-primitive).
     */
    @Test
    void wheelNotchMovesTheListOneWheelStep() {
        GroupsPane pane = manyGroups(new GroupsTab(new ManagerDraft()));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        pane.scroll(SHORT_VIEW.left() + 1, SHORT_VIEW.top() + 60, SHORT_VIEW.left(), -1);
        assertEquals(ScrollBar.WHEEL_STEP, pane.listBar().offset());
    }

    /**
     * A group's name centers on its list row through the one centering helper, as every list pane's does (decision
     * one-lettering-primitive-names-three-text-roles).
     */
    @Test
    void groupNameCentersOnItsRow() {
        DrawnFrame tall = new DrawnFrame();
        manyGroups(new GroupsTab(new ManagerDraft())).extract(tall.graphics, DrawnFrame.font(), TALL_VIEW,
                tall.targets);
        DrawnFrame.Text second = tall.findFirst("#" + groupName(1));
        assertEquals(Lettering.full(DrawnFrame.font()).centeredTop(tall.rowTopAbove(second),
                RowLayout.SPRITE_ROW_HEIGHT), second.y());
    }

    /**
     * The selected group's card fills in the palette's selected fill (decision fills-are-two-roles-selected-and-panel).
     */
    @Test
    void selectedCardFillsTheSelectedRole() {
        ManagerDraft draft = new ManagerDraft();
        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": null, "tweaks": [], "preview": []}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        GroupsPane pane = new GroupsPane(groups, new ValuesTab(draft), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        groups.selectGroup("logs");
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        DrawnFrame.Text members = frame.find("Members:");
        assertTrue(frame.fillsOf(Palette.SELECTED_FILL).stream().anyMatch(fill -> fill[0] <= members.x()
                && members.x() < fill[2] && fill[1] <= members.y() && members.y() < fill[3]), frame.drawn().toString());
    }

    /**
     * In a group's expression, a shift + left click on a prefab ref opens the Prefabs tab on that prefab and a plain
     * left click on the same ref opens the builder over the value (decision
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
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        frame.shiftClick("$wood");
        verify(actions).openVariable("wood");
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
        ScreenDraw.extract(pane, closed.graphics, DrawnFrame.font(), bounds, closed.targets);
        DrawnFrame.Text newGroup = closed.find("+ Custom");
        DrawnFrame.Text addTag = closed.find("+ Tag");
        assertEquals(newGroup.y(), addTag.y());
        assertTrue(addTag.x() > newGroup.x(), closed.drawn().toString());
        closed.click("+ Tag");
        DrawnFrame open = new DrawnFrame();
        ScreenDraw.extract(pane, open.graphics, DrawnFrame.font(), bounds, open.targets);
        assertTrue(open.texts().containsAll(List.of("#minecraft:leaves, 8 members", "#minecraft:logs, 40 members")),
                open.texts().toString());
    }
}
