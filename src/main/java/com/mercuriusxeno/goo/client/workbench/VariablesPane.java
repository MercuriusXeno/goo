package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the Variables tab: each constant's block with its expression, what it resolves to or its error, and the
 * items it cascades into; a click on an expression swaps in the edit box and a blur swaps it back; every $name
 * link opens its variable and hovers its card; rename edits the name in place and delete confirms the readers it
 * removes, as index.html's variables section does (decisions goo-workbench-opens-the-in-game-manager-screen,
 * references-link-to-their-variable-and-hover-a-card). The blocks and the delete confirm's readers each draw a
 * {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link VariablesTab}.
 */
final class VariablesPane {
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    private static final int BLOCK_GAP = 5;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int NAME_WIDTH = 140;
    private static final int KIND_GAP = 8;
    private static final int ACTION_GAP = 6;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int CARD_WIDTH = 220;
    private static final int CARD_PADDING = 6;
    private static final int CARD_OFFSET = 12;
    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 140;
    private static final int READER_GAP = 6;
    private static final int TEXT_NUDGE = 4;
    private static final int HALF = 2;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int LINK = 0xFF80C0FF;
    private static final int TREE_COLOR = 0xFFE0C060;
    private static final int CARD_COLOR = 0xF0181818;
    private static final int DIM_COLOR = 0xA0000000;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final String RESOLVED_SEPARATOR = ", ";
    private static final char HOVER_KEY_MARK = ':';
    private static final String TAG_PREFIX = "#";
    private static final String RENAME = "[Rename]";
    private static final String DELETE = "[Delete]";
    private static final String CANCEL = "[Cancel]";
    private static final String USED_BY = "Used by ";
    private static final String INDIRECTLY = "(indirectly) ";
    private static final String DIRECT_KEY = ":direct";
    private static final String INDIRECT_KEY = ":indirect";
    private static final String DELETE_TITLE_START = "Delete ";
    private static final String DELETE_TITLE_END = " and what reads it";
    private static final String NO_CONSTANTS = "The file names no constants.";
    private static final String NO_EXPRESSION = " ";
    private static final String CONSTANT_KIND = "constant";
    private static final String GROUP_KIND = "group";
    private static final String NO_TEXT = "";

    private final VariablesTab variables;
    private final ManagerDraft draft;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private final Map<String, VariablesTab.HoverCard> cards = new HashMap<>();
    private final Set<String> cardsAsked = new HashSet<>();
    private final Set<String> expandedUsedBy = new HashSet<>();
    private final ScrollBar cascadeBar = new ScrollBar(1);
    private final Map<String, Integer> blockTops = new HashMap<>();
    private final ExpressionBuilderPane builder;
    private @Nullable String building;
    private @Nullable EditBox rename;
    private @Nullable String renaming;
    private @Nullable String deleting;
    private @Nullable String jumpPending;
    private boolean visible;
    private final ScrollBar bar = new ScrollBar(SCROLL_STEP);

    VariablesPane(VariablesTab variables, ManagerDraft draft, ItemIcons icons, ManagerActions actions,
                  ExpressionBuilderPane builder) {
        this.variables = variables;
        this.draft = draft;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
    }

    /**
     * Stands the pane's boxes among the screen's widgets, keeping what they held across a resize.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        builder.init(font);
        EditBox previousRename = rename;
        rename = actions.addWidget(new EditBox(font, 0, 0, NAME_WIDTH, BOX_HEIGHT, Component.literal(NO_TEXT)));
        if (previousRename != null) {
            rename.setValue(previousRename.getValue());
        }
        show(visible);
    }

    /**
     * @param shown whether the Variables tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        builder.show(shown && deleting == null);
        if (rename != null) {
            rename.visible = shown && deleting == null && renaming != null;
        }
    }

    /**
     * Evaluates every constant of the draft afresh; the hover cards read the new draft from now.
     */
    void refresh() {
        cards.clear();
        cardsAsked.clear();
        actions.request(WorkbenchOperations.EVALUATE_CONSTANTS, draft.text(), variables::readEvaluated);
    }

    /**
     * Opens a variable's edit box and scrolls its block into view, as a $name link does.
     *
     * @param name the variable
     */
    void jumpTo(String name) {
        deleting = null;
        startEditing(name);
        jumpPending = name;
    }

