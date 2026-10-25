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
 * references-link-to-their-variable-and-hover-a-card). The state lives in {@link VariablesTab}.
 */
final class VariablesPane {
    private static final int LINE_HEIGHT = 11;
    private static final int BOX_HEIGHT = 16;
    private static final int BLOCK_GAP = 8;
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
    private static final String REFERENCE_MARK = "$";
    private static final String TAG_PREFIX = "#";
    private static final String RENAME = "[Rename]";
    private static final String DELETE = "[Delete]";
    private static final String CANCEL = "[Cancel]";
    private static final String USED_BY = "Used by ";
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
    private final Map<String, Integer> blockTops = new HashMap<>();
    private @Nullable EditBox expression;
    private @Nullable EditBox rename;
    private @Nullable String renaming;
    private @Nullable String deleting;
    private @Nullable String jumpPending;
    private boolean expressionHeldFocus;
    private boolean loadingBox;
    private boolean visible;
    private int scroll;

    VariablesPane(VariablesTab variables, ManagerDraft draft, ItemIcons icons, ManagerActions actions) {
        this.variables = variables;
        this.draft = draft;
        this.icons = icons;
        this.actions = actions;
    }

    /**
     * Stands the pane's boxes among the screen's widgets, keeping what they held across a resize.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        EditBox previous = expression;
        expression = actions.addWidget(new EditBox(font, 0, 0, NAME_WIDTH, BOX_HEIGHT, Component.literal(NO_TEXT)));
        expression.setMaxLength(Short.MAX_VALUE);
        if (previous != null) {
            expression.setValue(previous.getValue());
        }
        expression.setResponder(typed -> {
            if (!loadingBox) {
                variables.editing().ifPresent(name -> variables.edit(name, typed));
            }
        });
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
        if (expression != null) {
            expression.visible = shown && deleting == null && variables.editing().isPresent();
        }
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
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * SCROLL_STEP);
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
        if (expression != null && expression.isFocused() && (isEnter(key) || key == GLFW.GLFW_KEY_ESCAPE)) {
            expression.setFocused(false);
            return true;
        }
        return false;
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets,
                 int mouseX, int mouseY) {
        swapBackOnBlur();
        List<VariablesTab.ConstantBlock> blocks = variables.blocks();
        if (blocks.isEmpty()) {
            graphics.text(font, NO_CONSTANTS, bounds.left(), bounds.top(), GRAY);
            return;
        }
        applyPendingJump();
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        boolean editShown = false;
        boolean renameShown = false;
        int y = bounds.top() - scroll;
        for (VariablesTab.ConstantBlock block : blocks) {
            blockTops.put(block.name(), y + scroll - bounds.top());
            renameShown |= drawHead(graphics, font, block, bounds, y, targets);
            y += LINE_HEIGHT + HALF;
            boolean editingThis = variables.editing().filter(block.name()::equals).isPresent();
            editShown |= editingThis && placeExpression(bounds, y);
            y += editingThis ? BOX_HEIGHT : drawReadView(graphics, font, block, bounds, y, targets);
            y += drawUsedBy(graphics, font, block.usedBy(), bounds.left(), y + HALF) + BLOCK_GAP;
        }
        scroll = Math.min(scroll, Math.max(0, y + scroll - bounds.bottom()));
        targets.unclip();
        graphics.disableScissor();
        showBoxes(editShown, renameShown);
        drawDeleteDialog(graphics, font, bounds, targets);
        drawHoverCard(graphics, font, bounds, targets, mouseX, mouseY);
    }

    // Answers whether the edit box stands wholly inside the pane, where it may show.
    private boolean placeExpression(GroupsPane.Bounds bounds, int y) {
        if (expression == null) {
            return false;
        }
        expression.setPosition(bounds.left(), y);
        expression.setWidth(bounds.right() - bounds.left());
        return y >= bounds.top() && y + BOX_HEIGHT <= bounds.bottom();
    }

    private void showBoxes(boolean editShown, boolean renameShown) {
        boolean open = visible && deleting == null;
        if (expression != null) {
            expression.visible = open && editShown;
        }
        if (rename != null) {
            rename.visible = open && renameShown;
        }
    }

    private void swapBackOnBlur() {
        if (expression == null || variables.editing().isEmpty()) {
            expressionHeldFocus = false;
            return;
        }
        if (expression.isFocused()) {
            expressionHeldFocus = true;
        } else if (expressionHeldFocus) {
            expressionHeldFocus = false;
            variables.stopEditing();
            show(visible);
            actions.draftChanged();
        }
    }

    private void applyPendingJump() {
        if (jumpPending != null && blockTops.containsKey(jumpPending)) {
            scroll = Math.max(0, blockTops.get(jumpPending) - LINE_HEIGHT);
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
            graphics.text(font, REFERENCE_MARK + block.name(), x, y, WHITE);
        }
        int kindX = x + Math.max(NAME_WIDTH, font.width(REFERENCE_MARK + block.name())) + KIND_GAP;
        graphics.text(font, block.kind(), kindX, y, VariablesTab.TREE_KIND.equals(block.kind()) ? TREE_COLOR : GRAY);
        int actionsWidth = font.width(RENAME) + ACTION_GAP + font.width(DELETE);
        int resolvedX = kindX + font.width(block.kind()) + KIND_GAP;
        int resolvedWidth = bounds.right() - actionsWidth - ACTION_GAP - resolvedX;
        boolean failed = !block.error().isEmpty();
        graphics.text(font, font.plainSubstrByWidth(failed ? block.error() : block.resolved(), resolvedWidth),
                resolvedX, y, failed ? RED : GREEN);
        int renameX = bounds.right() - actionsWidth;
        graphics.text(font, RENAME, renameX, y, LINK);
        targets.add(renameX, y, font.width(RENAME), LINE_HEIGHT, () -> startRename(block.name()));
        int deleteX = renameX + font.width(RENAME) + ACTION_GAP;
        graphics.text(font, DELETE, deleteX, y, LINK);
        targets.add(deleteX, y, font.width(DELETE), LINE_HEIGHT, () -> askDelete(block.name()));
        return renamingThis;
    }

    // Answers the height the read view took.
    private int drawReadView(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                             GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int width = bounds.right() - bounds.left();
        String text = block.expression().isEmpty() ? NO_EXPRESSION : block.expression();
        int lines = ExpressionLines.measure(font, ExpressionReferences.nodes(text), width);
        int height = lines * LINE_HEIGHT + HALF;
        graphics.fill(bounds.left(), y, bounds.right(), y + height, READ_VIEW_COLOR);
        targets.add(bounds.left(), y, width, height, () -> startEditing(block.name()));
        ExpressionLines.draw(graphics, font, ExpressionReferences.nodes(text), bounds.left(), y + 1, width,
                block.error().isEmpty() ? WHITE : RED, LINK, (name, linkX, linkY, linkWidth) ->
                        targets.add(linkX, linkY, linkWidth, LINE_HEIGHT, () -> actions.openVariable(name), name));
        return height;
    }

    // Answers the height the line took, none where the constant reaches no item.
    private int drawUsedBy(GuiGraphicsExtractor graphics, Font font, List<String> usedBy, int x, int y) {
        if (usedBy.isEmpty()) {
            return 0;
        }
        graphics.text(font, USED_BY, x, y + TEXT_NUDGE, GRAY);
        int itemX = x + font.width(USED_BY);
        for (String item : usedBy) {
            graphics.item(icons.of(item), itemX, y);
            itemX += ICON_SIZE + ICON_GAP;
        }
        return ICON_SIZE + HALF;
    }

    private void startEditing(String name) {
        boolean switching = variables.editing().filter(name::equals).isEmpty() && variables.editing().isPresent();
        variables.startEditing(name);
        if (expression != null) {
            String shown = variables.blocks().stream().filter(block -> block.name().equals(name))
                    .map(VariablesTab.ConstantBlock::expression).findFirst().orElse(NO_TEXT);
            loadingBox = true;
            expression.setValue(shown);
            loadingBox = false;
            expression.visible = visible;
            actions.focus(expression);
        }
        expressionHeldFocus = false;
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
        graphics.text(font, DELETE_TITLE_START + REFERENCE_MARK + name + DELETE_TITLE_END, left + CARD_PADDING,
                top + CARD_PADDING, WHITE);
        drawCascade(graphics, font, name, dialog, targets);
        drawDialogButtons(graphics, font, name, dialog, targets);
    }

    private void drawCascade(GuiGraphicsExtractor graphics, Font font, String name, GroupsPane.Bounds dialog,
                             ClickTargets targets) {
        int x = dialog.left() + CARD_PADDING;
        int y = dialog.top() + CARD_PADDING + LINE_HEIGHT + CARD_PADDING;
        int readerX = x;
        for (VariablesTab.Reader reader : variables.deletionCascade(name)) {
            readerX += drawReader(graphics, font, reader, readerX, y, targets) + READER_GAP;
            if (readerX > dialog.right() - CARD_PADDING - ICON_SIZE) {
                readerX = x;
                y += ICON_SIZE + HALF;
            }
            if (y > dialog.bottom() - LINE_HEIGHT * HALF - CARD_PADDING) {
                return;
            }
        }
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
            String label = REFERENCE_MARK + reader.id();
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
        VariablesTab.HoverCard card = cards.get(name);
        if (card != null) {
            drawCard(graphics, font, card, bounds, mouseX, mouseY);
        } else if (cardsAsked.add(name)) {
            actions.request(WorkbenchOperations.CONSTANT_HOVER, variables.hoverRequest(name),
                    answered -> cards.put(name, variables.readHover(answered)));
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, Font font, VariablesTab.HoverCard card,
                          GroupsPane.Bounds bounds, int mouseX, int mouseY) {
        int textWidth = CARD_WIDTH - CARD_PADDING * HALF;
        int expressionLines = ExpressionLines.measure(font, ExpressionReferences.nodes(card.expression()), textWidth);
        int height = CARD_PADDING * HALF + LINE_HEIGHT * (1 + expressionLines + card.lines().size())
                + (card.usedBy().isEmpty() ? 0 : ICON_SIZE + HALF);
        int left = Math.min(mouseX + CARD_OFFSET, bounds.right() - CARD_WIDTH);
        int top = Math.min(mouseY + CARD_OFFSET, bounds.bottom() - height);
        graphics.nextStratum();
        graphics.fill(left, top, left + CARD_WIDTH, top + height, CARD_COLOR);
        int x = left + CARD_PADDING;
        int y = top + CARD_PADDING;
        graphics.text(font, card.title(), x, y, WHITE);
        y += LINE_HEIGHT;
        ExpressionLines.draw(graphics, font, ExpressionReferences.nodes(card.expression()), x, y, textWidth, GRAY,
                LINK, (link, linkX, linkY, linkWidth) -> { });
        y += expressionLines * LINE_HEIGHT;
        for (String line : card.lines()) {
            graphics.text(font, font.plainSubstrByWidth(line, textWidth), x, y, GREEN);
            y += LINE_HEIGHT;
        }
        drawUsedBy(graphics, font, card.usedBy(), x, y);
    }
}
