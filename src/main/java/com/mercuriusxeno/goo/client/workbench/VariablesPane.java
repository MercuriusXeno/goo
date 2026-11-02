package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the Variables tab: each constant's block with its expression, what it resolves to or its error, and the
 * items it cascades into; a plain left click on an expression, a $name link in it included, swaps in the edit box and
 * a blur swaps it back; a shift + left click on a $name link opens its variable, and the link hovers its card; rename
 * edits the name in place and delete confirms the readers it removes, as index.html's variables section does
 * (decisions goo-workbench-opens-the-in-game-manager-screen, references-link-to-their-variable-and-hover-a-card,
 * shift-left-click-follows-plain-left-modifies). The blocks and the delete confirm's readers each draw a
 * {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). A search box above the blocks keeps those whose name
 * holds its text (decision prefabs-tab-searches-its-list-by-name). The state lives in {@link VariablesTab}.
 */
final class VariablesPane {
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    // Decision prefab-lines-tighten-their-padding: a pixel between blocks, the head itself the 18px row the other
    // tabs keep (decision sprites-stay-16px-and-rows-tighten-to-18px); the head's pad keeps its buttons clear of the
    // separator above.
    private static final int BLOCK_GAP = 1;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int NAME_WIDTH = 140;
    private static final int SEARCH_WIDTH = 180;
    private static final int SEARCH_GAP = 3;
    private static final int KIND_GAP = 8;
    private static final int VALUE_GAP = 4;
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
    // A block opens on its separator's row, then a gap, then whatever of its head reaches highest above the head's
    // text, so the rule stands in its own row clear of both blocks.
    // decision prefabs-buttons-take-padding-above
    private static final int SEPARATOR_ROW = 1;
    /** The height of a head line holding no used-by run: its text and its buttons' border below it. */
    private static final int BARE_HEAD_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT + ButtonPainter.TEXT_NUDGE;
    /**
     * A number prefab used nowhere, from its top to the next one's: its head holding its value, its read view's line
     * and the gaps.
     */
    static final int UNUSED_PREFAB_PITCH = SEPARATOR_ROW + BLOCK_GAP + TEXT_NUDGE + BARE_HEAD_HEIGHT
            + ExpressionLines.TEXT_LINE_HEIGHT + 2 + BLOCK_GAP;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int LINK = ButtonPainter.LINK;
    private static final int CARD_COLOR = 0xF0181818;
    private static final int DIM_COLOR = 0xA0000000;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final String RESOLVED_SEPARATOR = ", ";
    private static final char HOVER_KEY_MARK = ':';
    private static final String TAG_PREFIX = "#";
    private static final String EQUALS = "=";
    private static final String VALUE_LABEL = "Value:";
    private static final String AMOUNT_OPEN = "(";
    private static final String AMOUNT_CLOSE = ")";
    private static final String RENAME = "✎";
    private static final String DELETE = "✗";
    private static final String CONFIRM_DELETE = "Delete";
    // The operator's ruling: the pencil's tooltip reads Rename and the x's Delete, one word each.
    private static final String RENAME_HINT = "Rename";
    private static final String DELETE_HINT = "Delete";
    private static final int PENCIL_GAP = 3;
    private static final String CANCEL = "Cancel";
    private static final String USED_BY = "Used by ";
    private static final String USED_BY_KEY = ":usedBy";
    private static final String DELETE_TITLE_START = "Delete ";
    private static final String DELETE_TITLE_END = " and what reads it";
    private static final String NO_CONSTANTS = "The file names no constants.";
    private static final String SEARCH_HINT = "Search prefabs";
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
    private final ExpandedLists expandedUsedBy = new ExpandedLists();
    private final ScrollBar cascadeBar = new ScrollBar(1);
    private final Map<String, Integer> blockTops = new HashMap<>();
    private final Map<String, Integer> blockHeights = new HashMap<>();
    private final ExpressionBuilderPane builder;
    private @Nullable String building;
    private @Nullable EditBox search;
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
        standSearch(font);
        EditBox previousRename = rename;
        rename = actions.addWidget(new EditBox(font, 0, 0, NAME_WIDTH, BOX_HEIGHT, Component.literal(NO_TEXT)));
        if (previousRename != null) {
            rename.setValue(previousRename.getValue());
        }
        show(visible);
    }

    // A change to the text returns the list to its top, the bar measuring the filtered blocks from the next frame.
    // decision prefabs-tab-searches-its-list-by-name
    // decision every-tab-draws-a-scroll-bar
    private void standSearch(Font font) {
        String searched = search == null ? NO_TEXT : search.getValue();
        EditBox box = actions.addWidget(new EditBox(font, 0, 0, SEARCH_WIDTH, BOX_HEIGHT,
                Component.literal(SEARCH_HINT)));
        box.setHint(Component.literal(SEARCH_HINT));
        box.setValue(searched);
        box.setResponder(text -> {
            variables.search(text);
            bar.reset();
        });
        search = box;
    }

    @Nullable EditBox searchBox() {
        return search;
    }

    ScrollBar listBar() {
        return bar;
    }

    /**
     * @param shown whether the Variables tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        builder.show(shown && deleting == null);
        showBoxes(renaming != null);
    }

    /**
     * Evaluates every constant of the draft afresh; the hover cards read the new draft from now.
     */
    void refresh() {
        cards.clear();
        cardsAsked.clear();
        actions.request(WorkbenchOperations.EVALUATE_CONSTANTS, draft.changeSetText(), variables::readEvaluated);
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
        if (builder.scrollList(amount)) {
            return;
        }
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
                stopRenaming();
                return true;
            }
        }
        return builder.keyPressed(key);
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    /**
     * The read view's runs with each evaluated amount in parentheses after what it evaluates: a scalar's after the
     * expression, a typed prefab's after each type row's terms, a failed evaluation's error after the expression
     * (decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount).
     *
     * @param runs     the expression's runs
     * @param resolved what the prefab resolves to: "type amount" pairs joined by commas, or a scalar
     * @param error    why it failed to resolve, empty where it resolved
     * @return the runs with the amounts placed
     */
    static List<ExpressionLines.Run> amountedRuns(List<ExpressionLines.Run> runs, String resolved, String error) {
        Map<String, String> typed = typedAmounts(resolved);
        List<ExpressionLines.Run> amounted = new java.util.ArrayList<>();
        String lastRow = amountTypeRows(runs, typed, amounted);
        if (error.isEmpty()) {
            addAmount(amounted, lastRow == null ? resolved : typed.get(lastRow));
        } else {
            amounted.add(new ExpressionLines.Run(ExpressionLines.Kind.ERROR, error, null));
        }
        return amounted;
    }

    // Copies the runs, each type row's amount before the break that ends it; answers the last row's type, null where
    // the value holds no type rows.
    private static @Nullable String amountTypeRows(List<ExpressionLines.Run> runs, Map<String, String> typed,
                                                   List<ExpressionLines.Run> amounted) {
        String rowType = null;
        for (int index = 0; index < runs.size(); index++) {
            ExpressionLines.Run run = runs.get(index);
            if (run.kind() == ExpressionLines.Kind.BREAK && rowType != null) {
                addAmount(amounted, typed.get(rowType));
            }
            amounted.add(run);
            if (startsTypeRow(runs, index)) {
                rowType = run.ref();
            }
        }
        return rowType;
    }

    // A type row opens on its type, then =, then the break before its terms.
    private static boolean startsTypeRow(List<ExpressionLines.Run> runs, int index) {
        int equals = index + 1;
        int terms = equals + 1;
        return terms < runs.size() && runs.get(index).kind() == ExpressionLines.Kind.TYPE
                && EQUALS.equals(runs.get(equals).text())
                && runs.get(terms).kind() == ExpressionLines.Kind.INDENTED_BREAK;
    }

    private static void addAmount(List<ExpressionLines.Run> runs, @Nullable String amount) {
        if (amount != null && !amount.isEmpty()) {
            runs.addAll(ExpressionLines.textRuns(AMOUNT_OPEN + amount + AMOUNT_CLOSE));
        }
    }

    private static Map<String, String> typedAmounts(String resolved) {
        Map<String, String> amounts = new HashMap<>();
        for (String part : resolved.split(RESOLVED_SEPARATOR)) {
            int space = part.indexOf(' ');
            if (space > 0) {
                amounts.put(part.substring(0, space), part.substring(space + 1));
            }
        }
        return amounts;
    }

    // A block's name line with its Used by run, then its value line or builder; answers the height it took with its
    // gap. Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount.
    private int drawBlock(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                          GroupsPane.Bounds bounds, int top, ClickTargets targets) {
        blockTops.put(block.name(), top + bar.offset() - bounds.top());
        int headY = top + SEPARATOR_ROW + BLOCK_GAP + TEXT_NUDGE;
        int y = headY + drawHead(graphics, font, block, bounds, headY, targets);
        boolean editingThis = variables.editing().filter(block.name()::equals).isPresent();
        y += editingThis ? drawBuilder(graphics, font, block, bounds, y, targets)
                : drawValueLine(graphics, font, block, bounds, y, targets);
        y += BLOCK_GAP;
        blockHeights.put(block.name(), y - top);
        return y - top;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds view, ClickTargets targets,
                 int mouseX, int mouseY) {
        int listTop = placeSearch(view);
        if (variables.blocks().isEmpty()) {
            graphics.text(font, NO_CONSTANTS, view.left(), listTop, GRAY);
            return;
        }
        List<VariablesTab.ConstantBlock> blocks = variables.listed();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(view.left(), listTop,
                view.right() - ScrollBar.WIDTH - ScrollBar.GAP, view.bottom());
        int end = drawBlocks(graphics, font, blocks, bounds, targets);
        bar.measureAndDraw(graphics, targets, end, view.right(), bounds.top(), view.bottom());
        applyPendingJump();
        showBoxes(rename != null && blocks.stream().anyMatch(block -> block.name().equals(renaming)));
        builder.drawOverlays(graphics, font, targets);
        drawDeleteDialog(graphics, font, view, targets);
        if (!builder.listOpen()) {
            drawHoverCard(graphics, font, view, targets, mouseX, mouseY);
        }
    }

    // The builder over the block being edited, each change written to the draft and done evaluating it afresh
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

    // The listed blocks clipped to the list's bounds; answers the y the last one ended at.
    private int drawBlocks(GuiGraphicsExtractor graphics, Font font, List<VariablesTab.ConstantBlock> blocks,
                           GroupsPane.Bounds bounds, ClickTargets targets) {
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        int y = bounds.top() - bar.offset();
        for (int index = 0; index < blocks.size(); index++) {
            RowLayout.drawSeparatorAbove(graphics, index, y + SEPARATOR_ROW, bounds.left(), bounds.right());
            y += drawBlock(graphics, font, blocks.get(index), bounds, y, targets);
        }
        targets.unclip();
        graphics.disableScissor();
        return y;
    }

    // Answers the list's top, below the box.
    private int placeSearch(GroupsPane.Bounds view) {
        if (search != null) {
            search.setPosition(view.left(), view.top());
        }
        return view.top() + BOX_HEIGHT + SEARCH_GAP;
    }

    private void showBoxes(boolean renameShown) {
        boolean open = visible && deleting == null;
        if (search != null) {
            search.visible = open;
        }
        if (rename != null) {
            rename.visible = open && renameShown;
        }
    }

    // Once the jumped block has drawn, editing, and the bar has measured this frame, centers the block in the list.
    // Decision shift-follow-centers-the-target-in-its-scrolling-view.
    private void applyPendingJump() {
        if (jumpPending != null && blockTops.containsKey(jumpPending)) {
            bar.center(blockTops.get(jumpPending), blockHeights.get(jumpPending));
            jumpPending = null;
        }
    }

    // Name [pencil], padding, Used by with its run, then the x at the right end; the rename box stands in place of the
    // name while the block is renamed. Answers how far the line reaches below the head's text top.
    // Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount.
    private int drawHead(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                         GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        boolean renamingThis = rename != null && block.name().equals(renaming);
        int pencilWidth = ButtonPainter.width(font, RENAME);
        int nameWidth = font.width(block.name()) + PENCIL_GAP + pencilWidth;
        if (renamingThis) {
            drawRenameBox(graphics, font, rename, block.name(), x, y, targets);
            nameWidth = NAME_WIDTH;
        } else {
            ButtonPainter.drawEntryName(graphics, font, block.name(), x, y, isMarked(block.name()));
            // The operator's ruling: rename is a pencil just past the name, delete a red x at the row's right end.
            ButtonPainter.draw(graphics, font, RENAME, x + font.width(block.name()) + PENCIL_GAP,
                    y - ButtonPainter.TEXT_NUDGE, ButtonPainter.HEIGHT, targets, () -> startRename(block.name()),
                    BuilderOverlays.hintKey(RENAME_HINT));
        }
        int usedByX = x + nameWidth + KIND_GAP;
        int usedByWidth = bounds.right() - ButtonPainter.width(font, DELETE) - ACTION_GAP - usedByX;
        int usedBy = drawUsedByLines(graphics, font, block.direct(), block.links(), usedByX, y - TEXT_NUDGE,
                Math.max(1, usedByWidth), block.name(), targets);
        drawRemoval(graphics, font, block.name(), bounds.right(), y, targets);
        return Math.max(BARE_HEAD_HEIGHT, usedBy - TEXT_NUDGE);
    }

    // Value: one indent in, then the read view inline with each evaluated amount in parentheses; answers its height.
    // Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount.
    private int drawValueLine(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                              GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int labelX = bounds.left() + ControlFlow.INDENT;
        int viewX = labelX + font.width(VALUE_LABEL) + VALUE_GAP;
        int height = drawReadView(graphics, font, block, new GroupsPane.Bounds(viewX, bounds.top(), bounds.right(),
                bounds.bottom()), y, targets);
        graphics.text(font, VALUE_LABEL, labelX, y + 1 + ExpressionLines.textOffset(font, readRuns(block)), GRAY);
        return height;
    }

    private List<ExpressionLines.Run> readRuns(VariablesTab.ConstantBlock block) {
        String text = block.expression().isEmpty() ? NO_EXPRESSION : block.expression();
        return amountedRuns(ExpressionLines.shownRuns(text, icons::nameOf), block.resolved(), block.error());
    }

    // The box shrinks to leave the name's room to its ✓ and ↺, which run what Enter and Escape run.
    // Decision open-name-box-takes-a-done-checkmark.
    // Decision open-name-box-takes-a-gold-arrow-that-cancels.
    private void drawRenameBox(GuiGraphicsExtractor graphics, Font font, EditBox box, String name, int x, int y,
                               ClickTargets targets) {
        int boxWidth = NAME_WIDTH - NameBoxButtons.width(font);
        box.setPosition(x, y - HALF);
        box.setWidth(boxWidth);
        NameBoxButtons.draw(graphics, font, x + boxWidth, y - ButtonPainter.TEXT_NUDGE, ButtonPainter.HEIGHT, targets,
                () -> submitRename(name, box.getValue().trim()), this::stopRenaming);
    }

    private void stopRenaming() {
        renaming = null;
        show(visible);
    }

    // The red x at the row's right end marks the prefab; a marked prefab's gold arrow in its place restores it.
    // Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
    private void drawRemoval(GuiGraphicsExtractor graphics, Font font, String name, int right, int y,
                             ClickTargets targets) {
        int top = y - ButtonPainter.TEXT_NUDGE;
        if (isMarked(name)) {
            ButtonPainter.drawRestore(graphics, font, right, top, ButtonPainter.HEIGHT, targets,
                    () -> draft.removals().restore(RemovalMarks.Tab.PREFABS, name));
            return;
        }
        ButtonPainter.drawColored(graphics, font, DELETE, RED, right - ButtonPainter.width(font, DELETE), top,
                ButtonPainter.HEIGHT, targets, () -> askDelete(name), BuilderOverlays.hintKey(DELETE_HINT));
    }

    // A plain left click on the read view starts editing its block, a ref in it included, and a shift + left click
    // on a ref opens the prefab it names; answers the height the read view took.
    // Decision shift-left-click-follows-plain-left-modifies.
    private int drawReadView(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                             GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int width = bounds.right() - bounds.left();
        List<ExpressionLines.Run> runs = readRuns(block);
        int height = ExpressionLines.height(font, runs, width) + HALF;
        graphics.fill(bounds.left(), y, bounds.right(), y + height, READ_VIEW_COLOR);
        targets.add(bounds.left(), y, width, height, () -> startEditing(block.name()));
        ExpressionLines.draw(font, runs, bounds.left(), y + 1, width, new ExpressionLines.Painter(graphics, icons,
                targets, block.error().isEmpty() ? WHITE : RED, LINK, (name, linkX, linkY, linkWidth) ->
                        targets.addFollow(linkX, linkY, linkWidth, LINE_HEIGHT, () -> actions.openVariable(name), name)));
        return height;
    }

    // Used by holds the items naming the constant themselves, then a link to each prefab and group reading it, on
    // one line drawn only where it holds one; the terminus items the cascade reaches draw nowhere. Answers the height
    // it took.
    // Decision used-by-lists-direct-items-and-links-to-prefabs-and-groups.
    private int drawUsedByLines(GuiGraphicsExtractor graphics, Font font, List<String> direct,
                                List<VariablesTab.Reader> links, int x, int y, int width, @Nullable String expandKey,
                                ClickTargets targets) {
        return drawUsedBy(graphics, font, new UsedBy(direct, links), x, y, width,
                expandKey == null ? null : expandKey + USED_BY_KEY, targets);
    }

    // The items' icons, then the links' chips, each a slot of its own width.
    private record UsedBy(List<String> items, List<VariablesTab.Reader> links) {
        int count() {
            return items.size() + links.size();
        }

        List<Integer> widths(Font font) {
            List<Integer> widths = new java.util.ArrayList<>();
            items.forEach(item -> widths.add(ICON_SIZE));
            links.forEach(link -> widths.add(ButtonPainter.width(font, linkLabel(link))));
            return widths;
        }
    }

    private static String linkLabel(VariablesTab.Reader link) {
        return CONSTANT_KIND.equals(link.kind()) ? link.id() : groupLabel(link.id());
    }

    private static String groupLabel(String id) {
        return id.startsWith(TAG_PREFIX) ? id : TAG_PREFIX + id;
    }

    // Answers the height the used-by slots took, none where nothing uses the constant. A line too narrow for them
    // stops at a ..+n button, which expands the block's line onto as many rows as it needs, and an expanded line
    // ends on Hide, which collapses it (decision expanded-used-by-lists-take-a-hide-button); a null expandKey draws
    // the count as plain text and never expands, as the hover card does.
    private int drawUsedBy(GuiGraphicsExtractor graphics, Font font, UsedBy usedBy, int x, int y, int width,
                           @Nullable String expandKey, ClickTargets targets) {
        if (usedBy.count() == 0) {
            return 0;
        }
        graphics.text(font, USED_BY, x, y + TEXT_NUDGE, GRAY);
        int itemsX = x + font.width(USED_BY);
        int itemsWidth = x + width - itemsX;
        List<Integer> widths = usedBy.widths(font);
        boolean expanded = expandKey != null && expandedUsedBy.expanded(expandKey);
        UsedByLine.Fit fit = expanded ? UsedByLine.expanded(usedBy.count())
                : UsedByLine.fitSlots(widths, ICON_GAP, itemsWidth,
                        hidden -> ButtonPainter.width(font, UsedByLine.moreLabel(hidden)));
        Optional<String> endLabel = UsedByLine.endLabel(fit, expanded);
        List<UsedByLine.Slot> slots = UsedByLine.flow(widths.subList(0, fit.shown()), ICON_GAP, itemsWidth,
                endLabel.map(label -> ButtonPainter.width(font, label)).orElse(0));
        for (int index = 0; index < fit.shown(); index++) {
            drawUsedBySlot(graphics, font, usedBy, index, itemsX + slots.get(index).x(),
                    y + slots.get(index).row() * (ICON_SIZE + HALF), targets);
        }
        UsedByLine.Slot end = slots.getLast();
        endLabel.ifPresent(label -> drawUsedByEnd(graphics, font, label, itemsX + end.x(),
                y + end.row() * (ICON_SIZE + HALF), expandKey, expanded, targets));
        return (UsedByLine.lastRow(slots, endLabel.isPresent()) + 1) * (ICON_SIZE + HALF);
    }

    // An item draws its icon; a prefab's chip follows to its block and hovers its value, a group's to its group, on
    // shift + left click (decision shift-left-click-follows-plain-left-modifies).
    private void drawUsedBySlot(GuiGraphicsExtractor graphics, Font font, UsedBy usedBy, int index, int x, int y,
                                ClickTargets targets) {
        if (index < usedBy.items().size()) {
            String item = usedBy.items().get(index);
            graphics.item(icons.of(item), x, y);
            ItemLinks.add(targets, actions, item, x, y);
            return;
        }
        VariablesTab.Reader link = usedBy.links().get(index - usedBy.items().size());
        if (CONSTANT_KIND.equals(link.kind())) {
            ButtonPainter.drawFollow(graphics, font, link.id(), x, y, ICON_SIZE, targets,
                    () -> actions.openVariable(link.id()), PrefabHovers.hoverKey(link.id()));
        } else {
            String label = groupLabel(link.id());
            ButtonPainter.drawFollow(graphics, font, label, x, y, ICON_SIZE, targets,
                    () -> actions.openGroup(label.substring(TAG_PREFIX.length())), null);
        }
    }

    private void drawUsedByEnd(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                               @Nullable String expandKey, boolean expanded, ClickTargets targets) {
        if (expandKey == null) {
            graphics.text(font, label, x, y + TEXT_NUDGE, GRAY);
        } else if (expanded) {
            ButtonPainter.draw(graphics, font, label, x, y, ICON_SIZE, targets, () -> expandedUsedBy.hide(expandKey));
        } else {
            ButtonPainter.draw(graphics, font, label, x, y, ICON_SIZE, targets, () -> expandedUsedBy.expand(expandKey));
        }
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
            draft.readEdited(renamed);
            if (variables.editing().filter(from::equals).isPresent()) {
                variables.startEditing(to);
            }
            actions.draftChanged();
        });
    }

    /**
     * Deletes every marked prefab, in mark order, each with its cascade, then runs the draft's write; a prefab an
     * earlier delete's cascade already removed drops its mark, and one whose delete is refused keeps it while the
     * status says why.
     * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
     *
     * @param then the draft's write
     */
    void applyRemovals(Runnable then) {
        deleteMarked(new ArrayDeque<>(draft.removals().marked(RemovalMarks.Tab.PREFABS)), then);
    }

    private void deleteMarked(Deque<String> pending, Runnable then) {
        String name = pending.poll();
        if (name == null) {
            then.run();
            return;
        }
        if (variables.blocks().stream().noneMatch(block -> block.name().equals(name))) {
            draft.removals().restore(RemovalMarks.Tab.PREFABS, name);
            deleteMarked(pending, then);
            return;
        }
        if (variables.editing().filter(name::equals).isPresent()) {
            variables.stopEditing();
        }
        actions.stream(WorkbenchOperations.DELETE_CONSTANT, variables.deleteRequest(name), line -> { }, left -> {
            draft.readEdited(left);
            draft.removals().restore(RemovalMarks.Tab.PREFABS, name);
            deleteMarked(pending, then);
        }, refused -> deleteMarked(pending, then));
    }

    private void askDelete(String name) {
        if (variables.deletionCascade(name).isEmpty()) {
            markForRemoval(name);
            return;
        }
        deleting = name;
        cascadeBar.reset();
        show(visible);
    }

    private void markForRemoval(String name) {
        deleting = null;
        draft.removals().mark(RemovalMarks.Tab.PREFABS, name);
        actions.markUnsaved();
        show(visible);
    }

    private boolean isMarked(String name) {
        return draft.removals().isMarked(RemovalMarks.Tab.PREFABS, name);
    }

    private void drawDeleteDialog(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                                  ClickTargets targets) {
        if (deleting == null) {
            return;
        }
        String name = deleting;
        graphics.nextStratum();
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), DIM_COLOR);
        targets.addShield(bounds.left(), bounds.top(), bounds.right() - bounds.left(), bounds.bottom() - bounds.top(),
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
            return ButtonPainter.width(font, reader.id());
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
        ButtonPainter.Box deleteBox = ButtonPainter.drawColored(graphics, font, CONFIRM_DELETE, RED, x,
                buttonY - ButtonPainter.PADDING, ButtonPainter.HEIGHT, targets, () -> markForRemoval(name), null);
        ButtonPainter.drawAtText(graphics, font, CANCEL, deleteBox.right() + ACTION_GAP * HALF, buttonY, targets, () -> {
            deleting = null;
            show(visible);
        });
    }

    // A constant reads as a $name button a shift + left click follows, a group or tag as its #name, and an item as its
    // icon; answers the width the reader took.
    // Decision shift-left-click-follows-plain-left-modifies.
    private int drawReader(GuiGraphicsExtractor graphics, Font font, VariablesTab.Reader reader, int x, int y,
                           ClickTargets targets) {
        if (CONSTANT_KIND.equals(reader.kind())) {
            return ButtonPainter.drawFollow(graphics, font, reader.id(), x, y, ICON_SIZE, targets,
                    () -> actions.openVariable(reader.id()), reader.id()).width();
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

    // The card's Used by line stays collapsed on one row.
    private static int usedByHeight(VariablesTab.HoverCard card) {
        return card.direct().isEmpty() && card.links().isEmpty() ? 0 : ICON_SIZE + HALF;
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
        drawUsedByLines(graphics, font, card.direct(), card.links(), x, y, textWidth, null, targets);
    }
}
