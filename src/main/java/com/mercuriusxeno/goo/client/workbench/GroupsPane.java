package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the Groups tab: each group's card, its #name with its pencil or "tag" and its x, its members as item icons
 * named on hover, the rest behind an ..+n button, and its value built in place in the expression builder
 * (decisions groups-list-members-as-icons-without-headers-or-count, group-card-is-name-then-members-then-value);
 * and the selected group's detail, membership alone: Members with the [+] that picks an item or #tag into a custom
 * group, or Tag members over the tag's id, then the member rows, a member's own line built too, its count times the
 * group's value plus its extra (decisions group-controls-name-add-and-build,
 * detail-is-members-plus-or-tag-members-with-the-tag-id). The list takes 60% of the pane and the member labels span
 * the detail (decision group-list-takes-60-percent-and-labels-span-the-pane). A tag referent lists beside the custom
 * groups, its members the game's (decision tag-referent-lists-the-games-members-and-values-them), and Add tag beside
 * New group
 * picks a game tag to list (decision add-tag-picks-a-game-tag-and-writes-its-entry). The list and
 * the detail column each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in
 * {@link GroupsTab}.
 */
final class GroupsPane {
    // Decision group-list-takes-60-percent-and-labels-span-the-pane: the list takes three fifths of the pane.
    // Decision member-list-takes-30-percent-with-no-indent, as the operator restated it: the group list takes 65
    // percent and the members panel 35.
    private static final int LIST_PARTS = 13;
    private static final int PARTS = 20;
    /** The room between the group list and the member detail. */
    static final int GAP = 12;
    /** A group row's height, its sprites' and margin. */
    static final int ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
    private static final int BOX_HEIGHT = 14;
    private static final int NAME_BOX_WIDTH = 140;
    /**
     * A member card's height: its sprite's row, then the padding that keeps the next card's rule off the sprite
     * (decision rule-takes-padding-above-it).
     */
    static final int CARD_HEIGHT = RowLayout.SPRITE_ROW_PITCH;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int HALF = 2;
    // A hover key no item id, prefab name or goo type key spells.
    // Nine digits always fit an int.
    private static final int MAX_DIGITS = 9;
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
    // Decision group-card-is-name-then-members-then-value.
    private static final String TAG_WORD = "tag";
    private static final String MEMBERS_COLON = "Members:";
    private static final String USED_BY = "Used by ";
    private static final String VALUE_LABEL = "Value:";
    // The draft's line naming a group's base reads its name after #.
    private static final String GROUP_PREFIX = "#";
    // A key suffix no group name spells, so a card's Used by line expands apart from its Members line.
    private static final String REFERENTS_KEY = ":usedBy";
    // Decision detail-is-members-plus-or-tag-members-with-the-tag-id.
    private static final String TAG_MEMBERS_LABEL = "Tag members";
    private static final String NO_GROUP = "Pick a group, or name a new one.";
    private static final String NO_GROUPS = "The file names no groups.";
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
    private final MemberRails memberRails;
    private final ExpandedLists expandedRows = new ExpandedLists();
    private final ScrollBar listBar = new ScrollBar(ScrollBar.WHEEL_STEP);
    private final ScrollBar detailBar = new ScrollBar(ScrollBar.WHEEL_STEP);
    private final Reveal revealing = new Reveal();
    private final RevealGlow glow = new RevealGlow();
    private @Nullable EditBox nameBox;
    private @Nullable EditBox search;
    private @Nullable String building;
    private boolean naming;
    private boolean renaming;
    private boolean visible;
    private int listWidth;

