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
 * Draws the Groups tab: the group list, the selected group's picker, base line, member cards with their icons, the
 * selected member's count and trait, and the lines the file will hold, as index.html's groups section does
 * (decision goo-workbench-opens-the-in-game-manager-screen). The state lives in {@link GroupsTab}.
 */
final class GroupsPane {
    private static final int LIST_WIDTH = 150;
    private static final int GAP = 12;
    private static final int HALF = 2;
    private static final int ROW_HEIGHT = 14;
    private static final int LINE_HEIGHT = 11;
    private static final int BOX_HEIGHT = 16;
    private static final int PICKER_WIDTH = 160;
    private static final int BASE_WIDTH = 200;
    private static final int TWEAK_BOX_WIDTH = 90;
    private static final int TRAIT_TEXT_WIDTH = 160;
    private static final int CARD_WIDTH = 130;
    private static final int CARD_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int TEXT_NUDGE = 4;
    private static final int CHOICES_SHOWN = 6;
    private static final int TWEAK_AREA = 52;
    private static final int PREVIEW_LINES = 3;
    private static final int LABEL_GAP = 6;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int LINK = 0xFF80C0FF;
    private static final int SELECTED_COLOR = 0x60FFFFFF;
    private static final int CHOICES_COLOR = 0xF0202020;
    private static final String TAG_PREFIX = "#";
    private static final String MEMBERS_SUFFIX = " members";
    private static final String NAME_GAP = "  ";
    private static final String UNKNOWN_OPEN = " (";
    private static final String UNKNOWN_CLOSE = " not in the inventory)";
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
    private int listScroll;
    private int cardScroll;
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
     * @param shown whether the Groups tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        Optional<GroupsTab.Group> group = groups.selected();
        boolean editing = shown && group.isPresent() && group.get().base() != null
                && groups.selectedMember().isPresent();
        setVisible(picker, shown && group.isPresent());
        setVisible(base, shown && group.isPresent());
        setVisible(count, editing);
        setVisible(trait, editing);
        setVisible(traitText, editing);
    }

    private static void setVisible(@Nullable EditBox box, boolean shown) {
        if (box != null) {
            box.visible = shown;
        }
    }

    /**
     * Reads every group of the draft afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.DESCRIBE_GROUPS, values.modelText(), described -> {
            groups.readDescribed(described);
            show(visible);
        });
    }

    /**
     * @param x      the cursor's x
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, int left, double amount) {
        int step = (int) -Math.signum(amount);
        if (x < left + LIST_WIDTH) {
            listScroll = Math.max(0, listScroll + step);
        } else {
            cardScroll = Math.max(0, cardScroll + step);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        drawList(graphics, font, bounds, targets);
        int detailLeft = bounds.left() + LIST_WIDTH + GAP;
        Optional<GroupsTab.Group> selected = groups.selected();
        if (selected.isEmpty()) {
            graphics.text(font, groups.groupRows().isEmpty() ? NO_GROUPS : NO_GROUP, detailLeft, bounds.top(), GRAY);
            return;
        }
        drawDetail(graphics, font, new Bounds(detailLeft, bounds.top(), bounds.right(), bounds.bottom()),
                selected.get(), targets);
    }

    private void drawList(GuiGraphicsExtractor graphics, Font font, Bounds bounds, ClickTargets targets) {
        List<GroupsTab.GroupRow> rows = groups.groupRows();
        int shown = Math.max(1, (bounds.bottom() - bounds.top()) / ROW_HEIGHT);
        listScroll = Math.min(listScroll, Math.max(0, rows.size() - shown));
        String selected = groups.selected().map(GroupsTab.Group::name).orElse(NO_TEXT);
        int y = bounds.top();
        for (int index = listScroll; index < Math.min(rows.size(), listScroll + shown); index++) {
            GroupsTab.GroupRow row = rows.get(index);
            if (row.name().equals(selected)) {
                graphics.fill(bounds.left(), y, bounds.left() + LIST_WIDTH, y + ROW_HEIGHT, SELECTED_COLOR);
            }
            String count = row.members() + (row.unknownCount() == 0 ? NO_TEXT
                    : UNKNOWN_OPEN + row.unknownCount() + UNKNOWN_CLOSE);
            graphics.text(font, font.plainSubstrByWidth(TAG_PREFIX + row.name(), LIST_WIDTH / HALF), bounds.left(),
                    y + TEXT_NUDGE / HALF, WHITE);
            graphics.text(font, font.plainSubstrByWidth(count, LIST_WIDTH / HALF), bounds.left() + LIST_WIDTH / HALF,
                    y + TEXT_NUDGE / HALF, GRAY);
            targets.add(bounds.left(), y, LIST_WIDTH, ROW_HEIGHT, () -> pickGroup(row.name()));
            y += ROW_HEIGHT;
        }
    }

    private void pickGroup(String name) {
        groups.selectGroup(name);
        cardScroll = 0;
        notice = NO_TEXT;
        groups.selected().ifPresent(group -> {
            if (base != null) {
                base.setValue(group.base() == null ? NO_TEXT : group.base());
            }
        });
        show(visible);
    }

    private void drawDetail(GuiGraphicsExtractor graphics, Font font, Bounds detail, GroupsTab.Group group,
                            ClickTargets targets) {
        int x = detail.left();
        int y = detail.top();
        graphics.text(font, TAG_PREFIX + group.name() + NAME_GAP + group.members().size() + MEMBERS_SUFFIX, x, y,
                WHITE);
        y += LINE_HEIGHT + HALF;
        place(picker, x, y);
        int addX = x + PICKER_WIDTH + LABEL_GAP;
        graphics.text(font, ADD, addX, y + TEXT_NUDGE, LINK);
        targets.add(addX, y, font.width(ADD), BOX_HEIGHT, this::addPick);
        int pickerY = y;
        y += BOX_HEIGHT + LABEL_GAP;
        graphics.text(font, BASE_LABEL, x, y + TEXT_NUDGE, GRAY);
        int baseX = x + font.width(BASE_LABEL) + LABEL_GAP;
        place(base, baseX, y);
        int setX = baseX + BASE_WIDTH + LABEL_GAP;
        graphics.text(font, SET_BASE, setX, y + TEXT_NUDGE, LINK);
        targets.add(setX, y, font.width(SET_BASE), BOX_HEIGHT, this::setBase);
        y += BOX_HEIGHT + LABEL_GAP;
        graphics.text(font, notice.isEmpty() ? MEMBERS_HINT : notice, x, y, notice.isEmpty() ? GRAY : RED);
        y += LINE_HEIGHT;
        int previewTop = detail.bottom() - LINE_HEIGHT * (PREVIEW_LINES + 1);
        int tweakTop = previewTop - TWEAK_AREA;
        drawCards(graphics, font, new Bounds(x, y, detail.right(), tweakTop - LABEL_GAP), targets);
        drawTweak(graphics, font, x, tweakTop, group, targets);
        drawPreview(graphics, font, x, previewTop, detail.right() - x, group);
        drawChoices(graphics, font, x, pickerY + BOX_HEIGHT, targets);
    }

    private void drawCards(GuiGraphicsExtractor graphics, Font font, Bounds cards, ClickTargets targets) {
        List<GroupsTab.MemberRow> members = groups.memberRows(values.entries());
        int columns = Math.max(1, (cards.right() - cards.left()) / CARD_WIDTH);
        int rows = Math.max(1, (cards.bottom() - cards.top()) / CARD_HEIGHT);
        int totalRows = (members.size() + columns - 1) / columns;
        cardScroll = Math.min(cardScroll, Math.max(0, totalRows - rows));
        String selected = groups.selectedMember().orElse(NO_TEXT);
        graphics.enableScissor(cards.left(), cards.top(), cards.right(), cards.bottom());
        targets.clip(cards.left(), cards.top(), cards.right(), cards.bottom());
        for (int index = cardScroll * columns; index < members.size(); index++) {
            int row = index / columns - cardScroll;
            if (row >= rows) {
                break;
            }
            GroupsTab.MemberRow member = members.get(index);
            int cardX = cards.left() + index % columns * CARD_WIDTH;
            int cardY = cards.top() + row * CARD_HEIGHT;
            if (member.id().equals(selected)) {
                graphics.fill(cardX, cardY, cardX + CARD_WIDTH - HALF, cardY + CARD_HEIGHT, SELECTED_COLOR);
            }
            graphics.item(icons.of(member.id()), cardX, cardY + HALF);
            int textX = cardX + ICON_SIZE + ICON_GAP;
            int textWidth = CARD_WIDTH - ICON_SIZE - ICON_GAP - HALF;
            graphics.text(font, font.plainSubstrByWidth(member.name(), textWidth), textX, cardY + 1,
                    member.known() ? WHITE : RED);
            graphics.text(font, font.plainSubstrByWidth(member.note(), textWidth), textX, cardY + LINE_HEIGHT - 1,
                    GRAY);
            targets.add(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, () -> pickMember(member.id()));
        }
        targets.unclip();
        graphics.disableScissor();
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
        show(visible);
    }

    private void drawTweak(GuiGraphicsExtractor graphics, Font font, int x, int top, GroupsTab.Group group,
                           ClickTargets targets) {
        Optional<String> member = groups.selectedMember();
        if (member.isEmpty()) {
            return;
        }
        if (group.base() == null) {
            graphics.text(font, NEEDS_BASE, x, top, GRAY);
            return;
        }
        graphics.text(font, COUNT_LABEL, x, top, GRAY);
        graphics.text(font, TRAIT_LABEL, x + TWEAK_BOX_WIDTH + LABEL_GAP, top, GRAY);
        graphics.text(font, TRAIT_TEXT_LABEL, x + (TWEAK_BOX_WIDTH + LABEL_GAP) * HALF, top, GRAY);
        int boxY = top + LINE_HEIGHT;
        place(count, x, boxY);
        place(trait, x + TWEAK_BOX_WIDTH + LABEL_GAP, boxY);
        place(traitText, x + (TWEAK_BOX_WIDTH + LABEL_GAP) * HALF, boxY);
        int buttonY = boxY + BOX_HEIGHT + LABEL_GAP;
        graphics.text(font, APPLY, x, buttonY, LINK);
        targets.add(x, buttonY, font.width(APPLY), LINE_HEIGHT, () -> applyTweak(member.get()));
        int removeX = x + font.width(APPLY) + LABEL_GAP * HALF;
        graphics.text(font, REMOVE, removeX, buttonY, LINK);
        targets.add(removeX, buttonY, font.width(REMOVE), LINE_HEIGHT, () -> removeMember(member.get()));
    }

    private void drawPreview(GuiGraphicsExtractor graphics, Font font, int x, int top, int width,
                             GroupsTab.Group group) {
        graphics.text(font, PREVIEW_LABEL, x, top, GRAY);
        List<String> lines = group.preview().isEmpty() ? List.of(NO_BASE_PREVIEW) : group.preview();
        for (int index = 0; index < Math.min(PREVIEW_LINES, lines.size()); index++) {
            graphics.text(font, font.plainSubstrByWidth(lines.get(index), width), x, top + LINE_HEIGHT * (index + 1),
                    WHITE);
        }
    }

    // The picker's choices draw over the cards below it while the picker holds focus and text.
    private void drawChoices(GuiGraphicsExtractor graphics, Font font, int x, int top, ClickTargets targets) {
        if (picker == null || !picker.isFocused() || picker.getValue().isBlank()) {
            return;
        }
        List<String> choices = groups.pickerChoices(picker.getValue(), values.entries());
        int shown = Math.min(CHOICES_SHOWN, choices.size());
        if (shown == 0) {
            return;
        }
        graphics.nextStratum();
        graphics.fill(x, top, x + PICKER_WIDTH, top + shown * ROW_HEIGHT, CHOICES_COLOR);
        for (int index = 0; index < shown; index++) {
            String choice = choices.get(index);
            int y = top + index * ROW_HEIGHT;
            graphics.item(icons.of(choice), x, y - 1);
            graphics.text(font, font.plainSubstrByWidth(choice, PICKER_WIDTH - ICON_SIZE - ICON_GAP),
                    x + ICON_SIZE + ICON_GAP, y + TEXT_NUDGE / HALF, WHITE);
            targets.add(x, y, PICKER_WIDTH, ROW_HEIGHT, () -> picker.setValue(choice));
        }
    }

    private static void place(@Nullable EditBox box, int x, int y) {
        if (box != null) {
            box.setPosition(x, y);
        }
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
        show(visible);
    }

    private void apply(String request) {
        notice = NO_TEXT;
        actions.request(WorkbenchOperations.APPLY_TO_GROUP, request, applied -> {
            groups.readApplied(applied);
            show(visible);
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
    }
}
