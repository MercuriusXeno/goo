package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Draws the Mutators tab: each block's mutators (formulas, additives and stacks) and its iterators
 * (group assignments) under a heading each, every heading with a create control naming a fresh entry (decisions
 * group-assignments-read-as-iterators, mutators-and-iterators-each-take-a-create-control). Each entry draws the key, the form, the parser's verdict and Delete on
 * an entry's first line and its sentence of controls at full width below (decision mutator-rows-take-an-extra-line),
 * which build the entry with nothing typed but a number or a search (decision
 * expression-builder-composes-every-form-without-typing), and Delete drops the entry, as
 * index.html's processes section does (decision goo-workbench-opens-the-in-game-manager-screen). The rows scroll
 * under a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ProcessesTab}.
 */
final class ProcessesPane {
    private static final int ROW_HEIGHT = 13;
    private static final int MARK_WIDTH = 14;
    private static final int NAME_HEAD = 20;
    private static final int NAME_TAIL = 4;
    private static final String ELLIPSIS = "…";
    // The operator's ruling: rename is a pencil just past the name, delete a red x at the row's right end.
    private static final String RENAME = "✎";
    private static final String RENAME_HINT = "Rename";
    private static final String DELETE_HINT = "Delete";
    private static final int PENCIL_GAP = 3;
    // A button stands a pixel under the rule above its row and its label five under that.
    private static final int BUTTON_INSET = 1;
    private static final int HEAD_TEXT_NUDGE = 4;
    // The kind dropdown's line centers a button two pixels under the line's top.
    private static final int KIND_LINE_INSET = 2;
    private static final int HEAD_HEIGHT = BUTTON_INSET + ButtonPainter.HEIGHT + 1;
    private static final int ROW_GAP = 1;
    /**
     * A row whose sentence takes one line, from its top to the next row's
     * (decision mutator-delete-clears-a-thinner-rule-and-lines-tighten).
     */
    static final int ONE_LINE_ROW_PITCH = HEAD_HEIGHT + ControlFlow.LINE + ROW_GAP;
    private static final int GAP = 6;
    private static final int COLUMN_GAP = 12;
    // The mutators column takes three fifths of the pane, the iterators column the other two.
    // Decision mutators-side-takes-60-percent-of-the-pane.
    private static final int MUTATOR_FIFTHS = 3;
    private static final int FIFTHS = 5;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = ROW_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    /** An Invalid verdict's color. */
    static final int RED = 0xFFFF5555;
    /** A Valid verdict's color. */
    static final int GREEN = 0xFF80E080;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";
    private static final String REASON_GAP = ": ";
    private static final String NO_REASON = "";
    private static final int ROW_COLOR = 0x20FFFFFF;
    private static final String DELETE = "✗";
    // A section key starts with an underscore and holds no newline, so a newline parts it from the entry's key.
    private static final char MARK_SEPARATOR = '\n';
    private static final String MUTATORS = "Mutators";
    private static final String ITERATORS = "Iterators";
    // The operator's ruling: a [+] beside its heading plainly adds one of that heading's kind.
    private static final String NEW_ENTRY = "+";
    private static final String NAME_HINT = "name, Enter";
    private static final String NO_TEXT = "";
    // Mutators first, then iterators.
    private static final boolean[] KINDS = {false, true};
    /** How far a column heading's text stands below the columns' top. */
    static final int HEADING_NUDGE = 5;
    private static final int NAME_BOX_WIDTH = 140;
    private static final int NAME_BOX_HEIGHT = 16;
    private static final String SEARCH_HINT = "Search mutators";
    // Decision conversions-is-the-one-section-and-the-pane-name: the pane shows the one section, under no sub-tab.
    private static final String CONVERSIONS = "_conversions";
    private static final int SEARCH_WIDTH = 200;
    /** The row the search box takes above the columns. */
    static final int SEARCH_ROW = NAME_BOX_HEIGHT + 4;

    private final ProcessesTab processes;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final BuilderOverlays overlays;
    private final MutatorControls controls;
    // Decision mutators-left-iterators-right-each-with-its-own-scroll-bar: the mutators column's bar, then the
    // iterators column's.
    private final ScrollBar[] bars = {new ScrollBar(SCROLL_STEP), new ScrollBar(SCROLL_STEP)};
    private int columnsMiddle;
    private @Nullable String revealPending;
    private @Nullable RevealedRow revealedRow;
    private @Nullable EditBox nameBox;
    private @Nullable EditBox search;
    private @Nullable Naming naming;
    private boolean nameBoxPlaced;
    private boolean visible;

