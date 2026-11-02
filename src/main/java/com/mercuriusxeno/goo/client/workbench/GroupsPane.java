package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Optional;

/**
 * Draws the Groups tab: each group's row, its #name and its members as item icons named on hover, the rest behind an
 * ..+n button, with no header and no count (decision groups-list-members-as-icons-without-headers-or-count);
 * and the selected group's detail: New group names a group, [+] right of the group's name picks an item or #tag into
 * it and Rename beside it renames the group, the group's value is built in the expression builder under Value, and a
 * member's own line is built too, its count times the group's value plus its extra (decision
 * group-controls-name-add-and-build). The list takes 60% of the pane and the member labels span the detail (decision
 * group-list-takes-60-percent-and-labels-span-the-pane). A tag referent lists beside the custom groups, its members
 * the game's, its detail saying its membership isn't controlled here in place of [+] (decision
 * tag-referent-lists-the-games-members-and-values-them), and Add tag beside New group
 * picks a game tag to list (decision add-tag-picks-a-game-tag-and-writes-its-entry). The list and
 * the detail column each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in
 * {@link GroupsTab}.
 */
final class GroupsPane {
    // Decision group-list-takes-60-percent-and-labels-span-the-pane: the list takes three fifths of the pane, a row's
    // name two fifths of the list.
    private static final int LIST_PARTS = 3;
    private static final int NAME_PARTS = 2;
    private static final int PARTS = 5;
    private static final int GAP = 12;
    /** A group row's height, its sprites' and margin. */
    static final int ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    private static final int NAME_BOX_WIDTH = 140;
    /** A member card's height, its sprite's and margin. */
    static final int CARD_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int TEXT_NUDGE = 5;
    private static final int SECTION_GAP = 6;
    private static final int HALF = 2;
    // A hover key no item id, prefab name or goo type key spells.
    private static final String NAME_HOVER_PREFIX = "goo-group-name:";
    private static final int SCROLL_STEP = ROW_HEIGHT * 3;
    // Nine digits always fit an int.
    private static final int MAX_DIGITS = 9;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int LINK = ButtonPainter.LINK;
    private static final int SELECTED_COLOR = 0x60FFFFFF;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final String NEW_GROUP = "+ Custom";
    private static final String NEW_GROUP_HINT = "group name, Enter";
    private static final String SEARCH_HINT = "Search groups";
    // Decision group-detail-puts-plus-and-rename-beside-the-name.
    private static final String ADD_MEMBER = "+";
    private static final String MEMBERS_LABEL = "Members";
    // The operator's ruling: rename is a pencil just past the name.
    private static final String RENAME = "✎";
    private static final String RENAME_HINT = "Rename";
    private static final String DELETE = "✗";
    private static final String DELETE_HINT = "Delete";
    private static final String NOTE_GAP = "  ";
    private static final String ADD_TAG = "+ Tag";
    private static final String NOT_CONTROLLED ="Tag membership isn't controlled here";
    private static final String NO_GROUP = "Pick a group, or name a new one.";
    private static final String NO_GROUPS = "The file names no groups.";
    // Decision every-value-builder-reads-value-kind-on-one-line-defaulting-by-type.
    private static final String WORTH_LABEL = "Value";
    private static final String NO_WORTH = "empty";
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
    private final ExpandedLists expandedRows = new ExpandedLists();
    private final ScrollBar listBar = new ScrollBar(SCROLL_STEP);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private @Nullable String revealPending;
    private @Nullable RevealedRow revealedRow;
    private @Nullable EditBox nameBox;
    private @Nullable EditBox search;
    private @Nullable String building;
    private boolean naming;
    private boolean renaming;
    private boolean visible;
    private int listWidth;