    /**
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        if (deleting != null) {
            cascadeBar.wheel(amount);
            return;
        }
        bar.wheel(amount);
    }

    /**
     * Enter submits a rename or ends an edit and Escape drops a rename, so Escape on the box keeps the screen open.
     *
     * @param key the GLFW key pressed
     * @return whether the pane took the key
     */
    boolean keyPressed(int key) {
        if (rename != null && rename.isFocused() && renaming != null) {
            if (isEnter(key)) {
                submitRename(renaming, rename.getValue().trim());
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                renaming = null;
                show(visible);
                return true;
            }
        }
        return builder.keyPressed(key);
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds view, ClickTargets targets,
                 int mouseX, int mouseY) {
        List<VariablesTab.ConstantBlock> blocks = variables.blocks();
        if (blocks.isEmpty()) {
            graphics.text(font, NO_CONSTANTS, view.left(), view.top(), GRAY);
            return;
        }
        applyPendingJump();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(view.left(), view.top(),
                view.right() - ScrollBar.WIDTH - ScrollBar.GAP, view.bottom());
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        boolean renameShown = false;
        int y = bounds.top() - bar.offset();
        for (VariablesTab.ConstantBlock block : blocks) {
            blockTops.put(block.name(), y + bar.offset() - bounds.top());
            renameShown |= drawHead(graphics, font, block, bounds, y, targets);
            y += ExpressionLines.ICON_LINE_HEIGHT;
            boolean editingThis = variables.editing().filter(block.name()::equals).isPresent();
            y += editingThis ? drawBuilder(graphics, font, block, bounds, y, targets)
                    : drawReadView(graphics, font, block, bounds, y, targets);
            y += drawUsedByLines(graphics, font, block.direct(), block.indirect(), bounds.left(), y + HALF,
                    bounds.right() - bounds.left(), block.name(), targets) + BLOCK_GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, targets, y, view.right(), view.top(), view.bottom());
        showBoxes(renameShown);
        builder.drawOverlays(graphics, font, targets);
        drawDeleteDialog(graphics, font, view, targets);
        if (!builder.listOpen()) {
            drawHoverCard(graphics, font, view, targets, mouseX, mouseY);
        }
    }

    // The builder over the block being edited, each change written to the draft and [done] evaluating it afresh
    // (decision expression-builder-composes-every-form-without-typing); answers the height it took.
    private int drawBuilder(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                            GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        if (!builder.isOpen() || !block.name().equals(building)) {
            building = block.name();
            builder.openPrefab(block.name(), block.expression(), typed -> {
                variables.edit(block.name(), typed);
                actions.markUnsaved();
            }, () -> {
                building = null;
                variables.stopEditing();
                actions.draftChanged();
            });
        }
        return builder.extract(graphics, font, bounds, y, targets);
    }

    private void showBoxes(boolean renameShown) {
        boolean open = visible && deleting == null;
        if (rename != null) {
            rename.visible = open && renameShown;
        }
    }

    private void applyPendingJump() {
        if (jumpPending != null && blockTops.containsKey(jumpPending)) {
            bar.scrollTo(blockTops.get(jumpPending) - LINE_HEIGHT);
            jumpPending = null;
        }
    }

    // Answers whether the rename box stands on this block.
    private boolean drawHead(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                             GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        boolean renamingThis = block.name().equals(renaming) && rename != null;
        if (renamingThis) {
            rename.setPosition(x, y - HALF);
        } else {
            graphics.text(font, block.name(), x, y, WHITE);
        }
        int kindX = x + Math.max(NAME_WIDTH, font.width(block.name())) + KIND_GAP;
        graphics.text(font, block.kind(), kindX, y, VariablesTab.TREE_KIND.equals(block.kind()) ? TREE_COLOR : GRAY);
        int actionsWidth = font.width(RENAME) + ACTION_GAP + font.width(DELETE);
        int resolvedX = kindX + font.width(block.kind()) + KIND_GAP;
        int resolvedWidth = bounds.right() - actionsWidth - ACTION_GAP - resolvedX;
        drawResolved(graphics, font, block, resolvedX, y, resolvedWidth, targets);
        int renameX = bounds.right() - actionsWidth;
        graphics.text(font, RENAME, renameX, y, LINK);
        targets.add(renameX, y, font.width(RENAME), LINE_HEIGHT, () -> startRename(block.name()));
        int deleteX = renameX + font.width(RENAME) + ACTION_GAP;
        graphics.text(font, DELETE, deleteX, y, LINK);
        targets.add(deleteX, y, font.width(DELETE), LINE_HEIGHT, () -> askDelete(block.name()));
        return renamingThis;
    }

