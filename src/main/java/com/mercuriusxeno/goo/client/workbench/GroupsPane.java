package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Draws the Groups tab: the headed group list, and the selected group's detail as one column scrolling under the
 * wheel: its picker, its base line, its member cards with their icons, the selected member's count and trait, and
 * the lines the file will hold, as index.html's groups section does
 * (decision goo-workbench-opens-the-in-game-manager-screen). The list, the detail column and the picker's choices
 * each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link GroupsTab}.
 */
final class GroupsPane {
    private static final int LIST_WIDTH = 150;
    private static final int COUNT_X = 100;
    private static final int GAP = 12;
    private static final int ROW_HEIGHT = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    private static final int PICKER_WIDTH = 160;
    private static final int BASE_WIDTH = 200;
    private static final int MIN_BOX_WIDTH = 40;
    private static final int TWEAK_BOX_WIDTH = 90;
    private static final int TRAIT_TEXT_WIDTH = 160;
    private static final int CARD_WIDTH = 130;
    private static final int CARD_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int TEXT_NUDGE = 3;
    private static final int CHOICES_SHOWN = 6;
    private static final int LABEL_GAP = 6;
    private static final int SECTION_GAP = 4;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int LINK = 0xFF80C0FF;
    private static final int SELECTED_COLOR = 0x60FFFFFF;
    private static final int CHOICES_COLOR = 0xF0202020;
    private static final String TAG_PREFIX = "#";
    private static final String GROUP_HEADER = "Group";
    private static final String MEMBERS_HEADER = "Members";
    private static final String MEMBERS_SUFFIX = " members";
    private static final String NAME_GAP = "  ";
    private static final String UNKNOWN_OPEN = ", ";
    private static final String UNKNOWN_CLOSE = " not in the inventory";
    private static final String UNKNOWN_SHORT = " (?)";
    private static final String NO_GROUP = "Pick a group.";
    private static final String NO_GROUPS = "The file names no groups.";
    private static final String PICK_HINT = "item or #tag";
    private static final String ADD = "[Add]";
    private static final String BASE_LABEL = "Common base";
    private static final String SET_BASE = "[Set base]";
    private static final String MEMBERS_HINT = "Members: pick one to give it a count and a trait";
    private static final String NEEDS_BASE = "Give the group a base line first; a member's line adds to it.";
    private static final String COUNT_LABEL = "Count";
    private static final String TRAIT_LABEL = "Trait constant";
    private static final String TRAIT_TEXT_LABEL = "Trait value";
    private static final String TRAIT_TEXT_HINT = "{\"hex\": \"$trait\"}, empty for none";
    private static final String APPLY = "[Apply]";
    private static final String REMOVE = "[Remove from group]";
    private static final String PREVIEW_LABEL = "What the file will say:";
    private static final String NO_BASE_PREVIEW = "(no base line yet)";
    private static final String COUNT_REFUSED = "A count is a whole number of at least 1";
    private static final String NAMESPACE_PATTERN = "^.*:";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg$";
    private static final String TRAIT_SUFFIX = "_trait";
    private static final String NO_TEXT = "";

    private final GroupsTab groups;
    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox picker;
    private @Nullable EditBox base;
    private @Nullable EditBox count;
    private @Nullable EditBox trait;
    private @Nullable EditBox traitText;
    private @Nullable Bounds choicesArea;
    private final ScrollBar listBar = new ScrollBar(1);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private final ScrollBar choiceBar = new ScrollBar(1);
    private boolean visible;
    private String notice = NO_TEXT;

    GroupsPane(GroupsTab groups, ValuesTab values, ItemIcons icons, ManagerActions actions) {
        this.groups = groups;
        this.values = values;
        this.icons = icons;
        this.actions = actions;
    }

    /**
     * Stands the pane's boxes among the screen's widgets, keeping what they held across a resize.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        picker = box(font, PICKER_WIDTH, picker, PICK_HINT);
        picker.setResponder(typed -> choiceBar.reset());
        base = box(font, BASE_WIDTH, base, NO_TEXT);
        count = box(font, TWEAK_BOX_WIDTH, count, NO_TEXT);
        trait = box(font, TWEAK_BOX_WIDTH, trait, NO_TEXT);
        traitText = box(font, TRAIT_TEXT_WIDTH, traitText, TRAIT_TEXT_HINT);
        show(visible);
    }

    private EditBox box(Font font, int width, @Nullable EditBox previous, String hint) {
        EditBox box = new EditBox(font, 0, 0, width, BOX_HEIGHT, Component.literal(hint));
        box.setMaxLength(Short.MAX_VALUE);
        if (!hint.isEmpty()) {
            box.setHint(Component.literal(hint));
        }
        if (previous != null) {
            box.setValue(previous.getValue());
        }
        return actions.addWidget(box);
    }

    /**
     * @param shown whether the Groups tab is the one drawn; each box shows only once a frame places it in view
     */
    void show(boolean shown) {
        visible = shown;
        for (EditBox box : boxes()) {
            box.visible = false;
        }
    }