    // Where the row a follow revealed stood inside the list's content this frame.
    private record RevealedRow(boolean iterators, int top, int height) {
    }

    /**
     * @param processes   the Mutators tab's state
     * @param draft       the draft every tab edits
     * @param actions     the panes' line back to the screen
     * @param icons       the items' icons
     * @param treePrefabs the tree prefabs' names, which an additive adds
     */
    ProcessesPane(ProcessesTab processes, ManagerDraft draft, ManagerActions actions, ItemIcons icons,
                  Supplier<List<String>> treePrefabs) {
        this.processes = processes;
        this.draft = draft;
        this.actions = actions;
        this.overlays = new BuilderOverlays(actions, icons);
        this.controls = new MutatorControls(processes, overlays, actions, treePrefabs,
                () -> GooTypes.order().stream().map(GooTypes::id).toList(), actions::draftChanged);
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        nameBox = actions.addWidget(new EditBox(font, 0, 0, NAME_BOX_WIDTH, NAME_BOX_HEIGHT,
                Component.literal(NAME_HINT)));
        nameBox.setHint(Component.literal(NAME_HINT));
        standSearch(font);
        overlays.init(font);
        show(visible);
    }

    // A resize stands the box afresh with the text it held (decision mutators-tab-renames-and-searches-by-name).
    private void standSearch(Font font) {
        String searched = search == null ? NO_TEXT : search.getValue();
        EditBox box = actions.addWidget(new EditBox(font, 0, 0, SEARCH_WIDTH, NAME_BOX_HEIGHT,
                Component.literal(SEARCH_HINT)));
        box.setHint(Component.literal(SEARCH_HINT));
        box.setValue(searched);
        box.setResponder(text -> {
            processes.search(text);
            for (ScrollBar each : bars) {
                each.reset();
            }
        });
        search = box;
    }

    /**
     * @param shown whether the Mutators tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        overlays.show(shown);
        if (nameBox != null) {
            nameBox.visible = shown && naming != null;
        }
        if (search != null) {
            search.visible = shown;
        }
    }

    /**
     * Describes the draft's processes afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.PROCESSES, draft.changeSetText(), processes::readDescribed);
    }

    /**
     * @param mouseX the pointer's x, which picks the column the wheel scrolls
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double mouseX, double amount) {
        if (overlays.scrollList(amount)) {
            return;
        }
        columnBar(mouseX >= columnsMiddle).wheel(amount);
    }

    /**
     * @param iterators whether the iterators column's bar is meant, not the mutators column's
     * @return that column's scroll bar
     */
    ScrollBar columnBar(boolean iterators) {
        return bars[iterators ? 1 : 0];
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the controls took the key: Enter commits a number box or the name box, and Escape closes a list
     *     or the name box
     */
    boolean keyPressed(int key) {
        if (nameBox != null && naming != null && nameBox.isFocused()) {
            return nameKey(nameBox, key);
        }
        return overlays.keyPressed(key);
    }

    private boolean nameKey(EditBox box, int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            create(box.getValue());
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            stopNaming();
            return true;
        }
        return false;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds pane, ClickTargets targets) {
        overlays.commitOnBlur();
        // Decision mutators-tab-renames-and-searches-by-name: the search box stands above the scrolling blocks.
        if (search != null) {
            search.setPosition(pane.left(), pane.top());
        }
        nameBoxPlaced = false;
        columnsMiddle = pane.left() + (pane.right() - pane.left()) * MUTATOR_FIFTHS / FIFTHS;
        processes.sections().stream().filter(section -> section.section().equals(CONVERSIONS)).findFirst()
                .ifPresent(section -> drawColumns(graphics, font, section, new GroupsPane.Bounds(pane.left(),
                        pane.top() + SEARCH_ROW, pane.right(), pane.bottom()), targets));
        if (nameBox != null) {
            nameBox.visible = visible && nameBoxPlaced;
        }
        centerRevealedRow();
        overlays.draw(graphics, font, targets);
    }

    // The section's mutators in the left column and its iterators in the right, from the area's top.
    // Decision mutators-pane-draws-no-entry-count.
    private void drawColumns(GuiGraphicsExtractor graphics, Font font, ProcessesTab.ProcessSection section,
                             GroupsPane.Bounds area, ClickTargets targets) {
        int columnsTop = area.top();
        for (boolean iterators : KINDS) {
            GroupsPane.Bounds column = iterators
                    ? new GroupsPane.Bounds(columnsMiddle + COLUMN_GAP / HALF, columnsTop, area.right(), area.bottom())
                    : new GroupsPane.Bounds(area.left(), columnsTop, columnsMiddle - COLUMN_GAP / HALF, area.bottom());
            drawColumn(new Frame(graphics, font, column, targets), section, iterators);
        }
    }

