package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the Groups tab: each group's row, its #name and its members as item icons named on hover, the rest behind an
 * "and N more" link, with no header and no count (decision groups-list-members-as-icons-without-headers-or-count);
 * and the selected group's detail: New group names a group, Add member picks an item or #tag into it, the
 * group's value is built in the expression builder as what every member is worth, and a member's own line is built
 * too, its count times the group's value plus its extra (decision group-controls-name-add-and-build). The list and
 * the detail column each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in
 * {@link GroupsTab}.
 */
final class GroupsPane {
    private static final int LIST_WIDTH = 220;
    private static final int NAME_WIDTH = 80;
    private static final int GAP = 12;
    private static final int ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    private static final int NAME_BOX_WIDTH = 140;
    private static final int CARD_WIDTH = 130;
    private static final int CARD_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int TEXT_NUDGE = 5;
    private static final int SECTION_GAP = 6;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = ROW_HEIGHT * 3;
    // Nine digits always fit an int.
    private static final int MAX_DIGITS = 9;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int LINK = ButtonPainter.LINK;
    private static final int BUTTON_PADDING = ButtonPainter.PADDING + ButtonPainter.PADDING;
    private static final int SELECTED_COLOR = 0x60FFFFFF;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final String NEW_GROUP = "New group";
    private static final String NEW_GROUP_HINT = "group name, Enter";
    private static final String ADD_MEMBER = "Add member";
    private static final String NO_GROUP = "Pick a group, or name a new one.";
    private static final String NO_GROUPS = "The file names no groups.";
    private static final String WORTH_LABEL = "What every member is worth:";
    private static final String NO_WORTH = "nothing yet";
    private static final String LINE_LABEL = "This member's line: count times the group's value, plus its extra";
    private static final String COUNT_START = "count ";
    private static final String EXTRA_LABEL = "extra:";
    private static final String NO_EXTRA = "none";
    private static final String NEEDS_WORTH = "Build the group's value first; a member's line adds to it.";
    private static final String REMOVE = "Remove from group";
    private static final String BASE_TARGET = "base:";
    private static final String EXTRA_TARGET = "extra:";
    private static final String NO_TEXT = "";

    private final GroupsTab groups;
    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private final ExpressionBuilderPane builder;
    private final BuilderOverlays overlays;
    private final Set<String> expandedRows = new HashSet<>();
    private final ScrollBar listBar = new ScrollBar(SCROLL_STEP);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private @Nullable EditBox nameBox;
    private @Nullable String building;
    private boolean naming;
    private boolean visible;

    GroupsPane(GroupsTab groups, ValuesTab values, ItemIcons icons, ManagerActions actions,
               ExpressionBuilderPane builder) {
        this.groups = groups;
        this.values = values;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
        this.overlays = new BuilderOverlays(actions, icons);
    }

    /**
     * Stands the pane's name box, the builder's boxes and the controls' boxes among the screen's widgets.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        nameBox = actions.addWidget(new EditBox(font, 0, 0, NAME_BOX_WIDTH, BOX_HEIGHT,
                Component.literal(NEW_GROUP_HINT)));
        nameBox.setHint(Component.literal(NEW_GROUP_HINT));
        builder.init(font);
        overlays.init(font);
        show(visible);
    }

    /**
     * @param shown whether the Groups tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        builder.show(shown);
        overlays.show(shown);
        if (nameBox != null) {
            nameBox.visible = shown && naming;
        }
    }

    /**
     * Reads every group of the draft afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.DESCRIBE_GROUPS, values.modelText(), groups::readDescribed);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the pane took the key: Enter on the name box creates the group, Escape drops it
     */
    boolean keyPressed(int key) {
        if (nameBox != null && naming && nameBox.isFocused()) {
            return nameKey(nameBox, key);
        }
        return builder.keyPressed(key) || overlays.keyPressed(key);
    }

