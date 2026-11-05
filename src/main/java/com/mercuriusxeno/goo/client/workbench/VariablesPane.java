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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

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
    private static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
    private static final int BOX_HEIGHT = 14;
    // Decision prefab-lines-tighten-their-padding: a pixel between blocks, the head itself the 18px row the other
    // tabs keep (decision sprites-stay-16px-and-rows-tighten-to-18px); the head's pad keeps its buttons clear of the
    // separator above.
    private static final int BLOCK_GAP = 1;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;
    private static final int NAME_WIDTH = 140;
    private static final int SEARCH_WIDTH = 180;
    private static final int KIND_GAP = 8;
    private static final int ACTION_GAP = 6;
    private static final int CARD_PADDING = 6;
    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 140;
    private static final int READER_GAP = 6;
    private static final int HALF = 2;
    // A block opens on its separator's row, then its head's 18px row, its text and its buttons centered on it, so the
    // rule stands in its own row clear of both blocks.
    // decision prefabs-buttons-take-padding-above
    private static final int SEPARATOR_ROW = 1;
    // A used-by line's 16px icons center on the head's row.
    private static final int USED_BY_INSET = (RowLayout.SPRITE_ROW_HEIGHT - ICON_SIZE) / HALF;
    /**
     * A number prefab used nowhere, from its top to the next one's: its name row, its one-line value button and the
     * gap.
     */
    static final int UNUSED_PREFAB_PITCH = SEPARATOR_ROW + RowLayout.SPRITE_ROW_HEIGHT + ButtonPainter.HEIGHT
            + BLOCK_GAP;
    private static final String RESOLVED_SEPARATOR = ", ";
    private static final String VALUE_LABEL = "Value:";
    private static final String RENAME = "✎";
    private static final String DELETE = "✗";
    private static final String CONFIRM_DELETE = "Delete";
    // The operator's ruling: the pencil's tooltip reads Rename and the x's Delete, one word each.
    private static final String RENAME_HINT = "Rename";
    private static final String DELETE_HINT = "Delete";
    // Decision spacing-rules-are-one-set-across-every-pane: the pencil a button gap past the name.
    private static final String CANCEL = "Cancel";
    private static final String USED_BY = "Used by ";
    private static final String USED_BY_KEY = ":usedBy";
    private static final String DELETE_TITLE_START = "Delete ";
    private static final String DELETE_TITLE_END = " and what reads it";
    private static final String NO_CONSTANTS = "The file names no constants.";
    private static final String SEARCH_HINT = "Search prefabs";
    private static final String NEW_PREFAB = "+";
    private static final String NAME_HINT = "name, Enter";
    // A used-by item's kind, as the evaluation names it.
    private static final String ITEM_KIND = "item";
    private static final String NO_TEXT = "";

    private final VariablesTab variables;
    private final ManagerDraft draft;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private final ExpandedLists expandedUsedBy = new ExpandedLists();
    private final ScrollBar cascadeBar = new ScrollBar(1);
    private final ExpressionBuilderPane builder;
    private @Nullable String building;
    private @Nullable EditBox search;
    private @Nullable EditBox rename;
    private @Nullable String renaming;
    private @Nullable EditBox nameBox;
    private boolean naming;
    private @Nullable String deleting;
    private final Reveal revealing = new Reveal();
    // How tall the selected block drew last.
    private int selectedHeight;
    private final RevealGlow glow = new RevealGlow();
    private boolean visible;
    private final ScrollBar bar = new ScrollBar(ScrollBar.WHEEL_STEP);

    VariablesPane(VariablesTab variables, ManagerDraft draft, ItemIcons icons, ManagerActions actions,
                  ExpressionBuilderPane builder) {
        this.variables = variables;
        this.draft = draft;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
    }

    /**
     * @return the box naming a new prefab, null before init
     */
    @Nullable EditBox nameBox() {
        return nameBox;
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
        EditBox previousName = nameBox;
        nameBox = actions.addWidget(new EditBox(font, 0, 0, NAME_WIDTH, BOX_HEIGHT, Component.literal(NAME_HINT)));
        nameBox.setHint(Component.literal(NAME_HINT));
        if (previousName != null) {
            nameBox.setValue(previousName.getValue());
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
     * Evaluates every constant of the draft afresh; the prefab tooltips read the new evaluation from now.
     */
    void refresh() {
        actions.request(WorkbenchOperations.EVALUATE_CONSTANTS, draft.changeSetText(), variables::readEvaluated);
    }

    /**
     * Lands on a variable's block selected and read-only and scrolls it into view, as a $name link does: a builder
     * open anywhere closes and nothing tells the draft it changed, as a follow navigates and edits nothing (decision
     * every-follow-lands-selected-and-read-only).
     *
     * @param name the variable
     */
    void jumpTo(String name) {
        deleting = null;
        revealing.follow(name, () -> {
            if (search != null) {
                search.setValue(NO_TEXT);
            }
            variables.search(NO_TEXT);
        });
        variables.jumpTo(name);
        builder.close();
        building = null;
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
        return nameKey(key) || renameKey(key) || builder.keyPressed(key);
    }

    private boolean nameKey(int key) {
        EditBox named = nameBox;
        return naming && named != null && named.isFocused()
                && boxKey(key, () -> createPrefab(named.getValue()), this::stopNaming);
    }

    private boolean renameKey(int key) {
        EditBox renamed = rename;
        String from = renaming;
        return from != null && renamed != null && renamed.isFocused()
                && boxKey(key, () -> submitRename(from, renamed.getValue().trim()), this::stopRenaming);
    }

    // Enter runs the box's done and Escape its cancel; answers whether either ran.
    private static boolean boxKey(int key, Runnable done, Runnable cancel) {
        if (isEnter(key)) {
            done.run();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel.run();
            return true;
        }
        return false;
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    

    /**
     * The evaluated amount a read view draws above its expression: a typed prefab's amounts highest first, a
     * scalar's one amount, and none for a plain number, whose amount is the number itself, nor for a failed or
     * unanswered evaluation.
     * Decisions prefab-head-is-name-pencil-used-by-then-value-with-its-amount,
     * value-lists-order-highest-first-ties-by-type-key.
     *
     * @param runs  the expression's runs
     * @param block the prefab's block, carrying its kind, what it resolves to and its error
     * @return the amount line
     */
    static GooAmountGrid amountLine(List<ExpressionLines.Run> runs, VariablesTab.ConstantBlock block) {
        String resolved = block.resolved();
        if (!block.error().isEmpty() || resolved.isEmpty() || isPlainNumber(runs)) {
            return GooAmountGrid.NONE;
        }
        if (!VariablesTab.TREE_KIND.equals(block.kind())) {
            return GooAmountGrid.inRow(GooAmountGrid.reducedScalar(resolved));
        }
        Map<String, String> typed = typedAmounts(resolved);
        // Decision one-amount-grid-draws-every-amount-list: the line is the one amount grid, in its parens.
        // Decision row-amount-grid-fills-the-width-and-wraps-past-it: reduced lays it as a row's grid.
        return typed.isEmpty() ? GooAmountGrid.NONE : GooAmountGrid.reduced(AmountOrder.highestFirst(typed));
    }

    private static boolean isPlainNumber(List<ExpressionLines.Run> runs) {
        return runs.size() == 1 && runs.getFirst().kind() == ExpressionLines.Kind.NUMBER;
    }

    private static Map<String, String> typedAmounts(String resolved) {
        Map<String, String> amounts = new LinkedHashMap<>();
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
        // Decision every-follow-lands-selected-and-read-only: the block a follow landed on stands in the selected
        // fill, at the height it drew last, which the reveal's measuring pass lays before the first frame.
        boolean selected = variables.selected().filter(block.name()::equals).isPresent();
        if (selected && selectedHeight > 0) {
            graphics.fill(bounds.left(), top, bounds.right(), top + selectedHeight, Palette.SELECTED_FILL);
        }
        int headY = RowLayout.nameTop(font, top + SEPARATOR_ROW);
        int y = headY + drawHead(graphics, font, block, bounds, headY, targets);
        y += drawValueLine(graphics, font, block, bounds, y, targets);
        y += BLOCK_GAP;
        revealing.laid(block.name(), top + bar.offset() - bounds.top(), y - top, 0);
        if (selected) {
            selectedHeight = y - top;
        }
        return y - top;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds view, ClickTargets targets,
                 int mouseX, int mouseY) {
        int listTop = placeSearch(font, view);
        if (variables.blocks().isEmpty()) {
            graphics.text(font, NO_CONSTANTS, view.left(), listTop, Palette.MUTED);
            drawCreate(graphics, font, view, targets);
            return;
        }
        List<VariablesTab.ConstantBlock> blocks = variables.listed();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(view.left(), listTop,
                view.right() - ScrollBar.WIDTH - ScrollBar.GAP, view.bottom());
        // Decision one-reveal-primitive-centers-before-the-first-draw: a followed block stands centered and glowing
        // from the first frame drawn.
        revealing.centerBeforeDraw(graphics, scratch -> drawList(graphics, font, blocks, bounds, scratch),
                column -> bar, glow);
        drawList(graphics, font, blocks, bounds, targets);
        drawCreate(graphics, font, view, targets);
        showBoxes(rename != null && blocks.stream().anyMatch(block -> block.name().equals(renaming)));
        builder.drawOverlays(graphics, font, targets);
        drawDeleteDialog(graphics, font, view, targets);
    }

    // The builder over the block being edited, each change written to the draft and evaluated afresh, so the value
    // shown is the value the builder holds (decision expression-builder-composes-every-form-without-typing); answers
    // the height it took; the frame reserves the panel's overhang above it.
    private int drawBuilder(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                            GroupsPane.Bounds box, ClickTargets targets) {
        if (!builder.isOpen() || !block.name().equals(building)) {
            building = block.name();
            builder.openPrefab(block.name(), block.expression(), typed -> {
                variables.edit(block.name(), typed);
                actions.draftChanged();
            }, () -> {
                building = null;
                variables.stopEditing();
                actions.draftChanged();
            });
        }
        return builder.extract(graphics, font, box, box.top(), targets);
    }

    // The listed blocks and the list's bar, which stands right of the blocks' bounds.
    private void drawList(GuiGraphicsExtractor graphics, Font font, List<VariablesTab.ConstantBlock> blocks,
                          GroupsPane.Bounds bounds, ClickTargets targets) {
        int end = drawBlocks(graphics, font, blocks, bounds, targets);
        bar.measureAndDraw(graphics, targets, end, bounds.right() + ScrollBar.GAP + ScrollBar.WIDTH, bounds.top(),
                bounds.bottom());
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
        glow.draw(graphics, bounds.left(), bounds.top() - bar.offset(), bounds.right());
        targets.unclip();
        graphics.disableScissor();
        return y;
    }

    // Answers the list's top, below the box.
    private int placeSearch(Font font, GroupsPane.Bounds view) {
        SearchRow.Laid laid = searchRow(font, view);
        SearchRow.place(search, laid);
        return laid.listTop();
    }

    // Decision one-search-row-per-list-with-its-create-control-beside-it: the box stretches to the [+], or to the open
    // name box standing in its place, at the list's right end.
    private SearchRow.Laid searchRow(Font font, GroupsPane.Bounds view) {
        int controls = naming && nameBox != null ? NAME_WIDTH : ButtonPainter.width(font, NEW_PREFAB);
        return SearchRow.lay(view.left(), view.top(), view.right() - ScrollBar.WIDTH - ScrollBar.GAP, controls);
    }

    private void showBoxes(boolean renameShown) {
        boolean open = visible && deleting == null;
        showBox(search, open);
        showBox(rename, open && renameShown);
        showBox(nameBox, open && naming);
    }

    private static void showBox(@Nullable EditBox box, boolean shown) {
        if (box != null) {
            box.visible = shown;
        }
    }

    // A [+] right of the search box, which opens a name box in its place with a done check and a gold arrow that run
    // what Enter and Escape run, as the Mutators [+] does.
    // Decision goo-workbench-opens-the-in-game-manager-screen: the page's Variables pane added constants.
    // Decision mutators-and-iterators-each-take-a-create-control.
    // Decisions open-name-box-takes-a-done-checkmark, open-name-box-takes-a-gold-arrow-that-cancels.
    private void drawCreate(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds view, ClickTargets targets) {
        int x = searchRow(font, view).controlsLeft();
        EditBox box = nameBox;
        if (!naming || box == null) {
            ButtonPainter.draw(graphics, font, NEW_PREFAB, x, view.top(), SearchRow.HEIGHT, targets, this::startNaming,
                    null);
            return;
        }
        int boxWidth = NAME_WIDTH - NameBoxButtons.width(font);
        box.setPosition(x, view.top());
        box.setWidth(boxWidth);
        NameBoxButtons.draw(graphics, font, x + boxWidth, view.top(), ButtonPainter.HEIGHT, targets,
                () -> createPrefab(box.getValue()), this::stopNaming);
    }

    private void startNaming() {
        naming = true;
        if (nameBox != null) {
            nameBox.setValue(NO_TEXT);
            showBoxes(renaming != null);
            actions.focus(nameBox);
        }
    }

    private void stopNaming() {
        naming = false;
        showBoxes(renaming != null);
    }

    // A free name adds the prefab and opens its builder centered in the list, the jump clearing the search so the
    // list holds it; a blank, illegal or held name leaves the box open.
    // Decision create-reveals-the-new-row-like-a-follow.
    private void createPrefab(String typed) {
        Optional<String> created = variables.createPrefab(typed);
        if (created.isEmpty()) {
            return;
        }
        stopNaming();
        actions.draftChanged();
        jumpTo(created.get());
        startEditing(created.get());
    }


    // Name [pencil], padding, Used by with its run, then the x at the right end; the rename box stands in place of the
    // name while the block is renamed. Answers how far the line reaches below the head's text top.
    // Decisions prefab-head-is-name-pencil-used-by-then-value-with-its-amount,
    // hash-marks-groups-and-dollar-marks-prefabs-everywhere (the name reads after $),
    // one-name-primitive-cuts-and-hovers-the-whole-name-on-every-pane.
    private int drawHead(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                         GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        int x = bounds.left();
        boolean renamingThis = rename != null && block.name().equals(renaming);
        String marked = PrefabNames.marked(block.name());
        String shown = ButtonPainter.cut(font, marked, NAME_WIDTH);
        int nameWidth = font.width(shown) + ButtonPainter.PENCIL_GAP + ButtonPainter.width(font, RENAME);
        if (renamingThis) {
            drawRenameBox(graphics, font, rename, block.name(), x, y, targets);
            nameWidth = NAME_WIDTH;
        } else {
            ButtonPainter.drawEntryName(graphics, font, targets, ButtonPainter.EntryName.of(marked, x, y, NAME_WIDTH,
                    isMarked(block.name())));
            // The operator's ruling: rename is a pencil just past the name, delete a red x at the row's right end.
            ButtonPainter.draw(graphics, font, RENAME, x + font.width(shown) + ButtonPainter.PENCIL_GAP,
                    y - ButtonPainter.labelInset(font), ButtonPainter.HEIGHT, targets, () -> startRename(block.name()),
                    BuilderOverlays.hintKey(RENAME_HINT));
        }
        int rowTop = y - RowLayout.nameTop(font, 0);
        int usedByX = x + nameWidth + KIND_GAP;
        int usedByWidth = bounds.right() - ButtonPainter.width(font, DELETE) - ACTION_GAP - usedByX;
        int usedByTop = rowTop + USED_BY_INSET;
        int usedBy = drawUsedByLines(graphics, font, block.direct(), block.links(), usedByX, usedByTop,
                Math.max(1, usedByWidth), block.name(), targets);
        drawRemoval(graphics, font, block.name(), bounds.right(), y, targets);
        return Math.max(rowTop + RowLayout.SPRITE_ROW_HEIGHT, usedByTop + usedBy) - y;
    }

    // Value: one indent in, then the evaluated amount on its own line above the value's box, outside it, and the box
    // below holding the expression; with no amount line, Value: stands beside the box. A prefab with no expression
    // reads Value: Not explicitly set as the button itself. Answers its height.
    // Decision prefab-head-is-name-pencil-used-by-then-value-with-its-amount; the operator's rulings of 2026-11-04
    // stand Value: on the amount line and the amount line above the box.
    // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
    // Decisions one-builder-frame-on-every-pane, one-red-error-line-under-every-value: the read view and the builder
    // both lay in the frame, the label and the amount line kept above the builder and the error a red line under it.
    private int drawValueLine(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                              GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        boolean editing = variables.editing().filter(block.name()::equals).isPresent();
        List<ExpressionLines.Run> shown = block.expression().isBlank() ? List.of()
                : ExpressionLines.shownRuns(block.expression(), icons::nameOf);
        if (shown.isEmpty() && !editing) {
            return drawReadView(graphics, font, block, shown, new GroupsPane.Bounds(bounds.left() + ControlFlow.INDENT,
                    y, bounds.right(), bounds.bottom()), targets);
        }
        BuilderFrame.Row row = new BuilderFrame.Row(new GroupsPane.Bounds(bounds.left(), y, bounds.right(),
                bounds.bottom()), VALUE_LABEL, amountLine(shown, block), true);
        return BuilderFrame.draw(graphics, font, row, new BuilderFrame.Value(editing, shown, block.error(),
                box -> editing ? drawBuilder(graphics, font, block, box, targets)
                        : drawReadView(graphics, font, block, shown, box, targets)));
    }

    // The box shrinks to leave the name's room to its ✓ and ↺, which run what Enter and Escape run.
    // Decision open-name-box-takes-a-done-checkmark.
    // Decision open-name-box-takes-a-gold-arrow-that-cancels.
    private void drawRenameBox(GuiGraphicsExtractor graphics, Font font, EditBox box, String name, int x, int y,
                               ClickTargets targets) {
        int boxWidth = NAME_WIDTH - NameBoxButtons.width(font);
        box.setPosition(x, y - HALF);
        box.setWidth(boxWidth);
        NameBoxButtons.draw(graphics, font, x + boxWidth, y - ButtonPainter.labelInset(font),
                ButtonPainter.HEIGHT, targets,
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
        int top = y - ButtonPainter.labelInset(font);
        if (isMarked(name)) {
            ButtonPainter.drawRestore(graphics, font, right, top, ButtonPainter.HEIGHT, targets,
                    () -> draft.removals().restore(RemovalMarks.Tab.PREFABS, name));
            return;
        }
        ButtonPainter.drawColored(graphics, font, DELETE, Palette.DANGER, right - ButtonPainter.width(font, DELETE),
                top, ButtonPainter.HEIGHT, targets, () -> askDelete(name), BuilderOverlays.hintKey(DELETE_HINT));
    }

    // A plain left click on the read view's button, its pencil or a ref in it, starts editing its block, and a
    // shift + left click on a ref opens the prefab it names; answers the height the read view took.
    // Decision shift-left-click-follows-plain-left-modifies.
    // Decision read-mode-builder-is-a-button-with-a-pencil-segment.
    private int drawReadView(GuiGraphicsExtractor graphics, Font font, VariablesTab.ConstantBlock block,
                             List<ExpressionLines.Run> runs, GroupsPane.Bounds box, ClickTargets targets) {
        return ReadValueButton.draw(graphics, font, runs, new ReadValueButton.Place(box.left(), box.top(),
                box.right(), () -> startEditing(block.name())), readPainter(graphics, targets), targets);
    }

    // The read view's runs paint white, a $name among them a link; an error reads as the frame's red line under it.
    private ExpressionLines.Painter readPainter(GuiGraphicsExtractor graphics, ClickTargets targets) {
        // Decision value-hover-reads-the-same-editing-and-read-only: a ref keys the prefab's tooltip as the builder's.
        return ExpressionLines.Painter.linked(graphics, icons, targets, Palette.TEXT, actions);
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

        // Every user in slot order: the items as item users, then the links.
        VariablesTab.Reader user(int index) {
            return index < items.size() ? new VariablesTab.Reader(items.get(index), ITEM_KIND)
                    : links.get(index - items.size());
        }

        List<Integer> widths(Font font) {
            return IntStream.range(0, count()).mapToObj(index -> UsedByLine.userWidth(font, user(index))).toList();
        }
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
        graphics.text(font, USED_BY, x, Lettering.full(font).centeredTop(y, ICON_SIZE), Palette.MUTED);
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

    // Decision one-used-by-primitive-draws-the-row-line-and-the-dialog: each slot draws as the dialog draws it.
    private void drawUsedBySlot(GuiGraphicsExtractor graphics, Font font, UsedBy usedBy, int index, int x, int y,
                                ClickTargets targets) {
        UsedByLine.drawUser(new UsedByLine.Painter(graphics, font, icons, actions, targets), usedBy.user(index), x, y);
    }

    private void drawUsedByEnd(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                               @Nullable String expandKey, boolean expanded, ClickTargets targets) {
        if (expandKey == null) {
            graphics.text(font, label, x, Lettering.full(font).centeredTop(y, ICON_SIZE), Palette.MUTED);
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
        // Decision one-wheel-step-and-one-dialog-primitive: the whole screen dims, as the draft's confirm dims it.
        GroupsPane.Bounds dialog = Dialog.open(graphics, targets, DIALOG_WIDTH, DIALOG_HEIGHT);
        graphics.text(font, DELETE_TITLE_START + name + DELETE_TITLE_END, dialog.left() + CARD_PADDING,
                dialog.top() + CARD_PADDING, Palette.TEXT);
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
            int readerWidth = UsedByLine.userWidth(font, reader);
            if (readerX + readerWidth > right && readerX > x) {
                row++;
                readerX = x;
            }
            int shownRow = row - first;
            if (shownRow >= 0 && shownRow < rowsShown) {
                // Decision one-used-by-primitive-draws-the-row-line-and-the-dialog.
                UsedByLine.drawUser(new UsedByLine.Painter(graphics, font, icons, actions, targets), reader, readerX,
                        top + shownRow * rowHeight);
            }
            readerX += readerWidth + READER_GAP;
        }
        cascadeBar.measure(row + 1, rowsShown);
        cascadeBar.draw(graphics, targets, dialog.right() - CARD_PADDING, top, top + rowsShown * rowHeight);
    }

    private void drawDialogButtons(GuiGraphicsExtractor graphics, Font font, String name, GroupsPane.Bounds dialog,
                                   ClickTargets targets) {
        int x = dialog.left() + CARD_PADDING;
        int buttonY = dialog.bottom() - LINE_HEIGHT - CARD_PADDING;
        ButtonPainter.Box deleteBox = ButtonPainter.drawColored(graphics, font, CONFIRM_DELETE, Palette.DANGER, x,
                buttonY - ButtonPainter.PADDING, ButtonPainter.HEIGHT, targets, () -> markForRemoval(name), null);
        ButtonPainter.drawAtText(graphics, font, CANCEL, deleteBox.right() + ACTION_GAP * HALF, buttonY, targets, () -> {
            deleting = null;
            show(visible);
        });
    }
}