    GroupsPane(GroupsTab groups, ValuesTab values, ItemIcons icons, ManagerActions actions,
               ExpressionBuilderPane builder) {
        this.groups = groups;
        this.values = values;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
        this.overlays = new BuilderOverlays(actions, icons);
        this.memberRails = new MemberRails((from, to) -> apply(groups.moveMemberRequest(from, to)));
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
            commitName(box.getValue());
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
                    Palette.MUTED);
        } else {
            drawDetail(graphics, font, detail, selected.get(), targets);
        }
        builder.drawOverlays(graphics, font, targets);
        overlays.draw(graphics, font, targets);
    }

    // Decision one-reveal-primitive-centers-before-the-first-draw: a followed row stands centered and glowing from the
    // first frame drawn.
    private void drawList(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        revealing.centerBeforeDraw(graphics, scratch -> drawListRows(graphics, font, bounds, scratch),
                column -> listBar, glow);
        drawListRows(graphics, font, bounds, targets);
    }

    private void drawListRows(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
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
            int height = drawGroupCard(graphics, font, new GroupCard(row, groups.group(row.name()),
                    new Bounds(left, y, rowRight, bounds.bottom()), row.name().equals(selected)), targets);
            revealing.laid(row.name(), y - top + listBar.offset(), height, 0);
            y += height;
        }
        glow.draw(graphics, left, top - listBar.offset(), rowRight);
        targets.unclip();
        graphics.disableScissor();
        listBar.measureAndDraw(graphics, targets, y, left + listWidth, top, bounds.bottom());
    }

    // The operator's ruling: one row, the search box at the left and + Custom and + Tag to its right; a new group's
    // name box stands beside the search box in their place while open, as on Prefabs. Answers the list's top below the
    // row. Decision one-search-row-per-list-with-its-create-control-beside-it.
    private int drawNewGroup(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        int rowRight = left + listWidth - ScrollBar.WIDTH - ScrollBar.GAP;
        boolean namingNew = naming && !renaming && nameBox != null;
        int controls = namingNew ? NAME_BOX_WIDTH + NameBoxButtons.width(font)
                : ButtonPainter.width(font, NEW_GROUP) + SearchRow.GAP + ButtonPainter.width(font, ADD_TAG);
        SearchRow.Laid laid = SearchRow.lay(left, top, rowRight, controls);
        SearchRow.place(search, laid);
        if (search != null) {
            search.visible = visible;
        }
        if (namingNew) {
            placeNameBox(graphics, font, nameBox, laid.controlsLeft(), top, NAME_BOX_WIDTH, targets);
            return laid.listTop();
        }
        ButtonPainter.Box custom = ButtonPainter.draw(graphics, font, NEW_GROUP, laid.controlsLeft(), top,
                SearchRow.HEIGHT, targets, this::startNaming, null);
        int tagX = custom.right() + SearchRow.GAP;
        // decision add-tag-picks-a-game-tag-and-writes-its-entry
        ButtonPainter.draw(graphics, font, ADD_TAG, tagX, top, SearchRow.HEIGHT, targets, () -> overlays.openDropdown(
                new BuilderOverlays.Anchor(tagX, top), groups.addTagChoices(values.entries()),
                choice -> createTag(choice.id())), null);
        return laid.listTop();
    }

    private void createTag(String tagId) {
        apply(groups.createTagRequest(tagId));
        // Decision create-reveals-the-new-row-like-a-follow.
        reveal(GroupsTab.referentName(tagId));
    }

    /**
     * A group card's place in the list and the group it draws.
     *
     * @param row      the listed row
     * @param group    the group it names, empty before the describe answers
     * @param area     the card's left, top and right edges; its bottom unused
     * @param selected whether it is the selected group
     */
    private record GroupCard(GroupsTab.GroupRow row, Optional<GroupsTab.Group> group, Bounds area,
                             boolean selected) {
        boolean tag() {
            return group.map(GroupsTab.Group::tag).orElse(false);
        }

        String base() {
            return group.map(GroupsTab.Group::base).orElse(NO_TEXT);
        }
    }

    /**
     * Where a card's lines stand below its name line.
     *
     * @param members   the Members line
     * @param referents the Used by line
     * @param usedByTop the Used by line's top
     * @param worthTop  the goo value line's top
     * @param worth     the evaluated goo value's runs, empty where none resolved
     * @param value     the value expression's runs, empty where none is set
     * @param body      where the value's button or builder stands, past its Value: label
     */
    private record CardLayout(MembersLine members, ReferentsLine referents, int usedByTop,
                              List<ExpressionLines.Run> value, BuilderFrame.Row frame) {
    }

    // Decision group-card-shows-its-goo-value-above-a-labelled-builder: the goo value line, then the value past
    // Value:, the label left out where the button itself reads Value: Not explicitly set.
    // Decision one-builder-frame-on-every-pane: the goo value is the frame's amount line, Value: on it, the box below.
    private CardLayout layCard(Font font, GroupCard card) {
        Bounds area = card.area();
        List<ExpressionLines.Run> runs = GroupsTab.rowRuns(card.row(), icons::nameOf);
        MembersLine members = layMembers(font, card.row(), runs.subList(1, runs.size()), area);
        ReferentsLine referents = layReferents(font, card.row().name(), groups.referents(card.row().name()),
                area.left() + ControlFlow.INDENT, area.right());
        int usedByTop = area.top() + ROW_HEIGHT + members.height();
        int worthTop = usedByTop + referents.height();
        List<ExpressionLines.Run> value = valueRuns(card.base(), null);
        BuilderFrame.Row frame = new BuilderFrame.Row(new Bounds(area.left(), worthTop, area.right(), area.bottom()),
                value.isEmpty() ? null : VALUE_LABEL, worthGrid(card), true);
        return new CardLayout(members, referents, usedByTop, value, frame);
    }

    // A card: #name with its pencil, or "tag" for a tag referent, and its x on line 1; "Members:" and the member
    // icons, the rest behind ..+n, one indent in on line 2; Used by; the goo value; then Value: and the value.
    // Answers its height. Decision group-card-is-name-then-members-then-value.
    private int drawGroupCard(GuiGraphicsExtractor graphics, Font font, GroupCard card, ClickTargets targets) {
        Bounds area = card.area();
        String target = BASE_TARGET + card.row().name();
        boolean buildingHere = target.equals(building) && builder.isOpen();
        CardLayout laid = layCard(font, card);
        BuilderFrame.Laid placed = BuilderFrame.lay(font, laid.frame());
        int readHeight = buildingHere ? 0
                : ReadValueButton.height(font, laid.value(), placed.boxRight() - placed.valueX());
        int readBottom = placed.boxTop() + readHeight;
        if (card.selected()) {
            graphics.fill(area.left(), area.top(), area.right(), readBottom, Palette.SELECTED_FILL);
        }
        targets.add(area.left(), area.top(), area.right() - area.left(), readBottom - area.top(),
                () -> pickGroup(card.row().name()));
        drawCardHead(graphics, font, card, targets);
        drawMembers(graphics, font, card.row().name(), laid.members(), targets);
        drawReferents(graphics, font, laid.referents(), area.left() + ControlFlow.INDENT, laid.usedByTop(), targets);
        Built built = new Built(target, card.base(), null, typed -> writeBase(card.row().name(), typed),
                () -> pickGroup(card.row().name()));
        int valueHeight = BuilderFrame.draw(graphics, font, laid.frame(), new BuilderFrame.Value(buildingHere,
                laid.value(), errorOf(card), box -> drawBuilt(graphics, font, box, box.top(), built, targets)));
        return laid.frame().row().top() + valueHeight + HALF - area.top();
    }

    // The group's evaluated goo value, type icons beside amounts, read off a member the group values with no tweak
    // of its own, as no group's own evaluation reaches the manager; none where no such member resolved.
    // Decision group-card-shows-its-goo-value-above-a-labelled-builder.
    private GooAmountGrid worthGrid(GroupCard card) {
        Map<String, String> worth = card.group().map(group -> worthOf(group, values)).orElse(Map.of());
        List<TooltipLine.GooAmount> amounts = new ArrayList<>();
        worth.forEach((type, amount) -> amounts.add(new TooltipLine.GooAmount(type, amount)));
        return worth.isEmpty() ? GooAmountGrid.NONE : new GooAmountGrid(new TooltipLine.GooAmounts(amounts));
    }

    // Decision one-red-error-line-under-every-value: a valued group's error is the one its members failed on, as no
    // group's own evaluation reaches the manager.
    private String errorOf(GroupCard card) {
        return card.group().filter(group -> group.base() != null && !group.base().isBlank())
                .flatMap(group -> group.members().stream().map(member -> values.rowOf(member.id()).error())
                        .filter(error -> !error.isEmpty()).findFirst())
                .orElse(NO_TEXT);
    }

    /**
     * @param group  a group
     * @param values the Items tab's state, holding each member's evaluated value
     * @return the group's goo value, read off a member it values with no tweak of its own; empty where the group
     *     takes no base or no such member resolved
     */
    static Map<String, String> worthOf(GroupsTab.Group group, ValuesTab values) {
        if (group.base() == null || group.base().isBlank()) {
            return Map.of();
        }
        Set<String> tweaked = new HashSet<>();
        group.tweaks().forEach(tweak -> tweaked.add(tweak.member()));
        return group.members().stream().map(GroupsTab.Member::id).filter(id -> !tweaked.contains(id))
                .filter(id -> valuedByGroup(values, id, group.name())).map(values::worthOf)
                .filter(worth -> !worth.isEmpty()).findFirst().orElse(Map.of());
    }

    private static boolean valuedByGroup(ValuesTab values, String id, String group) {
        ValuesTab.Source source = values.sourceOf(id);
        return source.kind() == ValuesTab.SourceKind.GROUP && group.equals(source.label());
    }



    // Line 1: the #name, red where it names an unknown member, its pencil or "tag", and the x at the right end; the
    // rename box stands in the name's place while the card's group is renamed.
    // Decision rename-is-a-pencil-and-delete-a-red-x-everywhere.
    private void drawCardHead(GuiGraphicsExtractor graphics, Font font, GroupCard card, ClickTargets targets) {
        Bounds area = card.area();
        String name = GroupsTab.rowRuns(card.row(), icons::nameOf).getFirst().text();
        int y = area.top();
        int deleteWidth = ButtonPainter.width(font, DELETE);
        drawRemoval(graphics, font, area.right(), y, card.row().name(), targets);
        String after = card.tag() ? TAG_WORD : RENAME;
        int afterWidth = card.tag() ? font.width(TAG_WORD) : ButtonPainter.width(font, RENAME);
        int nameTextWidth = Math.max(1, area.right() - area.left() - afterWidth - deleteWidth
                - BuilderOverlays.GAP * HALF);
        if (renaming && card.selected() && nameBox != null) {
            int room = area.right() - area.left() - deleteWidth - BuilderOverlays.GAP - NameBoxButtons.width(font);
            placeNameBox(graphics, font, nameBox, area.left(), y, Math.max(1, Math.min(NAME_BOX_WIDTH, room)),
                    targets);
            return;
        }
        int textTop = RowLayout.nameTop(font, y);
        // Decisions one-name-primitive-cuts-and-hovers-the-whole-name-on-every-pane,
        // hovering-a-truncated-group-name-shows-the-full-name: the name cuts and hovers as every pane's does.
        String shown = ButtonPainter.drawEntryName(graphics, font, targets, new ButtonPainter.EntryName(name,
                area.left(), textTop, nameTextWidth, groups.removals().isMarked(RemovalMarks.Tab.GROUPS,
                card.row().name()), card.row().unknownCount() == 0 ? Palette.TEXT : Palette.DANGER, null));
        int afterX = area.left() + font.width(shown) + ButtonPainter.PENCIL_GAP;
        if (card.tag()) {
            graphics.text(font, after, afterX, textTop, Palette.MUTED);
        } else {
            BuilderOverlays.control(graphics, font, RENAME, RENAME_HINT, afterX, y, targets,
                    at -> startRenaming(card.row().name(), name));
        }
    }

    /**
     * Where a card's members line lays its icons.
     *
     * @param area     the card's bounds
     * @param members  the member icons' runs
     * @param itemsX   the first icon's x, past the label
     * @param fit      how many show and whether ..+n follows
     * @param end      where the end button stands
     * @param perRow   the icons a line holds
     * @param expanded whether ..+n expanded the line
     */
    private record MembersLine(Bounds area, List<ExpressionLines.Run> members, int itemsX, UsedByLine.Fit fit,
                               UsedByLine.Slot end, int perRow, boolean expanded) {
        int height() {
            return (end.row() + 1) * ROW_HEIGHT;
        }
    }

    private MembersLine layMembers(Font font, GroupsTab.GroupRow row, List<ExpressionLines.Run> members,
                                   Bounds area) {
        int itemsX = area.left() + ControlFlow.INDENT + font.width(MEMBERS_COLON) + BuilderOverlays.GAP;
        int room = area.right() - itemsX;
        int step = ICON_SIZE + ICON_GAP;
        int perRow = Math.max(1, room / step);
        boolean expanded = expandedRows.expanded(row.name());
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(members.size())
                : UsedByLine.fit(members.size(), room, step,
                        hidden -> ButtonPainter.width(font, UsedByLine.moreLabel(hidden)));
        UsedByLine.Slot end = UsedByLine.endSlot(fit, expanded, perRow, step, room,
                ButtonPainter.width(font, UsedByLine.HIDE));
        return new MembersLine(area, members, itemsX, fit, end, perRow, expanded);
    }

    /**
     * Where a card's Used by line lays its iterator links.
     *
     * @param referents the iterators sourcing the group
     * @param itemsX    the first link's x, past the label
     * @param fit       how many links show and whether ..+n follows
     * @param slots     each shown link's slot, then the end button's
     * @param endLabel  ..+n, Hide, or none where every link fits
     * @param key       the line's key among the expanded lists
     */
    private record ReferentsLine(List<String> referents, int itemsX, UsedByLine.Fit fit, List<UsedByLine.Slot> slots,
                                 Optional<String> endLabel, String key) {
        int height() {
            return referents.isEmpty() ? 0 : (UsedByLine.lastRow(slots, endLabel.isPresent()) + 1) * ROW_HEIGHT;
        }
    }

    // The links that fit the line, then ..+n counting the rest, as every used-by line cuts short; an expanded line
    // flows onto as many rows as it needs and ends on Hide (decisions
    // diagnose-and-correct-group-referents-without-the-ellipsis, more-button-reads-dot-dot-plus-n).
    private ReferentsLine layReferents(Font font, String name, List<String> referents, int left, int right) {
        int itemsX = left + font.width(USED_BY);
        int room = right - itemsX;
        String key = name + REFERENTS_KEY;
        List<Integer> widths = referents.stream().map(iterator -> ButtonPainter.width(font, iterator)).toList();
        boolean expanded = expandedRows.expanded(key);
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(referents.size())
                : UsedByLine.fitSlots(widths, ICON_GAP, room,
                        hidden -> ButtonPainter.width(font, UsedByLine.moreLabel(hidden)));
        Optional<String> endLabel = UsedByLine.endLabel(fit, expanded);
        List<UsedByLine.Slot> slots = UsedByLine.flow(widths.subList(0, fit.shown()), ICON_GAP, room,
                endLabel.map(label -> ButtonPainter.width(font, label)).orElse(0));
        return new ReferentsLine(referents, itemsX, fit, slots, endLabel, key);
    }

    // "Used by" then a link to each iterator whose source is the group, which a shift + left click follows to its
    // row on the Conversions pane; nothing draws for a group nothing iterates.
    // Decision group-card-lists-its-referents-as-iterator-links.
    private void drawReferents(GuiGraphicsExtractor graphics, Font font, ReferentsLine line, int left, int top,
                               ClickTargets targets) {
        if (line.referents().isEmpty()) {
            return;
        }
        graphics.text(font, USED_BY, left, Lettering.full(font).centeredTop(top, ROW_HEIGHT), Palette.MUTED);
        for (int index = 0; index < line.fit().shown(); index++) {
            String iterator = line.referents().get(index);
            UsedByLine.Slot slot = line.slots().get(index);
            // Decision group-referent-previews-shorthand-members-then-its-value: an iterator previews its group.
            ButtonPainter.drawFollow(graphics, font, iterator, line.itemsX() + slot.x(),
                    top + slot.row() * ROW_HEIGHT, ROW_HEIGHT, targets, () -> actions.openMutator(iterator),
                    GroupHovers.iteratorHoverKey(iterator));
        }
        UsedByLine.Slot end = line.slots().getLast();
        boolean expanded = expandedRows.expanded(line.key());
        line.endLabel().ifPresent(label -> ButtonPainter.draw(graphics, font, label, line.itemsX() + end.x(),
                top + end.row() * ROW_HEIGHT, ROW_HEIGHT, targets, () -> toggle(line.key(), expanded)));
    }

    // Line 2: "Members:" then the member icons named on hover, ending on ..+n or Hide (decisions
    // groups-list-members-as-icons-without-headers-or-count, expanded-used-by-lists-take-a-hide-button).
    private void drawMembers(GuiGraphicsExtractor graphics, Font font, String name, MembersLine line,
                             ClickTargets targets) {
        int top = line.area().top() + ROW_HEIGHT;
        graphics.text(font, MEMBERS_COLON, line.area().left() + ControlFlow.INDENT,
                Lettering.full(font).centeredTop(top, ROW_HEIGHT), Palette.MUTED);
        drawMemberIcons(graphics, line.members().subList(0, line.fit().shown()), line.itemsX(), top,
                line.expanded() ? line.perRow() : line.fit().shown(), targets);
        UsedByLine.endLabel(line.fit(), line.expanded()).ifPresent(label -> ButtonPainter.draw(graphics, font, label,
                line.itemsX() + line.end().x(), top + line.end().row() * ROW_HEIGHT, ROW_HEIGHT, targets,
                () -> toggle(name, line.expanded())));
    }

    /**
     * @param pane the Groups pane's bounds
     * @return the list column's width, seven tenths of the pane (decision
     *     member-list-takes-30-percent-with-no-indent)
     */
    static int listWidth(Bounds pane) {
        return (pane.right() - pane.left()) * LIST_PARTS / PARTS;
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
            ItemLinks.draw(graphics, icons, targets, actions, id, iconX, iconY);
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
        revealing.follow(name, () -> {
            if (search != null) {
                search.setValue(NO_TEXT);
            }
            groups.search(NO_TEXT, values.entries());
        });
        pickGroup(name);
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
        int y = detail.top() - detailBar.offset();
        // Decision detail-is-members-plus-or-tag-members-with-the-tag-id: the detail is membership alone.
        // Decision member-list-takes-30-percent-with-no-indent: its rows stand at the detail's left.
        if (group.tag()) {
            y = drawTagMembersHead(graphics, font, detail.left(), y, group.name());
        } else {
            y = drawAddMember(graphics, font, detail, y, targets);
        }
        y = drawCards(graphics, font, detail, y, !group.tag(), targets);
        if (!group.tag()) {
            y = drawMemberLine(graphics, font, detail, group, y + SECTION_GAP, targets);
        }
        targets.unclip();
        graphics.disableScissor();
        if (!group.tag()) {
            memberRails.drawHeld(graphics, detail);
        }
        detailBar.measureAndDraw(graphics, targets, y, detail.right() + ScrollBar.GAP + ScrollBar.WIDTH, detail.top(),
                detail.bottom());
    }

    // "Tag members" over the tag's fully qualified id, which take no [+]; answers the y below them.
    // Decision detail-is-members-plus-or-tag-members-with-the-tag-id.
    private int drawTagMembersHead(GuiGraphicsExtractor graphics, Font font, int left, int top, String name) {
        graphics.text(font, TAG_MEMBERS_LABEL, left, Lettering.full(font).centeredTop(top, ROW_HEIGHT), Palette.MUTED);
        graphics.text(font, GroupsTab.tagId(name, values.entries()), left,
                Lettering.full(font).centeredTop(top + ROW_HEIGHT, ROW_HEIGHT), Palette.TEXT);
        return top + ROW_HEIGHT + ROW_HEIGHT;
    }

    // "Members" heads a custom group's list with the [+] that adds a member right of it; answers the y below the row.
    // Decisions members-labels-the-group-member-list, detail-is-members-plus-or-tag-members-with-the-tag-id.
    private int drawAddMember(GuiGraphicsExtractor graphics, Font font, Bounds detail, int top, ClickTargets targets) {
        graphics.text(font, MEMBERS_LABEL, detail.left(), Lettering.full(font).centeredTop(top, ROW_HEIGHT),
                Palette.MUTED);
        BuilderOverlays.control(graphics, font, ADD_MEMBER, null,
                detail.left() + font.width(MEMBERS_LABEL) + BuilderOverlays.GAP, top, targets,
                at -> overlays.openDropdown(at, SearchDropdown.overMembers(values.entries()),
                        choice -> apply(groups.addPickRequest(choice.id()))));
        return top + ROW_HEIGHT;
    }

    /**
     * A built value's change.
     */
    @FunctionalInterface
    private interface Writes {
        void wrote(String typed);
    }

    /**
     * A value the builder edits in place of its read view.
     *
     * @param target  which value it is, the builder standing open over it while building names it
     * @param shown   the value as the draft spells it
     * @param empty   what the read view says while it is blank, null for Value: Not explicitly set
     * @param writes  takes each change
     * @param opening runs before the builder opens, selecting what the value belongs to
     */
    private record Built(String target, String shown, @Nullable String empty, Writes writes, Runnable opening) {
    }

    // A blank value reads its empty text, or, with none, has no runs, which reads Value: Not explicitly set.
    // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
    private List<ExpressionLines.Run> valueRuns(String shown, @Nullable String empty) {
        if (!shown.isBlank()) {
            return ExpressionLines.shownRuns(shown, icons::nameOf);
        }
        return empty == null ? List.of() : ExpressionLines.textRuns(empty);
    }

    // A value drawn curated in its read-mode button, a plain left click opening the builder over it in its place
    // and a shift + left click on a ref opening its prefab; answers its height.
    // Decision shift-left-click-follows-plain-left-modifies.
    // Decision read-mode-builder-is-a-button-with-a-pencil-segment.
    private int drawBuilt(GuiGraphicsExtractor graphics, Font font, Bounds detail, int y, Built built,
                          ClickTargets targets) {
        if (built.target().equals(building) && builder.isOpen()) {
            return builder.extract(graphics, font, detail, y, targets);
        }
        String shown = built.shown();
        Runnable open = () -> {
            built.opening().run();
            building = built.target();
            builder.open(shown, built.writes()::wrote, this::closeBuilder);
        };
        return ReadValueButton.draw(graphics, font, valueRuns(shown, built.empty()),
                new ReadValueButton.Place(detail.left(), y,
                detail.right(), open), new ExpressionLines.Painter(graphics, icons, targets, Palette.TEXT, Palette.LINK,
                ExpressionLines.followedOnShiftClick(targets, actions::openVariable)), targets);
    }

    // Answers the y below the cards; a card cut by the pane's edge is not drawn, so no icon shows half. A railed list,
    // a custom group's, numbers each member and draws its rail (decision custom-group-members-drag-by-numbered-rails).
    private int drawCards(GuiGraphicsExtractor graphics, Font font, Bounds detail, int top, boolean railed,
                          ClickTargets targets) {
        List<GroupsTab.MemberRow> members = groups.memberRows(values.entries());
        String selected = groups.selectedMember().orElse(NO_TEXT);
        memberRails.layList(top, members.size());
        for (int index = 0; index < members.size(); index++) {
            int cardY = top + index * CARD_HEIGHT;
            if (cardY < detail.top() || cardY + CARD_HEIGHT > detail.bottom()) {
                continue;
            }
            RowLayout.drawSeparatorAbove(graphics, index, cardY, detail.left(), detail.right());
            Bounds card = new Bounds(detail.left(), cardY, detail.right(), cardY + CARD_HEIGHT);
            GroupsTab.MemberRow member = members.get(index);
            drawCard(graphics, font, member, card, railed ? memberRails.iconLeft(font, card) : card.left(), selected,
                    targets);
            if (railed) {
                memberRails.draw(graphics, font, card, index, member.id(), targets);
            }
        }
        return top + members.size() * CARD_HEIGHT;
    }

    // Decision group-list-takes-60-percent-and-labels-span-the-pane: a member's sprite, then its name and note on one
    // line reaching the detail's right edge, the text centered on the 18px row.
    private void drawCard(GuiGraphicsExtractor graphics, Font font, GroupsTab.MemberRow member, Bounds card,
                          int iconLeft, String selected, ClickTargets targets) {
        if (member.id().equals(selected)) {
            graphics.fill(card.left(), card.top(), card.right(), card.bottom(), Palette.SELECTED_FILL);
        }
        // The card's own click lies under its sprite's follow, so the sprite hovers and a plain click picks the card.
        targets.add(card.left(), card.top(), card.right() - card.left(), CARD_HEIGHT, () -> pickMember(member.id()));
        // Decision one-item-icon-primitive-hovers-and-links-every-icon-but-the-rows-own.
        ItemLinks.draw(graphics, icons, targets, actions, member.id(), iconLeft, card.top() + 1);
        int textX = iconLeft + ICON_SIZE + ICON_GAP;
        int textTop = Lettering.full(font).centeredTop(card.top(), RowLayout.SPRITE_ROW_HEIGHT);
        String name = font.plainSubstrByWidth(member.name(), card.right() - textX);
        graphics.text(font, name, textX, textTop, member.known() ? Palette.TEXT : Palette.DANGER);
        int noteX = textX + font.width(name + NOTE_GAP);
        if (!member.note().isEmpty() && noteX < card.right()) {
            graphics.text(font, font.plainSubstrByWidth(member.note(), card.right() - noteX), noteX, textTop,
                    Palette.MUTED);
        }
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
            graphics.text(font, NEEDS_WORTH, x, top, Palette.MUTED);
            return top + LINE_HEIGHT;
        }
        String id = member.get();
        GroupsTab.MemberLine line = groups.memberLine(id);
        graphics.text(font, LINE_LABEL, x, top, Palette.MUTED);
        int y = top + LINE_HEIGHT;
        int afterCount = BuilderOverlays.control(graphics, font, COUNT_START + line.count(), x, y, targets,
                at -> overlays.openNumber(at, String.valueOf(line.count()), typed -> countOf(typed).ifPresent(
                        count -> apply(groups.tweakRequest(id, count, line.trait(), line.extra())))));
        BuilderOverlays.control(graphics, font, REMOVE, afterCount, y, targets, at -> removeMember(id));
        y += ROW_HEIGHT;
        graphics.text(font, EXTRA_LABEL, x, y, Palette.MUTED);
        y += LINE_HEIGHT;
        return y + drawBuilt(graphics, font, detail, y, new Built(EXTRA_TARGET + id, line.extra(), NO_EXTRA,
                typed -> apply(groups.tweakRequest(id, groups.memberLine(id).count(), line.trait(), typed)),
                () -> { }), targets);
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

    // The open name box, naming a new group or renaming the selected one, with its ✓ and ↺ right of it, which run what
    // Enter and Escape run.
    // Decision open-name-box-takes-a-done-checkmark.
    // Decision open-name-box-takes-a-gold-arrow-that-cancels.
    private void placeNameBox(GuiGraphicsExtractor graphics, Font font, EditBox box, int x, int rowTop, int width,
                              ClickTargets targets) {
        box.setPosition(x, rowTop);
        box.setWidth(width);
        box.visible = visible;
        NameBoxButtons.draw(graphics, font, x + width, rowTop, ButtonPainter.HEIGHT, targets,
                () -> commitName(box.getValue()), this::stopNaming);
    }

    private void commitName(String typed) {
        if (renaming) {
            renameGroup(typed);
        } else {
            createGroup(typed);
        }
    }

    // The card's group is selected first, since the rename writes the selected group.
    private void startRenaming(String group, String shown) {
        pickGroup(group);
        startNaming();
        renaming = true;
        if (nameBox != null) {
            nameBox.setValue(GroupsTab.referentName(shown));
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
        ButtonPainter.drawColored(graphics, font, DELETE, Palette.DANGER, right - ButtonPainter.width(font, DELETE), y,
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
        // Decision create-reveals-the-new-row-like-a-follow.
        reveal(name);
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

    /**
     * Writes a group's base into the draft's #group line, a blank base dropping it, then evaluates the draft afresh on
     * the same call, as the Items and Prefabs builders write (decision one-red-error-line-under-every-value).
     *
     * @param group the group's name
     * @param typed the base the builder holds
     */
    void writeBase(String group, String typed) {
        String key = GROUP_PREFIX + group;
        if (typed.isBlank()) {
            values.dropEntry(key);
        } else {
            values.edit(key, typed);
        }
        actions.draftChanged();
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