    // A resolved tree draws each type as its icon beside its amount (decision goo-types-draw-as-icons-named-on-hover).
    private void drawResolved(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block, int x,
                              int y, int width, ClickTargets targets) {
        if (!block.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(block.error(), width), x, y, RED);
            return;
        }
        List<ExpressionLines.Run> resolved = ExpressionLines.resolvedRuns(block.resolved());
        ExpressionLines.draw(font, resolved, x, y - ExpressionLines.textOffset(resolved), width,
                new ExpressionLines.Painter(graphics, icons, targets, GREEN, LINK, (name, linkX, linkY, w) -> { }));
    }

    // Answers the height the read view took.
    private int drawReadView(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                             GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int width = bounds.right() - bounds.left();
        String text = block.expression().isEmpty() ? NO_EXPRESSION : block.expression();
        List<ExpressionLines.Run> runs = ExpressionLines.shownRuns(text, icons::nameOf);
        int height = ExpressionLines.height(font, runs, width) + HALF;
        graphics.fill(bounds.left(), y, bounds.right(), y + height, READ_VIEW_COLOR);
        targets.add(bounds.left(), y, width, height, () -> startEditing(block.name()));
        ExpressionLines.draw(font, runs, bounds.left(), y + 1, width, new ExpressionLines.Painter(graphics, icons,
                targets, block.error().isEmpty() ? WHITE : RED, LINK, (name, linkX, linkY, linkWidth) ->
                        targets.add(linkX, linkY, linkWidth, LINE_HEIGHT, () -> actions.openVariable(name), name)));
        return height;
    }

    // Used by holds the items naming the constant themselves and (indirectly) the terminus items its cascade
    // reaches, each line drawn only where it holds an item (decision used-by-draws-the-terminus-items-alone);
    // answers the height both took.
    private int drawUsedByLines(GuiGraphicsExtractor graphics, Font font, List<String> direct, List<String> indirect,
                                int x, int y, int width, @Nullable String expandKey, ClickTargets targets) {
        int directHeight = drawUsedBy(graphics, font, USED_BY, direct, x, y, width,
                expandKey == null ? null : expandKey + DIRECT_KEY, targets);
        int gap = directHeight > 0 && !indirect.isEmpty() ? HALF : 0;
        return directHeight + gap + drawUsedBy(graphics, font, INDIRECTLY, indirect, x, y + directHeight + gap,
                width, expandKey == null ? null : expandKey + INDIRECT_KEY, targets);
    }

    // Answers the height the used-by items took, none where the constant reaches no item. A line too narrow for
    // them stops at an "... and N more" link, which expands the block's line onto as many rows as it needs; a null
    // expandKey draws the link as plain text, as the hover card does.
    private int drawUsedBy(GuiGraphicsExtractor graphics, Font font, String label, List<String> usedBy, int x, int y,
                           int width, @Nullable String expandKey, ClickTargets targets) {
        if (usedBy.isEmpty()) {
            return 0;
        }
        graphics.text(font, label, x, y + TEXT_NUDGE, GRAY);
        int itemsX = x + Math.max(font.width(USED_BY), font.width(INDIRECTLY));
        int itemsWidth = x + width - itemsX;
        int step = ICON_SIZE + ICON_GAP;
        boolean expanded = expandKey != null && expandedUsedBy.contains(expandKey);
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(usedBy.size())
                : UsedByLine.fit(usedBy.size(), itemsWidth, step, hidden -> font.width(UsedByLine.moreLabel(hidden)));
        int perRow = Math.max(1, itemsWidth / step);
        for (int index = 0; index < fit.shown(); index++) {
            int row = expanded ? index / perRow : 0;
            int column = expanded ? index % perRow : index;
            int iconX = itemsX + column * step;
            int iconY = y + row * (ICON_SIZE + HALF);
            graphics.item(icons.of(usedBy.get(index)), iconX, iconY);
            targets.addHover(iconX, iconY, ICON_SIZE, ICON_SIZE, ItemIcons.hoverKey(usedBy.get(index)));
        }
        if (fit.hidden() > 0) {
            String more = UsedByLine.moreLabel(fit.hidden());
            int moreX = itemsX + fit.shown() * step;
            graphics.text(font, more, moreX, y + TEXT_NUDGE, expandKey == null ? GRAY : LINK);
            if (expandKey != null) {
                targets.add(moreX, y, font.width(more), ICON_SIZE, () -> expandedUsedBy.add(expandKey));
            }
        }
        int rows = expanded ? (fit.shown() + perRow - 1) / perRow : 1;
        return rows * (ICON_SIZE + HALF);
    }

    private void startEditing(String name) {
        boolean switching = variables.editing().filter(name::equals).isEmpty() && variables.editing().isPresent();
        variables.startEditing(name);
        builder.close();
        building = null;
        if (switching) {
            actions.draftChanged();
        }
    }

    private void startRename(String name) {
        renaming = name;
        if (rename != null) {
            rename.setValue(name);
            rename.visible = visible;
            actions.focus(rename);
        }
    }

    private void submitRename(String from, String to) {
        renaming = null;
        show(visible);
        if (to.isEmpty() || to.equals(from)) {
            return;
        }
        actions.request(WorkbenchOperations.RENAME_CONSTANT, variables.renameRequest(from, to), renamed -> {
            draft.read(renamed);
            if (variables.editing().filter(from::equals).isPresent()) {
                variables.startEditing(to);
            }
            actions.draftChanged();
        });
    }

    private void askDelete(String name) {
        if (variables.deletionCascade(name).isEmpty()) {
            delete(name);
            return;
        }
        deleting = name;
        cascadeBar.reset();
        show(visible);
    }

    private void delete(String name) {
        deleting = null;
        if (variables.editing().filter(name::equals).isPresent()) {
            variables.stopEditing();
        }
        show(visible);
        actions.request(WorkbenchOperations.DELETE_CONSTANT, variables.deleteRequest(name), left -> {
            draft.read(left);
            actions.draftChanged();
        });
    }

    private void drawDeleteDialog(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                                  ClickTargets targets) {
        if (deleting == null) {
            return;
        }
        String name = deleting;
        graphics.nextStratum();
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), DIM_COLOR);
        targets.add(bounds.left(), bounds.top(), bounds.right() - bounds.left(), bounds.bottom() - bounds.top(),
                () -> { });
        int left = (bounds.left() + bounds.right() - DIALOG_WIDTH) / HALF;
        int top = (bounds.top() + bounds.bottom() - DIALOG_HEIGHT) / HALF;
        GroupsPane.Bounds dialog = new GroupsPane.Bounds(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT);
        graphics.fill(dialog.left(), dialog.top(), dialog.right(), dialog.bottom(), CARD_COLOR);
        graphics.text(font, DELETE_TITLE_START + name + DELETE_TITLE_END, left + CARD_PADDING,
                top + CARD_PADDING, WHITE);
        drawCascade(graphics, font, name, dialog, targets);
        drawDialogButtons(graphics, font, name, dialog, targets);
    }

    // The readers lay out in rows; the wheel scrolls the rows, so a cascade longer than the dialog stays reachable.
    private void drawCascade(GuiGraphicsExtractor graphics, Font font, String name, GroupsPane.Bounds dialog,
                             ClickTargets targets) {
        int x = dialog.left() + CARD_PADDING;
        int top = dialog.top() + CARD_PADDING + LINE_HEIGHT + CARD_PADDING;
        int rowHeight = ICON_SIZE + HALF;
        int rowsShown = Math.max(1, (dialog.bottom() - LINE_HEIGHT * HALF - CARD_PADDING - top) / rowHeight);
        int right = dialog.right() - CARD_PADDING - ScrollBar.WIDTH - ScrollBar.GAP;
        int first = cascadeBar.offset();
        int row = 0;
        int readerX = x;
        for (VariablesTab.Reader reader : variables.deletionCascade(name)) {
            int readerWidth = readerWidth(font, reader);
            if (readerX + readerWidth > right && readerX > x) {
                row++;
                readerX = x;
            }
            int shownRow = row - first;
            if (shownRow >= 0 && shownRow < rowsShown) {
                drawReader(graphics, font, reader, readerX, top + shownRow * rowHeight, targets);
            }
            readerX += readerWidth + READER_GAP;
        }
        cascadeBar.measure(row + 1, rowsShown);
        cascadeBar.draw(graphics, targets, dialog.right() - CARD_PADDING, top, top + rowsShown * rowHeight);
    }

    private static int readerWidth(Font font, VariablesTab.Reader reader) {
        if (CONSTANT_KIND.equals(reader.kind())) {
            return font.width(reader.id());
        }
        if (GROUP_KIND.equals(reader.kind())) {
            return font.width(reader.id().startsWith(TAG_PREFIX) ? reader.id() : TAG_PREFIX + reader.id());
        }
        return ICON_SIZE;
    }

    private void drawDialogButtons(GuiGraphicsExtractor graphics, Font font, String name, GroupsPane.Bounds dialog,
                                   ClickTargets targets) {
        int x = dialog.left() + CARD_PADDING;
        int buttonY = dialog.bottom() - LINE_HEIGHT - CARD_PADDING;
        graphics.text(font, DELETE, x, buttonY, RED);
        targets.add(x, buttonY, font.width(DELETE), LINE_HEIGHT, () -> delete(name));
        int cancelX = x + font.width(DELETE) + ACTION_GAP * HALF;
        graphics.text(font, CANCEL, cancelX, buttonY, LINK);
        targets.add(cancelX, buttonY, font.width(CANCEL), LINE_HEIGHT, () -> {
            deleting = null;
            show(visible);
        });
    }

    // A constant reads as a $name link, a group or tag as its #name, and an item as its icon; answers the width the
    // reader took.
    private int drawReader(GuiGraphicsExtractor graphics, Font font, VariablesTab.Reader reader, int x, int y,
                           ClickTargets targets) {
        if (CONSTANT_KIND.equals(reader.kind())) {
            String label = reader.id();
            graphics.text(font, label, x, y + TEXT_NUDGE, LINK);
            targets.add(x, y, font.width(label), ICON_SIZE, () -> actions.openVariable(reader.id()), reader.id());
            return font.width(label);
        }
        if (GROUP_KIND.equals(reader.kind())) {
            String label = reader.id().startsWith(TAG_PREFIX) ? reader.id() : TAG_PREFIX + reader.id();
            graphics.text(font, label, x, y + TEXT_NUDGE, GRAY);
            return font.width(label);
        }
        graphics.item(icons.of(reader.id()), x, y);
        return ICON_SIZE;
    }

    private void drawHoverCard(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                               ClickTargets targets, int mouseX, int mouseY) {
        Optional<String> hovered = targets.hovered(mouseX, mouseY);
        if (hovered.isEmpty()) {
            return;
        }
        String name = hovered.get();
        // A type's or an item's icon names itself; a prefab's link alone opens a card.
        if (name.indexOf(HOVER_KEY_MARK) >= 0) {
            return;
        }
        VariablesTab.HoverCard card = cards.get(name);
        if (card != null) {
            drawCard(graphics, font, card, bounds, mouseX, mouseY, targets);
        } else if (cardsAsked.add(name)) {
            actions.request(WorkbenchOperations.CONSTANT_HOVER, variables.hoverRequest(name),
                    answered -> cards.put(name, variables.readHover(answered)));
        }
    }

    // The card's Used by lines stay collapsed, a row each.
    private static int usedByHeight(VariablesTab.HoverCard card) {
        int lines = (card.direct().isEmpty() ? 0 : 1) + (card.indirect().isEmpty() ? 0 : 1);
        return lines * (ICON_SIZE + HALF) + (lines > 1 ? HALF : 0);
    }

    private void drawCard(GuiGraphicsExtractor graphics, Font font, VariablesTab.HoverCard card,
                          GroupsPane.Bounds bounds, int mouseX, int mouseY, ClickTargets targets) {
        int textWidth = CARD_WIDTH - CARD_PADDING * HALF;
        List<ExpressionLines.Run> expression = ExpressionLines.shownRuns(card.expression(), icons::nameOf);
        List<ExpressionLines.Run> resolved = ExpressionLines.resolvedRuns(String.join(RESOLVED_SEPARATOR,
                card.lines()));
        int expressionHeight = ExpressionLines.height(font, expression, textWidth);
        int resolvedHeight = ExpressionLines.height(font, resolved, textWidth);
        int height = CARD_PADDING * HALF + LINE_HEIGHT + expressionHeight + resolvedHeight + usedByHeight(card);
        int left = Math.min(mouseX + CARD_OFFSET, bounds.right() - CARD_WIDTH);
        int top = Math.min(mouseY + CARD_OFFSET, bounds.bottom() - height);
        graphics.nextStratum();
        graphics.fill(left, top, left + CARD_WIDTH, top + height, CARD_COLOR);
        int x = left + CARD_PADDING;
        int y = top + CARD_PADDING;
        graphics.text(font, card.title(), x, y, WHITE);
        y += LINE_HEIGHT;
        ExpressionLines.draw(font, expression, x, y, textWidth, new ExpressionLines.Painter(graphics, icons, targets,
                GRAY, LINK, (link, linkX, linkY, linkWidth) -> { }));
        y += expressionHeight;
        ExpressionLines.draw(font, resolved, x, y, textWidth, new ExpressionLines.Painter(graphics, icons, targets,
                GREEN, LINK, (link, linkX, linkY, linkWidth) -> { }));
        y += resolvedHeight;
        drawUsedByLines(graphics, font, card.direct(), card.indirect(), x, y, textWidth, null, targets);
    }
}
