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
 * Draws the Mutators tab: each block's summary, then its mutators (formulas, additives and stacks) and its iterators
 * (group assignments) under a heading each, every heading with a create control naming a fresh entry (decisions
 * group-assignments-read-as-iterators, mutators-and-iterators-each-take-a-create-control). Each entry draws the key, the form, the parser's verdict and Delete on
 * an entry's first line and its sentence of controls at full width below (decision mutator-rows-take-an-extra-line),
 * which build the entry with nothing typed but a number or a search (decision
 * expression-builder-composes-every-form-without-typing), and Delete drops the entry, as
 * index.html's processes section does (decision goo-workbench-opens-the-in-game-manager-screen). The rows scroll
 * under a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ProcessesTab}.
 */
final class ProcessesPane {
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_HEIGHT = 13;
    // Decision mutator-row-is-aligned-columns-name-kind-validity-rename-delete: 25 characters of name.
    private static final int NAME_WIDTH = 156;
    private static final int KIND_WIDTH = 84;
    private static final int MARK_WIDTH = 14;
    private static final int NAME_HEAD = 20;
    private static final int NAME_TAIL = 4;
    private static final String ELLIPSIS = "…";
    private static final String RENAME = "Rename";
    private static final String RENAME_HINT = "Rename this entry";
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
    private static final int SECTION_GAP = 6;
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
    private static final String DELETE = "Delete";
    private static final String SUMMARY_GAP = "  ";
    private static final String MUTATORS = "Mutators";
    private static final String ITERATORS = "Iterators";
    private static final String NEW_MUTATOR = "New mutator";
    private static final String NEW_MUTATOR_HINT = "Name a new formula, additive or stack";
    private static final String NEW_ITERATOR = "New iterator";
    private static final String NEW_ITERATOR_HINT = "Name the group a new iterator assigns values to";
    private static final String NAME_HINT = "name, Enter";
    private static final String NO_TEXT = "";
    // Mutators first, then iterators.
    private static final boolean[] KINDS = {false, true};
    private static final int HEADING_NUDGE = 5;
    private static final int NAME_BOX_WIDTH = 140;
    private static final int NAME_BOX_HEIGHT = 16;
    private static final String SEARCH_HINT = "Search mutators";
    private static final int SEARCH_WIDTH = 200;
    private static final int SEARCH_ROW = NAME_BOX_HEIGHT + 4;

    private final ProcessesTab processes;
    private final ManagerDraft draft;
    private final ManagerActions actions;
    private final BuilderOverlays overlays;
    private final MutatorControls controls;
    private final ScrollBar bar = new ScrollBar(SCROLL_STEP);
    private @Nullable String revealPending;
    private @Nullable RevealedRow revealedRow;
    private @Nullable EditBox nameBox;
    private @Nullable EditBox search;
    private @Nullable Naming naming;
    private boolean nameBoxPlaced;
    private boolean visible;