    private List<EditBox> boxes() {
        return java.util.stream.Stream.of(picker, base, count, trait, traitText)
                .filter(java.util.Objects::nonNull).toList();
    }

    /**
     * Reads every group of the draft afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.DESCRIBE_GROUPS, values.modelText(), groups::readDescribed);
    }

    /**
     * @param x      the cursor's x
     * @param y      the cursor's y
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, double y, int left, double amount) {
        if (choicesArea != null && choicesArea.contains(x, y)) {
            choiceBar.wheel(amount);
        } else if (x < left + LIST_WIDTH) {
            listBar.wheel(amount);
        } else {
            detailBar.wheel(amount);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        for (EditBox box : boxes()) {
            box.visible = false;
        }
        choicesArea = null;
        drawList(graphics, font, bounds, targets);
        Bounds detail = new Bounds(bounds.left() + LIST_WIDTH + GAP, bounds.top(),
                bounds.right() - ScrollBar.WIDTH - ScrollBar.GAP, bounds.bottom());
        Optional<GroupsTab.Group> selected = groups.selected();
        if (selected.isEmpty()) {
            graphics.text(font, groups.groupRows().isEmpty() ? NO_GROUPS : NO_GROUP, detail.left(), detail.top(),
                    GRAY);
            return;
        }
        drawDetail(graphics, font, detail, selected.get(), targets);
    }

    private void drawList(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        graphics.text(font, GROUP_HEADER, bounds.left(), bounds.top(), GRAY);
        graphics.text(font, MEMBERS_HEADER, bounds.left() + COUNT_X, bounds.top(), GRAY);
        int top = bounds.top() + LINE_HEIGHT + HALF;
        List<GroupsTab.GroupRow> rows = groups.groupRows();
        int shown = Math.max(1, (bounds.bottom() - top) / ROW_HEIGHT);
        listBar.measure(rows.size(), shown);
        int first = listBar.offset();
        int rowWidth = LIST_WIDTH - ScrollBar.WIDTH - ScrollBar.GAP;
        String selected = groups.selected().map(GroupsTab.Group::name).orElse(NO_TEXT);
        int y = top;
        for (int index = first; index < Math.min(rows.size(), first + shown); index++) {
            GroupsTab.GroupRow row = rows.get(index);
            if (row.name().equals(selected)) {
                graphics.fill(bounds.left(), y, bounds.left() + rowWidth, y + ROW_HEIGHT, SELECTED_COLOR);
            }
            graphics.text(font, font.plainSubstrByWidth(TAG_PREFIX + row.name(), COUNT_X - LABEL_GAP), bounds.left(),
                    y + HALF, WHITE);
            String counted = row.members() + (row.unknownCount() == 0 ? NO_TEXT : UNKNOWN_SHORT);
            graphics.text(font, counted, bounds.left() + COUNT_X, y + HALF, row.unknownCount() == 0 ? GRAY : RED);
            targets.add(bounds.left(), y, rowWidth, ROW_HEIGHT, () -> pickGroup(row.name()));
            y += ROW_HEIGHT;
        }
        listBar.draw(graphics, targets, bounds.left() + LIST_WIDTH, top, top + shown * ROW_HEIGHT);
    }

    private void pickGroup(String name) {
        groups.selectGroup(name);
        detailBar.reset();
        notice = NO_TEXT;
        groups.selected().ifPresent(group -> {
            if (base != null) {
                base.setValue(group.base() == null ? NO_TEXT : group.base());
            }
        });
    }

    private void drawDetail(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group,
                            ClickTargets targets) {
        graphics.enableScissor(detail.left(), detail.top(), detail.right(), detail.bottom());
        targets.clip(detail.left(), detail.top(), detail.right(), detail.bottom());
        int x = detail.left();
        int y = detail.top() - detailBar.offset();
        long unknown = group.members().stream().filter(member -> !member.known()).count();
        graphics.text(font, TAG_PREFIX + group.name() + NAME_GAP + group.members().size() + MEMBERS_SUFFIX
                + (unknown == 0 ? NO_TEXT : UNKNOWN_OPEN + unknown + UNKNOWN_CLOSE), x, y, WHITE);
        y += LINE_HEIGHT + SECTION_GAP;
        int pickerY = y;
        y = drawPickerRow(graphics, font, detail, x, y, targets);
        y = drawBaseRow(graphics, font, detail, x, y, targets);
        graphics.text(font, notice.isEmpty() ? MEMBERS_HINT : notice, x, y, notice.isEmpty() ? GRAY : RED);
        y += LINE_HEIGHT + SECTION_GAP;
        y = drawCards(graphics, font, detail, y, targets);
        y = drawTweak(graphics, font, detail, x, y + SECTION_GAP, group, targets);
        y = drawPreview(graphics, font, x, y + SECTION_GAP, detail.right() - x, group);
        targets.unclip();
        graphics.disableScissor();
        detailBar.measureAndDraw(graphics, targets, y, detail.right() + ScrollBar.GAP + ScrollBar.WIDTH, detail.top(),
                detail.bottom());
        if (picker != null && picker.visible) {
            drawChoices(graphics, font, x, pickerY + BOX_HEIGHT, targets);
        }
    }

    // Answers the y below the picker row.
    private int drawPickerRow(GuiGraphicsExtractor graphics, Font font, Bounds detail, int x, int y,
                              ClickTargets targets) {
        int pickerWidth = Math.max(MIN_BOX_WIDTH, Math.min(PICKER_WIDTH,
                detail.right() - x - font.width(ADD) - LABEL_GAP));
        place(picker, x, y, pickerWidth, detail);
        int addX = x + pickerWidth + LABEL_GAP;
        graphics.text(font, ADD, addX, y + TEXT_NUDGE, LINK);
        targets.add(addX, y, font.width(ADD), BOX_HEIGHT, this::addPick);
        return y + BOX_HEIGHT + SECTION_GAP;
    }

    // Answers the y below the base row; the box shrinks so [Set base] stays inside the pane.
    private int drawBaseRow(GuiGraphicsExtractor graphics, Font font, Bounds detail, int x, int y,
                            ClickTargets targets) {
        graphics.text(font, BASE_LABEL, x, y + TEXT_NUDGE, GRAY);
        int baseX = x + font.width(BASE_LABEL) + LABEL_GAP;
        int baseWidth = Math.max(MIN_BOX_WIDTH, Math.min(BASE_WIDTH,
                detail.right() - baseX - font.width(SET_BASE) - LABEL_GAP));
        place(base, baseX, y, baseWidth, detail);
        int setX = baseX + baseWidth + LABEL_GAP;
        graphics.text(font, SET_BASE, setX, y + TEXT_NUDGE, LINK);
        targets.add(setX, y, font.width(SET_BASE), BOX_HEIGHT, this::setBase);
        return y + BOX_HEIGHT + SECTION_GAP;
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
        groups.selectMember(id);
        Optional<GroupsTab.Tweak> tweak = groups.tweakOf(id);
        if (count != null && trait != null && traitText != null) {
            count.setValue(String.valueOf(tweak.map(GroupsTab.Tweak::count).orElse(1)));
            trait.setValue(tweak.map(GroupsTab.Tweak::trait).orElseGet(() -> id.replaceFirst(NAMESPACE_PATTERN,
                    NO_TEXT).replaceFirst(SPAWN_EGG_SUFFIX, NO_TEXT) + TRAIT_SUFFIX));
            traitText.setValue(tweak.map(GroupsTab.Tweak::traitText).orElse(NO_TEXT));
        }
    }

    // Answers the y below the tweak editor, which stands only while a member is picked.
    private int drawTweak(GuiGraphicsExtractor graphics, Font font, Bounds detail, int x, int top,
                          GroupsTab.Group group, ClickTargets targets) {
        Optional<String> member = groups.selectedMember();
        if (member.isEmpty()) {
            return top;
        }
        if (group.base() == null) {
            graphics.text(font, NEEDS_BASE, x, top, GRAY);
            return top + LINE_HEIGHT;
        }
        int step = TWEAK_BOX_WIDTH + LABEL_GAP;
        graphics.text(font, COUNT_LABEL, x, top, GRAY);
        graphics.text(font, TRAIT_LABEL, x + step, top, GRAY);
        graphics.text(font, TRAIT_TEXT_LABEL, x + step * HALF, top, GRAY);
        int boxY = top + LINE_HEIGHT;
        place(count, x, boxY, TWEAK_BOX_WIDTH, detail);
        place(trait, x + step, boxY, TWEAK_BOX_WIDTH, detail);
        place(traitText, x + step * HALF, boxY, Math.max(MIN_BOX_WIDTH,
                Math.min(TRAIT_TEXT_WIDTH, detail.right() - x - step * HALF)), detail);
        int buttonY = boxY + BOX_HEIGHT + SECTION_GAP;
        graphics.text(font, APPLY, x, buttonY, LINK);
        targets.add(x, buttonY, font.width(APPLY), LINE_HEIGHT, () -> applyTweak(member.get()));
        int removeX = x + font.width(APPLY) + LABEL_GAP * HALF;
        graphics.text(font, REMOVE, removeX, buttonY, LINK);
        targets.add(removeX, buttonY, font.width(REMOVE), LINE_HEIGHT, () -> removeMember(member.get()));
        return buttonY + LINE_HEIGHT;
    }

    // Answers the y below the preview.
    private static int drawPreview(GuiGraphicsExtractor graphics, Font font, int x, int top, int width,
                                   GroupsTab.Group group) {
        graphics.text(font, PREVIEW_LABEL, x, top, GRAY);
        int y = top + LINE_HEIGHT;
        for (String line : group.preview().isEmpty() ? List.of(NO_BASE_PREVIEW) : group.preview()) {
            graphics.text(font, font.plainSubstrByWidth(line, width), x, y, WHITE);
            y += LINE_HEIGHT;
        }
        return y;
    }

    // The picker's choices draw over the detail below it while the picker holds focus and text, and scroll.
    private void drawChoices(GuiGraphicsExtractor graphics, Font font, int x, int top, ClickTargets targets) {
        if (picker == null || !picker.isFocused() || picker.getValue().isBlank()) {
            return;
        }
        List<String> choices = groups.pickerChoices(picker.getValue(), values.entries());
        choiceBar.measure(choices.size(), CHOICES_SHOWN);
        int first = choiceBar.offset();
        int shown = Math.min(CHOICES_SHOWN, choices.size() - first);
        if (shown <= 0) {
            return;
        }
        int width = picker.getWidth();
        graphics.nextStratum();
        graphics.fill(x, top, x + width, top + shown * ROW_HEIGHT, CHOICES_COLOR);
        choicesArea = new Bounds(x, top, x + width, top + shown * ROW_HEIGHT);
        for (int index = 0; index < shown; index++) {
            String choice = choices.get(first + index);
            int y = top + index * ROW_HEIGHT;
            graphics.text(font, font.plainSubstrByWidth(choice, width - HALF - ScrollBar.WIDTH - ScrollBar.GAP),
                    x + HALF, y + HALF, WHITE);
            targets.add(x, y, width - ScrollBar.WIDTH, ROW_HEIGHT, () -> picker.setValue(choice));
        }
        choiceBar.draw(graphics, targets, x + width, top, top + shown * ROW_HEIGHT);
    }

    // A box shows only while the frame places it wholly inside the pane.
    private void place(@Nullable EditBox box, int x, int y, int width, Bounds within) {
        if (box == null) {
            return;
        }
        box.setPosition(x, y);
        box.setWidth(width);
        box.visible = visible && y >= within.top() && y + BOX_HEIGHT <= within.bottom();
    }

    private void addPick() {
        if (picker != null && !picker.getValue().isBlank()) {
            apply(groups.addPickRequest(picker.getValue().trim()));
            picker.setValue(NO_TEXT);
        }
    }

    private void setBase() {
        if (base != null) {
            apply(groups.baseRequest(base.getValue()));
        }
    }

    private void applyTweak(String member) {
        int counted;
        try {
            counted = Integer.parseInt(count == null ? NO_TEXT : count.getValue().trim());
        } catch (NumberFormatException e) {
            counted = 0;
        }
        if (counted < 1 || trait == null || traitText == null) {
            notice = COUNT_REFUSED;
            return;
        }
        apply(groups.tweakRequest(member, counted, trait.getValue(), traitText.getValue()));
    }

    private void removeMember(String member) {
        apply(groups.removeMemberRequest(member));
        groups.selectMember(null);
    }

    private void apply(String request) {
        notice = NO_TEXT;
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