    // A column: its heading and [+] standing still, then its rows scrolling under its own bar.
    private void drawColumn(Frame frame, ProcessesTab.ProcessSection section, boolean iterators) {
        GroupsPane.Bounds column = frame.rows();
        GuiGraphicsExtractor graphics = frame.graphics();
        graphics.text(frame.font(), iterators ? ITERATORS : MUTATORS, column.left(), column.top() + HEADING_NUDGE,
                WHITE);
        drawCreate(frame, section.section(), iterators, column.top());
        int listTop = column.top() + ControlFlow.LINE + GAP;
        ScrollBar bar = columnBar(iterators);
        GroupsPane.Bounds rows = new GroupsPane.Bounds(column.left(), listTop,
                column.right() - ScrollBar.WIDTH - ScrollBar.GAP, column.bottom());
        graphics.enableScissor(column.left(), listTop, column.right(), column.bottom());
        frame.targets().clip(column.left(), listTop, column.right(), column.bottom());
        int y = drawRows(new Frame(graphics, frame.font(), rows, frame.targets()), section, iterators,
                listTop - bar.offset());
        frame.targets().unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, frame.targets(), y, column.right(), listTop, column.bottom());
    }

    /**
     * What one frame draws onto.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param rows     where the rows draw, left of the scroll bar
     * @param targets  the frame's click areas
     */
    private record Frame(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds rows, ClickTargets targets) {
    }

    // Decision group-assignments-read-as-iterators: a column's rows, mutators or iterators alone. Answers the y below
    // the last row.
    private int drawRows(Frame frame, ProcessesTab.ProcessSection section, boolean iterators, int top) {
        GroupsPane.Bounds rows = frame.rows();
        int y = top;
        List<ProcessesTab.ProcessRow> kindRows = section.rows().stream()
                .filter(row -> ProcessesTab.isIterator(row.key()) == iterators).toList();
        for (int index = 0; index < kindRows.size(); index++) {
            RowLayout.drawSeparatorAbove(frame.graphics(), index, y, rows.left(), rows.right());
            int rowTop = y;
            ProcessesTab.ProcessRow row = kindRows.get(index);
            y = drawRow(frame.graphics(), frame.font(), section.section(), row, rows, y, frame.targets());
            if (row.key().equals(revealPending)) {
                revealedRow = new RevealedRow(iterators, rowTop - rows.top() + columnBar(iterators).offset(),
                        y - rowTop);
            }
        }
        return y;
    }

    // Decision mutators-and-iterators-each-take-a-create-control: the button opens a name box in its place.
    private void drawCreate(Frame frame, String section, boolean iterators, int top) {
        String heading = iterators ? ITERATORS : MUTATORS;
        int x = frame.rows().left() + frame.font().width(heading) + GAP;
        if (isNaming(section, iterators) && nameBox != null) {
            placeNameBox(frame, nameBox, x, top + 1, frame.rows().right() - x);
            return;
        }
        BuilderOverlays.control(frame.graphics(), frame.font(), NEW_ENTRY,
                null, x, top, frame.targets(),
                at -> startNaming(new Naming(section, iterators, null)));
    }

    // The open name box at most its own width, shrunk where the room left holds less beside its ✓ and ↺, which run
    // what Enter and Escape run.
    // Decision open-name-box-takes-a-done-checkmark.
    // Decision open-name-box-takes-a-gold-arrow-that-cancels.
    private void placeNameBox(Frame frame, EditBox box, int x, int y, int room) {
        int width = Math.max(1, Math.min(NAME_BOX_WIDTH, room - NameBoxButtons.width(frame.font())));
        box.setPosition(x, y);
        box.setWidth(width);
        nameBoxPlaced = true;
        NameBoxButtons.draw(frame.graphics(), frame.font(), x + width, y + NAME_BOX_HEIGHT / HALF
                - ButtonPainter.HEIGHT / HALF, ButtonPainter.HEIGHT, frame.targets(), () -> create(box.getValue()),
                this::stopNaming);
    }

    private boolean isNaming(String section, boolean iterators) {
        return naming != null && naming.renamed() == null && naming.section().equals(section)
                && naming.iterators() == iterators;
    }

    /**
     * Which name box is open: a heading's for a new entry, or a row's for its rename.
     *
     * @param section   the block the entry stands in
     * @param iterators whether it is an iterator
     * @param renamed   the key the box renames, null for a new entry
     */
    private record Naming(String section, boolean iterators, @Nullable String renamed) {
    }

    private void startNaming(Naming which) {
        naming = which;
        if (nameBox != null) {
            nameBox.setValue(which.renamed() == null ? NO_TEXT : which.renamed());
            nameBox.visible = visible;
            actions.focus(nameBox);
        }
    }

    private void stopNaming() {
        naming = null;
        if (nameBox != null) {
            nameBox.visible = false;
        }
    }

    private void create(String typed) {
        Naming which = naming;
        stopNaming();
        if (which == null) {
            return;
        }
        Optional<String> created = which.renamed() != null ? processes.rename(which.section(), which.renamed(), typed)
                : which.iterators() ? processes.createIterator(which.section(), typed)
                : GooTypes.order().stream().findFirst().map(GooTypes::id)
                        .flatMap(type -> processes.createMutator(which.section(), typed, type));
        created.ifPresent(key -> {
            reveal(key);
            actions.draftChanged();
        });
    }

    // Answers the y below the row: its head line, then its sentence of controls wrapping at full width, the next
    // row starting a pixel below so the one-pixel rule above it stands clear of this row's buttons.
    // Decision mutator-delete-clears-a-thinner-rule-and-lines-tighten.
    private int drawRow(GuiGraphicsExtractor graphics, Font font, String section, ProcessesTab.ProcessRow row,
                        GroupsPane.Bounds bounds, int y, ClickTargets targets) {
        drawHeadLine(new Frame(graphics, font, bounds, targets), section, row, y);
        int sentenceTop = y + HEAD_HEIGHT;
        int below = controls.draw(graphics, font, section, row.key(), new GroupsPane.Bounds(bounds.left(),
                sentenceTop, bounds.right(), bounds.bottom()), sentenceTop, targets);
        graphics.fill(bounds.left(), y, bounds.right(), below, ROW_COLOR);
        return below + ROW_GAP;
    }

    // Decision mutator-row-is-aligned-columns-name-kind-validity-rename-delete: the name, the kind dropdown, the
    // mark, Rename and Delete at fixed column lefts, under no header.
    // The operator's report: the kind stood behind the scroll bar, so the line lays out from the x at the column's
    // right end leftward, and the name takes the room left over.
    private void drawHeadLine(Frame frame, String section, ProcessesTab.ProcessRow row, int y) {
        GuiGraphicsExtractor graphics = frame.graphics();
        Font font = frame.font();
        int deleteX = frame.rows().right() - ButtonPainter.width(font, DELETE);
        int markX = deleteX - MARK_WIDTH;
        int kindX = markX - MutatorControls.kindWidth(font) - PENCIL_GAP;
        int nameRoom = kindX - GAP - frame.rows().left() - ButtonPainter.width(font, RENAME) - PENCIL_GAP;
        drawName(frame, section, row.key(), new NameSlot(y, nameRoom));
        if (!ProcessesTab.isIterator(row.key())) {
            controls.drawKind(graphics, font, section, row.key(), kindX, y + BUTTON_INSET - KIND_LINE_INSET,
                    frame.targets());
        }
        VerdictMark mark = verdictMark(row);
        graphics.text(font, mark.glyph(), markX, y + HEAD_TEXT_NUDGE, mark.color());
        frame.targets().addHover(markX, y, MARK_WIDTH, HEAD_HEIGHT, BuilderOverlays.hintKey(row.verdict()
                + (row.reason().isEmpty() ? NO_REASON : REASON_GAP + row.reason())));
        drawRemoval(frame, section, row.key(), y + BUTTON_INSET);
    }

    // The red x at the row's right end marks the entry; a marked entry's gold arrow in its place restores it.
    // Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
    private void drawRemoval(Frame frame, String section, String key, int top) {
        String mark = markKey(section, key);
        if (draft.removals().isMarked(RemovalMarks.Tab.MUTATORS, mark)) {
            ButtonPainter.drawRestore(frame.graphics(), frame.font(), frame.rows().right(), top, ButtonPainter.HEIGHT,
                    frame.targets(), () -> draft.removals().restore(RemovalMarks.Tab.MUTATORS, mark));
            return;
        }
        ButtonPainter.drawColored(frame.graphics(), frame.font(), DELETE, RED,
                frame.rows().right() - ButtonPainter.width(frame.font(), DELETE), top, ButtonPainter.HEIGHT,
                frame.targets(), () -> markEntry(mark), BuilderOverlays.hintKey(DELETE_HINT));
    }

    // A mark names the entry's block and its key, since a key may stand in both blocks.
    private static String markKey(String section, String key) {
        return section + MARK_SEPARATOR + key;
    }

    /**
     * Where a row's name draws.
     *
     * @param y    the row's top
     * @param room the pixels the name may take before the pencil
     */
    private record NameSlot(int y, int room) {
    }

    // The name cut to its room, hovering whole where cut, the pencil just past its last letter; the rename box
    // stands in their place while open.
    private void drawName(Frame frame, String section, String key, NameSlot slot) {
        int x = frame.rows().left();
        int y = slot.y();
        if (nameBox != null && naming != null && key.equals(naming.renamed()) && section.equals(naming.section())) {
            int room = slot.room() + ButtonPainter.width(frame.font(), RENAME) + PENCIL_GAP;
            placeNameBox(frame, nameBox, x, y, room);
            return;
        }
        String shown = fittedName(frame.font(), key, slot.room());
        ButtonPainter.drawEntryName(frame.graphics(), frame.font(), shown, x, y + HEAD_TEXT_NUDGE,
                draft.removals().isMarked(RemovalMarks.Tab.MUTATORS, markKey(section, key)));
        if (!shown.equals(key)) {
            frame.targets().addHover(x, y, frame.font().width(shown), HEAD_HEIGHT, BuilderOverlays.hintKey(key));
        }
        ButtonPainter.draw(frame.graphics(), frame.font(), RENAME, x + frame.font().width(shown) + PENCIL_GAP,
                y + BUTTON_INSET, ButtonPainter.HEIGHT, frame.targets(), () -> startNaming(new Naming(section,
                        ProcessesTab.isIterator(key), key)), BuilderOverlays.hintKey(RENAME_HINT));
    }

    /**
     * @param key an entry's key
     * @return the key whole up to 24 characters; past that its first 20, an ellipsis and its last 4
     *     (decision mutator-row-is-aligned-columns-name-kind-validity-rename-delete)
     */
    static String shownName(String key) {
        return key.length() <= NAME_HEAD + NAME_TAIL ? key
                : key.substring(0, NAME_HEAD) + ELLIPSIS + key.substring(key.length() - NAME_TAIL);
    }

    // The shown name, its head shortened a letter at a time until it fits the room, its last four kept.
    private static String fittedName(Font font, String key, int room) {
        String shown = shownName(key);
        if (font.width(shown) <= room || key.length() <= NAME_TAIL + 1) {
            return shown;
        }
        String tail = key.substring(key.length() - NAME_TAIL);
        int head = Math.min(NAME_HEAD, key.length() - NAME_TAIL);
        while (head > 1 && font.width(key.substring(0, head) + ELLIPSIS + tail) > room) {
            head--;
        }
        return key.substring(0, head) + ELLIPSIS + tail;
    }

    /**
     * The mark a row's verdict draws behind.
     *
     * @param glyph the check or the x
     * @param color green for Valid, red for Invalid
     */
    record VerdictMark(String glyph, int color) {
    }

    /**
     * @param row a Mutators row
     * @return a green check for a row the parser accepts, a red x for one it refuses
     *     (decision mutators-read-valid-or-invalid-with-a-mark)
     */
    static VerdictMark verdictMark(ProcessesTab.ProcessRow row) {
        return row.parsed() ? new VerdictMark(CHECK, GREEN) : new VerdictMark(CROSS, RED);
    }

    /**
     * Centers a mutator's row on the next frame, as a mutator link's shift + left click does (decisions
     * diagnose-and-correct-mutator-references-that-do-not-link, shift-follow-centers-the-target-in-its-scrolling-view).
     *
     * @param key the mutator's key
     */
    void reveal(String key) {
        revealPending = key;
    }

    // Once the revealed row has drawn and the bar has measured this frame, centers the row in the list.
    private void centerRevealedRow() {
        if (revealedRow != null) {
            columnBar(revealedRow.iterators()).center(revealedRow.top(), revealedRow.height());
            revealedRow = null;
            revealPending = null;
        }
    }

    /**
     * Drops every marked mutator and iterator from its block, as the save does before it writes the draft.
     * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
     */
    void applyRemovals() {
        for (String mark : draft.removals().marked(RemovalMarks.Tab.MUTATORS)) {
            int split = mark.indexOf(MARK_SEPARATOR);
            processes.delete(mark.substring(0, split), mark.substring(split + 1));
            draft.removals().restore(RemovalMarks.Tab.MUTATORS, mark);
        }
    }

    private void markEntry(String mark) {
        overlays.close();
        draft.removals().mark(RemovalMarks.Tab.MUTATORS, mark);
        actions.markUnsaved();
    }
}