    // Where the row a follow revealed stood inside the list's content this frame.
    private record RevealedRow(int top, int height) {
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
            bar.reset();
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
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        if (overlays.scrollList(amount)) {
            return;
        }
        bar.wheel(amount);
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
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(pane.left(), pane.top() + SEARCH_ROW, pane.right(),
                pane.bottom());
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        targets.clip(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        GroupsPane.Bounds rows = new GroupsPane.Bounds(bounds.left(), bounds.top(),
                bounds.right() - ScrollBar.WIDTH - ScrollBar.GAP, bounds.bottom());
        int y = drawSections(new Frame(graphics, font, rows, targets), bounds.top() - bar.offset());
        targets.unclip();
        graphics.disableScissor();
        bar.measureAndDraw(graphics, targets, y, bounds.right(), bounds.top(), bounds.bottom());
        centerRevealedRow();
        overlays.draw(graphics, font, targets);
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

    // Answers the y below the last block; the name box shows only while the heading that opened it draws.
    private int drawSections(Frame frame, int top) {
        int y = top;
        nameBoxPlaced = false;
        for (ProcessesTab.ProcessSection section : processes.sections()) {
            frame.graphics().text(frame.font(), section.section() + SUMMARY_GAP + section.summary(),
                    frame.rows().left(), y, WHITE);
            y += LINE_HEIGHT + GAP;
            for (boolean iterators : KINDS) {
                y = drawKind(frame, section, iterators, y);
            }
            y += SECTION_GAP;
        }
        if (nameBox != null) {
            nameBox.visible = visible && nameBoxPlaced;
        }
        return y;
    }

    // Decision group-assignments-read-as-iterators: a block's mutators under one heading, its iterators under another,
    // each heading with its create control. Answers the y below the last row.
    private int drawKind(Frame frame, ProcessesTab.ProcessSection section, boolean iterators, int top) {
        GroupsPane.Bounds rows = frame.rows();
        frame.graphics().text(frame.font(), iterators ? ITERATORS : MUTATORS, rows.left(), top + HEADING_NUDGE,
                WHITE);
        drawCreate(frame, section.section(), iterators, top);
        int y = top + ControlFlow.LINE + GAP;
        List<ProcessesTab.ProcessRow> kindRows = section.rows().stream()
                .filter(row -> ProcessesTab.isIterator(row.key()) == iterators).toList();
        for (int index = 0; index < kindRows.size(); index++) {
            RowLayout.drawSeparatorAbove(frame.graphics(), index, y, rows.left(), rows.right());
            int rowTop = y;
            ProcessesTab.ProcessRow row = kindRows.get(index);
            y = drawRow(frame.graphics(), frame.font(), section.section(), row, rows, y, frame.targets());
            if (row.key().equals(revealPending)) {
                revealedRow = new RevealedRow(rowTop - rows.top() + bar.offset(), y - rowTop);
            }
        }
        return y;
    }

    // Decision mutators-and-iterators-each-take-a-create-control: the button opens a name box in its place.
    private void drawCreate(Frame frame, String section, boolean iterators, int top) {
        String heading = iterators ? ITERATORS : MUTATORS;
        int x = frame.rows().left() + frame.font().width(heading) + GAP;
        if (isNaming(section, iterators) && nameBox != null) {
            nameBox.setPosition(x, top + 1);
            nameBoxPlaced = true;
            return;
        }
        BuilderOverlays.control(frame.graphics(), frame.font(), iterators ? NEW_ITERATOR : NEW_MUTATOR,
                iterators ? NEW_ITERATOR_HINT : NEW_MUTATOR_HINT, x, top, frame.targets(),
                at -> startNaming(new Naming(section, iterators, null)));
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
    private void drawHeadLine(Frame frame, String section, ProcessesTab.ProcessRow row, int y) {
        GuiGraphicsExtractor graphics = frame.graphics();
        Font font = frame.font();
        int x = frame.rows().left();
        drawName(frame, section, row.key(), y);
        if (!ProcessesTab.isIterator(row.key())) {
            controls.drawKind(graphics, font, section, row.key(), x + NAME_WIDTH, y + BUTTON_INSET
                    - KIND_LINE_INSET, frame.targets());
        }
        int markX = x + NAME_WIDTH + KIND_WIDTH;
        VerdictMark mark = verdictMark(row);
        graphics.text(font, mark.glyph(), markX, y + HEAD_TEXT_NUDGE, mark.color());
        frame.targets().addHover(markX, y, MARK_WIDTH, HEAD_HEIGHT, BuilderOverlays.hintKey(row.verdict()
                + (row.reason().isEmpty() ? NO_REASON : REASON_GAP + row.reason())));
        int renameX = markX + MARK_WIDTH;
        ButtonPainter.Box rename = ButtonPainter.draw(graphics, font, RENAME, renameX, y + BUTTON_INSET,
                ButtonPainter.HEIGHT, frame.targets(), () -> startNaming(new Naming(section,
                        ProcessesTab.isIterator(row.key()), row.key())), BuilderOverlays.hintKey(RENAME_HINT));
        ButtonPainter.drawColored(graphics, font, DELETE, RED, rename.right() + GAP, y + BUTTON_INSET,
                ButtonPainter.HEIGHT, frame.targets(), () -> delete(section, row.key()), null);
    }

    // The name cut to its column, hovering whole where cut; the rename box stands in its place while open.
    private void drawName(Frame frame, String section, String key, int y) {
        int x = frame.rows().left();
        if (nameBox != null && naming != null && key.equals(naming.renamed()) && section.equals(naming.section())) {
            nameBox.setPosition(x, y);
            nameBoxPlaced = true;
            return;
        }
        String shown = shownName(key);
        frame.graphics().text(frame.font(), shown, x, y + HEAD_TEXT_NUDGE, WHITE);
        if (!shown.equals(key)) {
            frame.targets().addHover(x, y, frame.font().width(shown), HEAD_HEIGHT, BuilderOverlays.hintKey(key));
        }
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
            bar.center(revealedRow.top(), revealedRow.height());
            revealedRow = null;
            revealPending = null;
        }
    }

    private void delete(String section, String key) {
        overlays.close();
        processes.delete(section, key);
        actions.draftChanged();
    }
}