    private boolean nameKey(EditBox box, int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            createGroup(box.getValue());
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            stopNaming();
            return true;
        }
        return false;
    }

    /**
     * @param x      the cursor's x
     * @param y      the cursor's y
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, double y, int left, double amount) {
        if (builder.scrollList(amount) || overlays.scrollList(amount)) {
            return;
        }
        if (x < left + LIST_WIDTH) {
            listBar.wheel(amount);
        } else {
            detailBar.wheel(amount);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        overlays.commitOnBlur();
        drawList(graphics, font, bounds, targets);
        Bounds detail = new Bounds(bounds.left() + LIST_WIDTH + GAP, bounds.top(),
                bounds.right() - ScrollBar.WIDTH - ScrollBar.GAP, bounds.bottom());
        Optional<GroupsTab.Group> selected = groups.selected();
        if (selected.isEmpty()) {
            graphics.text(font, groups.groupRows().isEmpty() ? NO_GROUPS : NO_GROUP, detail.left(), detail.top(),
                    GRAY);
        } else {
            drawDetail(graphics, font, detail, selected.get(), targets);
        }
        builder.drawOverlays(graphics, font, targets);
        overlays.draw(graphics, font, targets);
    }

    private void drawList(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        int left = bounds.left();
        int top = drawNewGroup(graphics, font, left, bounds.top(), targets);
        int rowRight = left + LIST_WIDTH - ScrollBar.WIDTH - ScrollBar.GAP;
        graphics.enableScissor(left, top, rowRight, bounds.bottom());
        targets.clip(left, top, rowRight, bounds.bottom());
        String selected = groups.selected().map(GroupsTab.Group::name).orElse(NO_TEXT);
        int y = top - listBar.offset();
        for (GroupsTab.GroupRow row : groups.groupRows()) {
            int height = drawGroupRow(graphics, font, row, left, rowRight, y, row.name().equals(selected), targets);
            y += height;
        }
        targets.unclip();
        graphics.disableScissor();
        listBar.measureAndDraw(graphics, targets, y, left + LIST_WIDTH, top, bounds.bottom());
    }

    // Answers the list's top below the New group button or its name box.
    private int drawNewGroup(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        if (naming && nameBox != null) {
            nameBox.setPosition(left, top + HALF);
            nameBox.visible = visible;
        } else {
            BuilderOverlays.control(graphics, font, NEW_GROUP, left, top, targets, at -> startNaming());
        }
        return top + ROW_HEIGHT + HALF;
    }

    // A row draws #name and its members' icons, named on hover; answers its height, more than a line once its
    // "and N more" link expanded it (decision groups-list-members-as-icons-without-headers-or-count).
    private int drawGroupRow(GuiGraphicsExtractor graphics, Font font, GroupsTab.GroupRow row, int left, int right,
                             int y, boolean selected, ClickTargets targets) {
        List<ExpressionLines.Run> runs = GroupsTab.rowRuns(row, icons::nameOf);
        List<ExpressionLines.Run> members = runs.subList(1, runs.size());
        int itemsX = left + NAME_WIDTH;
        int step = ICON_SIZE + ICON_GAP;
        int perRow = Math.max(1, (right - itemsX) / step);
        boolean expanded = expandedRows.contains(row.name());
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(members.size())
                : UsedByLine.fit(members.size(), right - itemsX, step, hidden -> font.width(UsedByLine.moreLabel(hidden)) + BUTTON_PADDING);
        int lines = expanded ? Math.max(1, (fit.shown() + perRow - 1) / perRow) : 1;
        int height = lines * ROW_HEIGHT;
        if (selected) {
            graphics.fill(left, y, right, y + height, SELECTED_COLOR);
        }
        targets.add(left, y, right - left, height, () -> pickGroup(row.name()));
        graphics.text(font, font.plainSubstrByWidth(runs.getFirst().text(), NAME_WIDTH - HALF), left, y + TEXT_NUDGE,
                row.unknownCount() == 0 ? WHITE : RED);
        drawMemberIcons(graphics, members.subList(0, fit.shown()), itemsX, y, expanded ? perRow : fit.shown(),
                targets);
        drawMore(graphics, font, row.name(), fit, itemsX + fit.shown() * step, y, targets);
        return height;
    }

    // The "and N more" button a collapsed row stops at, which expands the row onto as many lines as it needs.
    private void drawMore(GuiGraphicsExtractor graphics, Font font, String name, UsedByLine.Fit fit, int x, int y,
                          ClickTargets targets) {
        if (fit.hidden() > 0) {
            String more = UsedByLine.moreLabel(fit.hidden());
            ButtonPainter.draw(graphics, font, more, x, y, ROW_HEIGHT, targets, () -> expandedRows.add(name));
        }
    }

    // Each member's icon, named on hover, perRow to a line.
    private void drawMemberIcons(GuiGraphicsExtractor graphics, List<ExpressionLines.Run> members, int left, int top,
                                 int perRow, ClickTargets targets) {
        int step = ICON_SIZE + ICON_GAP;
        for (int index = 0; index < members.size(); index++) {
            String id = String.valueOf(members.get(index).ref());
            int iconX = left + index % Math.max(1, perRow) * step;
            int iconY = top + index / Math.max(1, perRow) * ROW_HEIGHT + 1;
            graphics.item(icons.of(id), iconX, iconY);
            targets.addHover(iconX, iconY, ICON_SIZE, ICON_SIZE, ItemIcons.hoverKey(id));
        }
    }

    private void pickGroup(String name) {
        if (!name.equals(groups.selected().map(GroupsTab.Group::name).orElse(null))) {
            closeBuilder();
            groups.selectGroup(name);
            detailBar.reset();
        }
    }

    private void drawDetail(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group,
                            ClickTargets targets) {
        graphics.enableScissor(detail.left(), detail.top(), detail.right(), detail.bottom());
        targets.clip(detail.left(), detail.top(), detail.right(), detail.bottom());
        int x = detail.left();
        int y = detail.top() - detailBar.offset();
        graphics.text(font, GroupsTab.rowRuns(new GroupsTab.GroupRow(group.name(), List.of(), 0), icons::nameOf)
                .getFirst().text(), x, y + TEXT_NUDGE, WHITE);
        y += ROW_HEIGHT;
        BuilderOverlays.control(graphics, font, ADD_MEMBER, x, y, targets, at -> overlays.openDropdown(at,
                SearchDropdown.overMembers(values.entries()), choice -> apply(groups.addPickRequest(choice.id()))));
        y += ROW_HEIGHT + SECTION_GAP;
        y = drawWorth(graphics, font, detail, group, y, targets);
        y = drawCards(graphics, font, detail, y + SECTION_GAP, targets);
        y = drawMemberLine(graphics, font, detail, group, y + SECTION_GAP, targets);
        targets.unclip();
        graphics.disableScissor();
        detailBar.measureAndDraw(graphics, targets, y, detail.right() + ScrollBar.GAP + ScrollBar.WIDTH, detail.top(),
                detail.bottom());
    }

    // The group's value, curated, opening the builder on click; answers the y below it.
    private int drawWorth(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group, int top,
                          ClickTargets targets) {
        graphics.text(font, WORTH_LABEL, detail.left(), top, GRAY);
        String base = group.base() == null ? NO_TEXT : group.base();
        return top + LINE_HEIGHT + drawBuilt(graphics, font, detail, top + LINE_HEIGHT, BASE_TARGET + group.name(),
                base, NO_WORTH, typed -> apply(groups.baseRequest(typed)), targets);
    }

    /**
     * A built value's change.
     */
    @FunctionalInterface
    private interface Writes {
        void wrote(String typed);
    }

    // A value drawn curated in a read view, a click opening the builder over it in its place; answers its height.
    private int drawBuilt(GuiGraphicsExtractor graphics, Font font, Bounds detail, int y, String target, String shown,
                          String empty, Writes writes, ClickTargets targets) {
        if (target.equals(building) && builder.isOpen()) {
            return builder.extract(graphics, font, detail, y, targets);
        }
        int width = detail.right() - detail.left();
        List<ExpressionLines.Run> runs = shown.isBlank() ? ExpressionLines.textRuns(empty)
                : ExpressionLines.shownRuns(shown, icons::nameOf);
        int height = ExpressionLines.height(font, runs, width);
        graphics.fill(detail.left(), y, detail.right(), y + height, READ_VIEW_COLOR);
        targets.add(detail.left(), y, width, height, () -> {
            building = target;
            builder.open(shown, writes::wrote, this::closeBuilder);
        });
        ExpressionLines.draw(font, runs, detail.left(), y, width, new ExpressionLines.Painter(graphics, icons,
                targets, WHITE, LINK, (name, linkX, linkY, linkWidth) -> { }));
        return height;
    }

    // Answers the y below the cards; a card cut by the pane's edge is not drawn, so no icon shows half.
    private int drawCards(GuiGraphicsExtractor graphics, Font font, Bounds detail, int top, ClickTargets targets) {
        List<GroupsTab.MemberRow> members = groups.memberRows(values.entries());
        int columns = Math.max(1, (detail.right() - detail.left()) / CARD_WIDTH);
        String selected = groups.selectedMember().orElse(NO_TEXT);
        int textWidth = CARD_WIDTH - ICON_SIZE - ICON_GAP - HALF;
        for (int index = 0; index < members.size(); index++) {
            int cardX = detail.left() + index % columns * CARD_WIDTH;
            int cardY = top + index / columns * CARD_HEIGHT;
            if (cardY < detail.top() || cardY + CARD_HEIGHT > detail.bottom()) {
                continue;
            }
            GroupsTab.MemberRow member = members.get(index);
            if (member.id().equals(selected)) {
                graphics.fill(cardX, cardY, cardX + CARD_WIDTH - HALF, cardY + CARD_HEIGHT, SELECTED_COLOR);
            }
            graphics.item(icons.of(member.id()), cardX, cardY + HALF);
            int textX = cardX + ICON_SIZE + ICON_GAP;
            graphics.text(font, font.plainSubstrByWidth(member.name(), textWidth), textX, cardY + 1,
                    member.known() ? WHITE : RED);
            graphics.text(font, font.plainSubstrByWidth(member.note(), textWidth), textX, cardY + LINE_HEIGHT,
                    GRAY);
            targets.add(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, () -> pickMember(member.id()));
        }
        int rows = (members.size() + columns - 1) / columns;
        return top + rows * CARD_HEIGHT;
    }

    private void pickMember(String id) {
        closeBuilder();
        groups.selectMember(id);
    }

    // The selected member's line: its count and its built extra; answers the y below it.
    private int drawMemberLine(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group,
                               int top, ClickTargets targets) {
        Optional<String> member = groups.selectedMember();
        if (member.isEmpty()) {
            return top;
        }
        int x = detail.left();
        if (group.base() == null) {
            graphics.text(font, NEEDS_WORTH, x, top, GRAY);
            return top + LINE_HEIGHT;
        }
        String id = member.get();
        GroupsTab.MemberLine line = groups.memberLine(id);
        graphics.text(font, LINE_LABEL, x, top, GRAY);
        int y = top + LINE_HEIGHT;
        int afterCount = BuilderOverlays.control(graphics, font, COUNT_START + line.count(), x, y, targets,
                at -> overlays.openNumber(at, String.valueOf(line.count()), typed -> countOf(typed).ifPresent(
                        count -> apply(groups.tweakRequest(id, count, line.trait(), line.extra())))));
        BuilderOverlays.control(graphics, font, REMOVE, afterCount, y, targets, at -> removeMember(id));
        y += ROW_HEIGHT;
        graphics.text(font, EXTRA_LABEL, x, y, GRAY);
        y += LINE_HEIGHT;
        return y + drawBuilt(graphics, font, detail, y, EXTRA_TARGET + id, line.extra(), NO_EXTRA,
                typed -> apply(groups.tweakRequest(id, groups.memberLine(id).count(), line.trait(), typed)), targets);
    }

    // A count is a whole number of at least 1.
    private static Optional<Integer> countOf(String typed) {
        String trimmed = typed.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit) || trimmed.length() > MAX_DIGITS) {
            return Optional.empty();
        }
        int count = Integer.parseInt(trimmed);
        return count < 1 ? Optional.empty() : Optional.of(count);
    }

    private void startNaming() {
        naming = true;
        if (nameBox != null) {
            nameBox.setValue(NO_TEXT);
            nameBox.visible = visible;
            actions.focus(nameBox);
        }
    }

    private void stopNaming() {
        naming = false;
        if (nameBox != null) {
            nameBox.visible = false;
        }
    }

    private void createGroup(String typed) {
        String name = typed.trim();
        stopNaming();
        if (name.isEmpty()) {
            return;
        }
        apply(groups.createRequest(name));
        closeBuilder();
        groups.selectGroup(name);
    }

    private void removeMember(String member) {
        closeBuilder();
        apply(groups.removeMemberRequest(member));
        groups.selectMember(null);
    }

    private void closeBuilder() {
        building = null;
        builder.close();
    }

    private void apply(String request) {
        actions.request(WorkbenchOperations.APPLY_TO_GROUP, request, applied -> {
            groups.readApplied(applied);
            actions.draftChanged();
        });
    }

    /**
     * A pane's drawing area.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param right  its right edge
     * @param bottom its bottom edge
     */
    record Bounds(int left, int top, int right, int bottom) {
        /**
         * @param x a point's x
         * @param y a point's y
         * @return whether the point stands inside the area
         */
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