    // Where the row a follow revealed stood inside the list's content this frame.
    private record RevealedRow(int top, int height) {
    }

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
        standSearch(font);
        builder.init(font);
        overlays.init(font);
        show(visible);
    }

    // A resize stands the box afresh with the text it held (decision groups-search-matches-a-name-or-any-member).
    private void standSearch(Font font) {
        String searched = search == null ? NO_TEXT : search.getValue();
        EditBox box = actions.addWidget(new EditBox(font, 0, 0, NAME_BOX_WIDTH,
                BOX_HEIGHT, Component.literal(SEARCH_HINT)));
        box.setHint(Component.literal(SEARCH_HINT));
        box.setValue(searched);
        box.setResponder(text -> {
            groups.search(text, values.entries());
            listBar.reset();
        });
        search = box;
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
        if (search != null) {
            search.visible = shown;
        }
    }

    /**
     * Reads every group of the draft afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.DESCRIBE_GROUPS, values.postedText(), groups::readDescribed);
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
            if (renaming) {
                renameGroup(box.getValue());
            } else {
                createGroup(box.getValue());
            }
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
        if (x < left + listWidth) {
            listBar.wheel(amount);
        } else {
            detailBar.wheel(amount);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        overlays.commitOnBlur();
        listWidth = listWidth(bounds);
        drawList(graphics, font, bounds, targets);
        Bounds detail = new Bounds(bounds.left() + listWidth + GAP, bounds.top(),
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
        int rowRight = left + listWidth - ScrollBar.WIDTH - ScrollBar.GAP;
        graphics.enableScissor(left, top, rowRight, bounds.bottom());
        targets.clip(left, top, rowRight, bounds.bottom());
        String selected = groups.selected().map(GroupsTab.Group::name).orElse(NO_TEXT);
        int y = top - listBar.offset();
        List<GroupsTab.GroupRow> rows = groups.groupRows();
        for (int index = 0; index < rows.size(); index++) {
            GroupsTab.GroupRow row = rows.get(index);
            RowLayout.drawSeparatorAbove(graphics, index, y, left, rowRight);
            int height = drawGroupRow(graphics, font, row, left, rowRight, y, row.name().equals(selected), targets);
            if (row.name().equals(revealPending)) {
                revealedRow = new RevealedRow(y - top + listBar.offset(), height);
            }
            y += height;
        }
        targets.unclip();
        graphics.disableScissor();
        listBar.measureAndDraw(graphics, targets, y, left + listWidth, top, bounds.bottom());
        centerRevealedRow();
    }

    // Once the revealed row has drawn and the bar has measured this frame, centers the row in the list.
    // Decision shift-follow-centers-the-target-in-its-scrolling-view.
    private void centerRevealedRow() {
        if (revealedRow != null) {
            listBar.center(revealedRow.top(), revealedRow.height());
            revealedRow = null;
            revealPending = null;
        }
    }

    // The operator's ruling: one row, the search box at the left and + Custom and + Tag to its right; a new group's
    // name box stands in the search box's place while open. Answers the list's top below the row.
    private int drawNewGroup(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        int rowRight = left + listWidth - ScrollBar.WIDTH - ScrollBar.GAP;
        int buttonsWidth = ButtonPainter.width(font, NEW_GROUP) + BuilderOverlays.GAP + ButtonPainter.width(font,
                ADD_TAG);
        int boxWidth = Math.max(1, rowRight - left - buttonsWidth - BuilderOverlays.GAP);
        boolean namingNew = naming && !renaming && nameBox != null;
        if (namingNew) {
            nameBox.setPosition(left, top + HALF);
            nameBox.setWidth(boxWidth);
            nameBox.visible = visible;
        }
        if (search != null) {
            search.setPosition(left, top + HALF);
            search.setWidth(boxWidth);
            search.visible = visible && !namingNew;
        }
        int afterNewGroup = BuilderOverlays.control(graphics, font, NEW_GROUP, left + boxWidth + BuilderOverlays.GAP,
                top, targets, at -> startNaming());
        // decision add-tag-picks-a-game-tag-and-writes-its-entry
        BuilderOverlays.control(graphics, font, ADD_TAG, afterNewGroup, top, targets, at -> overlays.openDropdown(
                at, groups.addTagChoices(values.entries()), choice -> createTag(choice.id())));
        return top + ROW_HEIGHT + HALF;
    }

    private void createTag(String tagId) {
        apply(groups.createTagRequest(tagId));
        closeBuilder();
        groups.selectGroup(GroupsTab.referentName(tagId));
    }

    // A row draws #name and its members' icons, named on hover; answers its height, more than a line once its
    // ..+n button expanded it (decision groups-list-members-as-icons-without-headers-or-count).
    private int drawGroupRow(GuiGraphicsExtractor graphics, Font font, GroupsTab.GroupRow row, int left, int right,
                             int y, boolean selected, ClickTargets targets) {
        List<ExpressionLines.Run> runs = GroupsTab.rowRuns(row, icons::nameOf);
        List<ExpressionLines.Run> members = runs.subList(1, runs.size());
        int itemsX = left + nameWidth();
        int step = ICON_SIZE + ICON_GAP;
        int perRow = Math.max(1, (right - itemsX) / step);
        boolean expanded = expandedRows.expanded(row.name());
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(members.size())
                : UsedByLine.fit(members.size(), right - itemsX, step,
                        hidden -> ButtonPainter.width(font, UsedByLine.moreLabel(hidden)));
        UsedByLine.Slot end = UsedByLine.endSlot(fit, expanded, perRow, step, right - itemsX,
                ButtonPainter.width(font, UsedByLine.HIDE));
        int height = (end.row() + 1) * ROW_HEIGHT;
        drawRowName(graphics, font, row, runs.getFirst().text(), new Bounds(left, y, right, y + height), selected,
                targets);
        drawMemberIcons(graphics, members.subList(0, fit.shown()), itemsX, y, expanded ? perRow : fit.shown(),
                targets);
        UsedByLine.endLabel(fit, expanded).ifPresent(label -> ButtonPainter.draw(graphics, font, label,
                itemsX + end.x(), y + end.row() * ROW_HEIGHT, ROW_HEIGHT, targets,
                () -> toggle(row.name(), expanded)));
        return height;
    }

    // The row's #name, red where it names an unknown member, and the click that selects the group.
    private void drawRowName(GuiGraphicsExtractor graphics, Font font, GroupsTab.GroupRow row, String name,
                             Bounds area, boolean selected, ClickTargets targets) {
        if (selected) {
            graphics.fill(area.left(), area.top(), area.right(), area.bottom(), SELECTED_COLOR);
        }
        targets.add(area.left(), area.top(), area.right() - area.left(), area.bottom() - area.top(),
                () -> pickGroup(row.name()));
        int textTop = area.top() + TEXT_NUDGE;
        int nameTextWidth = nameWidth() - HALF;
        graphics.text(font, font.plainSubstrByWidth(name, nameTextWidth), area.left(), textTop,
                row.unknownCount() == 0 ? WHITE : RED);
        // decision hovering-a-truncated-group-name-shows-the-full-name
        nameHoverKey(font, name, nameTextWidth).ifPresent(key -> targets.addHover(area.left(), textTop,
                nameTextWidth, font.lineHeight, key));
    }

    private int nameWidth() {
        return listWidth * NAME_PARTS / PARTS;
    }

    /**
     * @param pane the Groups pane's bounds
     * @return the list column's width, three fifths of the pane (decision
     *     group-list-takes-60-percent-and-labels-span-the-pane)
     */
    static int listWidth(Bounds pane) {
        return (pane.right() - pane.left()) * LIST_PARTS / PARTS;
    }

    /**
     * @param font  the font the name draws in
     * @param name  a row's #name as drawn
     * @param width the name column's text width
     * @return the hover key whose tooltip reads the whole name where the name column truncates it, empty where it fits
     */
    static Optional<String> nameHoverKey(Font font, String name, int width) {
        return font.width(name) > width ? Optional.of(NAME_HOVER_PREFIX + name) : Optional.empty();
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the whole group name where the area is a truncated name's, empty otherwise
     */
    static Optional<TooltipLine> nameTooltipFor(String hoverKey) {
        return hoverKey.startsWith(NAME_HOVER_PREFIX)
                ? Optional.of(new TooltipLine.Text(Component.literal(hoverKey.substring(NAME_HOVER_PREFIX.length()))))
                : Optional.empty();
    }

    // Decision expanded-used-by-lists-take-a-hide-button: ..+n expands the row, Hide collapses it again.
    private void toggle(String name, boolean expanded) {
        if (expanded) {
            expandedRows.hide(name);
        } else {
            expandedRows.expand(name);
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
            ItemLinks.add(targets, actions, id, iconX, iconY);
        }
    }

    ScrollBar listBar() {
        return listBar;
    }

    /**
     * @return whether the member picker or another of the pane's lists stands open
     */
    boolean pickerOpen() {
        return overlays.listOpen();
    }

    /**
     * Selects a group, closing a builder open over another, as a grouped item's group link does, and centers its row
     * in the list on the next frame (decisions items-row-second-line-is-the-equality-expression,
     * shift-follow-centers-the-target-in-its-scrolling-view).
     *
     * @param name the group's name
     */
    void reveal(String name) {
        pickGroup(name);
        revealPending = name;
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
        drawDetailHead(graphics, font, detail, group, y, targets);
        y += ROW_HEIGHT;
        // decision tag-referent-lists-the-games-members-and-values-them
        if (group.tag()) {
            graphics.text(font, NOT_CONTROLLED, x, y + TEXT_NUDGE, GRAY);
            y += ROW_HEIGHT;
        }
        y += SECTION_GAP;
        y = drawWorth(graphics, font, detail, group, y, targets);
        y = drawAddMember(graphics, font, detail, group, y + SECTION_GAP, targets);
        y = drawCards(graphics, font, detail, y, targets);
        if (!group.tag()) {
            y = drawMemberLine(graphics, font, detail, group, y + SECTION_GAP, targets);
        }
        targets.unclip();
        graphics.disableScissor();
        detailBar.measureAndDraw(graphics, targets, y, detail.right() + ScrollBar.GAP + ScrollBar.WIDTH, detail.top(),
                detail.bottom());
    }

    // The group's #name cut to leave room for the pencil just past it, a custom group's alone taking one, and the red
    // x at the head's right end; the rename box stands in the name's place while open (decisions
    // group-detail-puts-plus-and-rename-beside-the-name, rename-is-a-pencil-and-delete-a-red-x-everywhere).
    private void drawDetailHead(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group,
                                int y, ClickTargets targets) {
        String name = GroupsTab.rowRuns(new GroupsTab.GroupRow(group.name(), List.of(), 0), icons::nameOf).getFirst()
                .text();
        int deleteWidth = ButtonPainter.width(font, DELETE);
        drawRemoval(graphics, font, detail.right(), y, group.name(), targets);
        int buttons = (group.tag() ? 0 : ButtonPainter.width(font, RENAME) + BuilderOverlays.GAP) + deleteWidth
                + BuilderOverlays.GAP;
        int nameWidth = detail.right() - detail.left() - buttons;
        int x = detail.left();
        if (renaming && nameBox != null) {
            nameBox.setPosition(x, y + HALF);
            nameBox.setWidth(NAME_BOX_WIDTH);
            nameBox.visible = visible;
            return;
        }
        ButtonPainter.drawEntryName(graphics, font, font.plainSubstrByWidth(name, nameWidth), x, y + TEXT_NUDGE,
                groups.removals().isMarked(RemovalMarks.Tab.GROUPS, group.name()));
        if (!group.tag()) {
            BuilderOverlays.control(graphics, font, RENAME, RENAME_HINT, x + Math.min(nameWidth, font.width(name))
                    + BuilderOverlays.GAP, y, targets, at -> startRenaming(name));
        }
    }

    // The operator's ruling: the [+] adding a member stands right above the member list, below the value, so its
    // place says what it adds to; Members heads the list left of it, alone for a tag, which takes no [+]. Answers
    // the y below the row.
    // Decision members-labels-the-group-member-list.
    private int drawAddMember(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group, int top,
                              ClickTargets targets) {
        graphics.text(font, MEMBERS_LABEL, detail.left(), top + TEXT_NUDGE, GRAY);
        if (!group.tag()) {
            BuilderOverlays.control(graphics, font, ADD_MEMBER, null,
                    detail.left() + font.width(MEMBERS_LABEL) + BuilderOverlays.GAP, top, targets,
                    at -> overlays.openDropdown(at, SearchDropdown.overMembers(values.entries()),
                            choice -> apply(groups.addPickRequest(choice.id()))));
        }
        return top + ROW_HEIGHT;
    }

    // The group's value, curated, opening the builder on click; answers the y below it.
    private int drawWorth(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group, int top,
                          ClickTargets targets) {
        String target = BASE_TARGET + group.name();
        // The open builder's own line reads Value and its kind, so the label stands once.
        int valueTop = target.equals(building) && builder.isOpen() ? top : top + LINE_HEIGHT;
        if (valueTop > top) {
            graphics.text(font, WORTH_LABEL, detail.left(), top, GRAY);
        }
        String base = group.base() == null ? NO_TEXT : group.base();
        return valueTop + drawBuilt(graphics, font, detail, valueTop, target, base, NO_WORTH,
                typed -> apply(groups.baseRequest(typed)), targets);
    }

    /**
     * A built value's change.
     */
    @FunctionalInterface
    private interface Writes {
        void wrote(String typed);
    }

    // A value drawn curated in a read view, a plain left click opening the builder over it in its place and a
    // shift + left click on a ref opening its prefab; answers its height.
    // Decision shift-left-click-follows-plain-left-modifies.
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
                targets, WHITE, LINK, ExpressionLines.followedOnShiftClick(targets, actions::openVariable)));
        return height;
    }

    // Answers the y below the cards; a card cut by the pane's edge is not drawn, so no icon shows half.
    private int drawCards(GuiGraphicsExtractor graphics, Font font, Bounds detail, int top, ClickTargets targets) {
        List<GroupsTab.MemberRow> members = groups.memberRows(values.entries());
        String selected = groups.selectedMember().orElse(NO_TEXT);
        for (int index = 0; index < members.size(); index++) {
            int cardY = top + index * CARD_HEIGHT;
            if (cardY < detail.top() || cardY + CARD_HEIGHT > detail.bottom()) {
                continue;
            }
            RowLayout.drawSeparatorAbove(graphics, index, cardY, detail.left(), detail.right());
            drawCard(graphics, font, members.get(index), new Bounds(detail.left(), cardY, detail.right(),
                    cardY + CARD_HEIGHT), selected, targets);
        }
        return top + members.size() * CARD_HEIGHT;
    }

    // Decision group-list-takes-60-percent-and-labels-span-the-pane: a member's sprite, then its name and note on one
    // line reaching the detail's right edge, the text centered on the 18px row.
    private void drawCard(GuiGraphicsExtractor graphics, Font font, GroupsTab.MemberRow member, Bounds card,
                          String selected, ClickTargets targets) {
        if (member.id().equals(selected)) {
            graphics.fill(card.left(), card.top(), card.right(), card.bottom(), SELECTED_COLOR);
        }
        graphics.item(icons.of(member.id()), card.left(), card.top() + 1);
        int textX = card.left() + ICON_SIZE + ICON_GAP;
        int textTop = ExpressionLines.centeredTextTop(card.top(), CARD_HEIGHT, font.lineHeight);
        String name = font.plainSubstrByWidth(member.name(), card.right() - textX);
        graphics.text(font, name, textX, textTop, member.known() ? WHITE : RED);
        int noteX = textX + font.width(name + NOTE_GAP);
        if (!member.note().isEmpty() && noteX < card.right()) {
            graphics.text(font, font.plainSubstrByWidth(member.note(), card.right() - noteX), noteX, textTop, GRAY);
        }
        targets.add(card.left(), card.top(), card.right() - card.left(), CARD_HEIGHT, () -> pickMember(member.id()));
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

    private void startRenaming(String name) {
        startNaming();
        renaming = true;
        if (nameBox != null) {
            nameBox.setValue(name);
        }
    }

    // The red x at the head's right end marks the group; a marked group's gold arrow in its place restores it.
    // Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
    private void drawRemoval(GuiGraphicsExtractor graphics, Font font, int right, int y, String name,
                             ClickTargets targets) {
        if (groups.removals().isMarked(RemovalMarks.Tab.GROUPS, name)) {
            ButtonPainter.drawRestore(graphics, font, right, y, ROW_HEIGHT, targets,
                    () -> groups.removals().restore(RemovalMarks.Tab.GROUPS, name));
            return;
        }
        ButtonPainter.drawColored(graphics, font, DELETE, RED, right - ButtonPainter.width(font, DELETE), y,
                ROW_HEIGHT, targets, () -> markGroup(name), BuilderOverlays.hintKey(DELETE_HINT));
    }

    /**
     * Deletes every marked group from the draft, as the save does before it writes the draft.
     * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
     */
    void applyRemovals() {
        for (String name : groups.removals().marked(RemovalMarks.Tab.GROUPS)) {
            if (groups.deleteGroup(name)) {
                detailBar.reset();
            }
            groups.removals().restore(RemovalMarks.Tab.GROUPS, name);
        }
    }

    private void markGroup(String name) {
        closeBuilder();
        stopNaming();
        groups.removals().mark(RemovalMarks.Tab.GROUPS, name);
        actions.markUnsaved();
    }

    private void renameGroup(String typed) {
        stopNaming();
        if (groups.renameSelected(typed)) {
            closeBuilder();
            actions.draftChanged();
        }
    }

    private void startNaming() {
        renaming = false;
        naming = true;
        if (nameBox != null) {
            nameBox.setValue(NO_TEXT);
            nameBox.visible = visible;
            actions.focus(nameBox);
        }
    }

    private void stopNaming() {
        naming = false;
        renaming = false;
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
