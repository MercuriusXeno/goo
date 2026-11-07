package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The values manager /goo workbench opens: the title row carrying Promote, Revert, Save, a [?] that toggles explainer mode and an X that closes it, a tab bar carrying every
 * tab of the page over one shared draft, each tab drawn by its pane, and a status line beneath
 * (decisions goo-workbench-opens-the-in-game-manager-screen, save-applies-the-draft-live-promote-renames-it). The
 * state lives in the tab classes and {@link ManagerConnection}; this class lays the panes out and forwards input.
 */
public final class ValuesManagerScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TITLE_Y = 6;
    private static final int CONTENT_Y = 40;
    private static final int TITLE_GAP = 8;
    private static final int STATUS_INSET = 12;
    private static final int STATUS_GAP = 4;
    private static final String TITLE = "Values manager";
    private static final String DRAFT_BADGE = "draft";
    private static final String READING = "Reading the draft.";
    private static final String SAVING = "saving";
    private static final String SAVED = "saved to the draft";
    private static final String UNSAVED = "unsaved changes";
    private static final String NOTHING_POSTED = "";
    private static final String NO_STATUS = "";
    private static final String EMPTIED = "";

    private final ManagerConnection connection;
    private final ManagerDraft draft = new ManagerDraft();
    private final ValuesTab values = new ValuesTab(draft);
    private final GroupsTab groupsTab = new GroupsTab(draft);
    private final VariablesTab variablesTab = new VariablesTab(draft);
    private final ProcessesTab processesTab = new ProcessesTab(draft);
    private final AuditTab auditTab = new AuditTab();
    private final AbilitiesTab abilitiesTab = new AbilitiesTab();
    private final ItemIcons icons = itemIconsOver(values);
    private final ClickTargets targets = new ClickTargets();
    private final LinkCursor linkCursor = new LinkCursor(cursor -> minecraft.getWindow().selectCursor(cursor),
            PointerHand::cursor, CopyPlus::cursor);
    private final ExplainerMode explainer = new ExplainerMode(ExplainerTargets.forTabs(this::tabBounds));
    private final ValuesPane valuesPane;
    private final GroupsPane groupsPane;
    private final VariablesPane variablesPane;
    private final ProcessesPane processesPane;
    private final AuditPane auditPane;
    private final AbilitiesPane abilitiesPane;
    private final DraftConfirm draftConfirm = new DraftConfirm();
    private final DraftPane draftPane = new DraftPane(draftConfirm, this::runChoice);
    private final ManagerEntrySource entrySource;
    private final ManagerReads reads;
    private boolean unsaved;
    private ManagerTab tab = ManagerTab.VALUES;
    private String status = READING;
    private boolean statusIsError;
    private boolean loaded;

    /**
     * @param connection  the line to the server's workbench operations
     * @param entrySource the client's items and item tags the Values tab lists
     */
    public ValuesManagerScreen(ManagerConnection connection, ManagerEntrySource entrySource) {
        super(Component.literal(TITLE));
        this.connection = connection;
        this.entrySource = entrySource;
        ManagerActions actions = new ScreenActions();
        valuesPane = new ValuesPane(values, icons, actions, builderPane(actions));
        groupsPane = new GroupsPane(groupsTab, values, icons, actions, builderPane(actions));
        variablesPane = new VariablesPane(variablesTab, draft, icons, actions, builderPane(actions));
        // Decision add-mutator-entry-shows-its-reduced-value: an add row reads its prefab's value, highest first.
        processesPane = new ProcessesPane(processesTab, draft, actions, icons, () -> variablesTab.blocks().stream()
                .filter(block -> VariablesTab.TREE_KIND.equals(block.kind())).map(VariablesTab.ConstantBlock::name)
                .toList(), prefab -> variablesTab.prefabValue(prefab).map(VariablesTab.PrefabValue::tree)
                .orElse(Map.of()));
        auditPane = new AuditPane(auditTab, draft, actions);
        // abilities-tab-lists-every-numeric-param-as-expression-boxes
        abilitiesPane = new AbilitiesPane(abilitiesTab, actions, icons, builderPane(actions),
                key -> Language.getInstance().getOrDefault(key), this::worthOf, this::perTypePrefabs);
        reads = new ManagerReads(actions, values, groupsPane, variablesPane, processesPane, abilitiesPane);
    }

    // The prefabs holding an amount per goo type, which an ability's cost reads at the ability's own type.
    private Set<String> perTypePrefabs() {
        return variablesTab.blocks().stream().filter(block -> VariablesTab.TREE_KIND.equals(block.kind()))
                .map(VariablesTab.ConstantBlock::name).collect(Collectors.toUnmodifiableSet());
    }

    // A builder over the server's items and the draft's prefabs (decision
    // expression-builder-composes-every-form-without-typing).
    private ExpressionBuilderPane builderPane(ManagerActions actions) {
        return new ExpressionBuilderPane(actions, icons, values::entries, () -> variablesTab.blocks().stream()
                .map(block -> new BuilderGrammar.Prefab(block.name(), VariablesTab.TREE_KIND.equals(block.kind())))
                .toList(), this::worthOf);
    }

    // A referent's resolved amounts by goo type, a prefab's from the last evaluate reply and an item's from the last
    // resolve, which the member picker greys the rest of (decision builder-disables-the-type-buttons-the-referent-lacks).
    private Map<String, String> worthOf(ExpressionBuilder.Ref ref) {
        if (ref.kind() == SearchDropdown.Kind.PREFAB) {
            return variablesTab.prefabValue(ref.id()).map(VariablesTab.PrefabValue::tree).orElse(Map.of());
        }
        return values.worthOf(ref.id());
    }

    /**
     * The panes' line back to this screen.
     */
    private final class ScreenActions implements ManagerActions {
        @Override
        public void request(String operation, String text, Consumer<String> answered) {
            ValuesManagerScreen.this.request(operation, text, answered);
        }

        @Override
        public void stream(String operation, String text, Consumer<String> progress, Consumer<String> answered,
                           Consumer<String> refused) {
            connection.request(operation, text, progress).thenAccept(reply -> {
                if (reply.answered()) {
                    answered.accept(reply.text());
                } else {
                    setStatus(reply.text(), true);
                    refused.accept(reply.text());
                }
            });
        }

        @Override
        public void draftChanged() {
            markUnsaved();
            readDraftAfresh();
        }

        @Override
        public void markUnsaved() {
            ValuesManagerScreen.this.markUnsaved();
        }

        @Override
        public void reloadDraft() {
            request(WorkbenchOperations.MODEL, NOTHING_POSTED, model -> {
                values.readModel(model);
                unsaved = false;
                setStatus(SAVED, false);
                readDraftAfresh();
            });
        }

        @Override
        public void openVariable(String name) {
            selectTab(ManagerTab.VARIABLES);
            variablesPane.jumpTo(name);
        }

        @Override
        public void openItem(String id) {
            selectTab(ManagerTab.VALUES);
            valuesPane.reveal(id);
        }

        @Override
        public void openGroup(String name) {
            selectTab(ManagerTab.GROUPS);
            groupsPane.reveal(name);
        }

        @Override
        public void openMutator(String name) {
            selectTab(ManagerTab.PROCESSES);
            processesPane.reveal(name);
        }

        @Override
        public <W extends AbstractWidget> W addWidget(W widget) {
            return addRenderableWidget(widget);
        }

        @Override
        public void focus(AbstractWidget widget) {
            setFocused(widget);
        }
    }

    @Override
    protected void init() {
        // A scaled label lands on a whole screen pixel, so no operator's glyph draws one column stretched.
        Lettering.guiScale(minecraft.getWindow().getGuiScale());
        valuesPane.init(font);
        groupsPane.init(font);
        variablesPane.init(font);
        processesPane.init(font);
        abilitiesPane.init(font);
        selectTab(tab);
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    // The chrome's buttons: the title row's X, [?], Save, Revert and Promote and the tab row.
    private ManagerChrome.Actions chromeActions() {
        return new ManagerChrome.Actions(this::onClose, explainer::toggle, () -> save(() -> { }),
                () -> draftConfirm.askRevert(draft.path()), () -> draftConfirm.askPromote(draft.path(), unsaved),
                this::selectTab);
    }

    private DragMask.Rect tabBounds(ManagerTab each) {
        ButtonPainter.Box box = ManagerChrome.tabBox(font, each);
        return new DragMask.Rect(box.left(), box.top(), box.right(), box.top() + box.height());
    }

    private GroupsPane.Bounds contentBounds() {
        return new GroupsPane.Bounds(MARGIN, CONTENT_Y, width - MARGIN, height - STATUS_INSET - STATUS_GAP);
    }

    private void selectTab(ManagerTab chosen) {
        tab = chosen;
        valuesPane.show(chosen == ManagerTab.VALUES);
        groupsPane.show(chosen == ManagerTab.GROUPS);
        variablesPane.show(chosen == ManagerTab.VARIABLES);
        processesPane.show(chosen == ManagerTab.PROCESSES);
        abilitiesPane.show(chosen == ManagerTab.ABILITIES);
    }

    private void load() {
        reads.open(entrySource, () -> setStatus(NO_STATUS, false));
        auditPane.refresh();
        abilitiesPane.refresh();
    }

    private void readDraftAfresh() {
        reads.readAfresh();
    }

    /**
     * Writes the draft, which the server applies to every player at once
     * (decision save-applies-the-draft-live-promote-renames-it).
     *
     * @param after runs once the save answered
     */
    private void save(Runnable after) {
        setStatus(SAVING, false);
        reads.save(() -> {
            unsaved = false;
            setStatus(SAVED, false);
            after.run();
        });
    }

    private void markUnsaved() {
        unsaved = true;
        setStatus(UNSAVED, false);
    }

    private void runChoice(DraftConfirm.Choice choice) {
        switch (choice) {
            case CONFIRM -> runDraftAction();
            case SAVE_AND_CLOSE -> {
                draftConfirm.cancel();
                save(this::closeManager);
            }
            case CLOSE_UNSAVED -> {
                draftConfirm.cancel();
                closeManager();
            }
            case CANCEL -> draftConfirm.cancel();
        }
    }

    /**
     * The X and Esc reach here: unsaved changes stand the close confirm rather than closing and losing them, and Esc
     * over a standing confirm cancels it.
     */
    @Override
    public void onClose() {
        if (draftConfirm.closesNow(unsaved)) {
            closeManager();
        }
    }

    private void closeManager() {
        super.onClose();
    }

    /**
     * Runs the confirmed Promote or Revert, saving unsaved changes first for a Promote
     * (decision values-checks-go-with-the-checks-tab).
     */
    private void runDraftAction() {
        boolean savesFirst = draftConfirm.savesFirst();
        draftConfirm.confirmed();
        Runnable send = () -> connection.request(draftConfirm.operation(), draftConfirm.requestText())
                .thenAccept(reply -> {
                    if (reply.answered()) {
                        values.readModel(reply.text());
                        // decision every-delete-marks-for-removal-and-a-gold-arrow-restores
                        draft.removals().clear();
                        unsaved = false;
                        setStatus(draftConfirm.doneStatus(draft.path()), false);
                        draftConfirm.cancel();
                        readDraftAfresh();
                        // ability-drafts-promote-per-file-like-base-values: one confirm covers both kinds of draft
                        abilitiesPane.refresh();
                    } else {
                        draftConfirm.cancel();
                        setStatus(reply.text(), true);
                    }
                });
        if (savesFirst) {
            save(send);
        } else {
            send.run();
        }
    }

    private void request(String operation, String text, Consumer<String> answered) {
        connection.request(operation, text).thenAccept(reply -> {
            if (reply.answered()) {
                answered.accept(reply.text());
            } else {
                setStatus(reply.text(), true);
            }
        });
    }

    private void setStatus(String text, boolean error) {
        status = text;
        statusIsError = error;
    }

    /**
     * Hands a press to the frame's areas by its button and the modifiers held
     * (decision shift-left-click-follows-plain-left-modifies).
     *
     * @param targets the frame's click areas
     * @param event   the press
     * @return whether an area took it
     */
    static boolean clickTargets(ClickTargets targets, MouseButtonEvent event) {
        return targets.click(event.x(), event.y(), event.button(), event.modifiers());
    }

    /**
     * Empties the visible text box a right-button press lands in, through setValue so its responder refills the list
     * it filters (decision right-click-empties-every-text-box-in-the-manager).
     *
     * @param widgets the screen's widgets
     * @param event   the press
     * @return whether a box took the press
     */
    static boolean emptyBoxUnder(List<? extends GuiEventListener> widgets, MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        for (GuiEventListener widget : widgets) {
            if (widget instanceof EditBox box && box.visible && box.isMouseOver(event.x(), event.y())) {
                box.setValue(EMPTIED);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (draftPane.open()) {
            return clickTargets(targets, event);
        }
        // The mode is modal: a click anywhere, the [?] included, leaves it and reaches nothing beneath.
        return explainer.leave() || routePress(targets, event, press -> emptyBoxUnder(children(), press)
                || super.mouseClicked(press, doubleClick), () -> setFocused(null));
    }

    /**
     * Where a press goes once the draft and the explainer have passed it: an open overlay over the point takes it
     * through the click targets alone, so no widget behind the overlay takes it; elsewhere the widgets take it first,
     * then the click targets with the focus cleared.
     * Decision open-dropdown-intercepts-clicks-before-the-pane-behind-it.
     *
     * @param targets the frame's click areas
     * @param event   the press
     * @param widgets offers the press to the screen's widgets, answering whether one took it
     * @param unfocus clears the screen's focus
     * @return whether anything took the press
     */
    static boolean routePress(ClickTargets targets, MouseButtonEvent event, Predicate<MouseButtonEvent> widgets,
                              Runnable unfocus) {
        if (targets.overlayAt(event.x(), event.y())) {
            return clickTargets(targets, event);
        }
        if (widgets.test(event)) {
            return true;
        }
        unfocus.run();
        return clickTargets(targets, event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return targets.drag(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy: Ctrl's state as the button comes up
        // decides a copy from a move.
        targets.holdControl(minecraft.hasControlDown());
        return targets.release() || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && explainer.leave()) {
            return true;
        }
        return paneTakesKey(event.key())
                || event.key() == GLFW.GLFW_KEY_ESCAPE && escapeEmpties(getFocused(), () -> setFocused(null))
                || super.keyPressed(event);
    }

    /**
     * Empties the focused text box through setValue, so its responder refills the list it filters, and drops its
     * focus, as a right click empties a box; the manager closes only from an Escape with no box focused (decision
     * escape-empties-the-box-and-never-closes-the-manager).
     *
     * @param focused the screen's focused element, null for none
     * @param unfocus drops the screen's focus
     * @return whether a box took the Escape
     */
    static boolean escapeEmpties(@Nullable GuiEventListener focused, Runnable unfocus) {
        if (!(focused instanceof EditBox box) || !box.visible) {
            return false;
        }
        box.setValue(EMPTIED);
        unfocus.run();
        return true;
    }

    private boolean paneTakesKey(int key) {
        return switch (tab) {
            case VALUES -> valuesPane.keyPressed(key);
            case GROUPS -> groupsPane.keyPressed(key);
            case VARIABLES -> variablesPane.keyPressed(key);
            case PROCESSES -> processesPane.keyPressed(key);
            case ABILITIES -> abilitiesPane.keyPressed(key);
            default -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (draftPane.open() || explainer.active()) {
            return true;
        }
        switch (tab) {
            case VALUES -> valuesPane.scroll(x, MARGIN, scrollY);
            case GROUPS -> groupsPane.scroll(x, y, MARGIN, scrollY);
            case VARIABLES -> variablesPane.scroll(scrollY);
            case PROCESSES -> processesPane.scroll(x, scrollY);
            case AUDIT -> auditPane.scroll(y, scrollY);
            case ABILITIES -> abilitiesPane.scroll(scrollY);
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, Palette.SCREEN_BACKDROP);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        targets.clear();
        // Decision holding-shift-underlines-every-link.
        targets.holdShift(minecraft.hasShiftDown());
        // Decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy.
        targets.holdControl(minecraft.hasControlDown());
        ManagerChrome.draw(graphics, font, targets, width, tab, chromeActions());
        extractPane(graphics, mouseX, mouseY);
        drawTitleAndStatus(graphics);
        // Decision dropdowns-draw-on-one-stratum-above-everything-but-tooltip-and-dialog: the open lists draw here
        // alone, after every pane and chrome draw, then the dialogs, the explainer and the tooltip over them.
        OverlayStratum.drawOverlays(graphics, font, targets, overlayLayers());
        drawDialogs(graphics);
        explainer.extract(graphics, width, height, targets);
        // decision goo-types-draw-as-icons-named-on-hover
        extractTooltip(graphics, mouseX, mouseY);
        // Decision shift-over-a-link-shows-a-pixel-pointer-hand.
        linkCursor.frame(targets, mouseX, mouseY, graphics::requestCursor);
    }

    private void extractPane(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GroupsPane.Bounds content = contentBounds();
        switch (tab) {
            case VALUES -> valuesPane.extract(graphics, font, content, targets);
            case GROUPS -> groupsPane.extract(graphics, font, content, targets);
            case VARIABLES -> variablesPane.extract(graphics, font, content, targets, mouseX, mouseY);
            case PROCESSES -> processesPane.extract(graphics, font, content, targets);
            case AUDIT -> auditPane.extract(graphics, font, content, targets);
            case ABILITIES -> abilitiesPane.extract(graphics, font, content, targets);
        }
    }

    // The title, the gold badge while the draft differs from the value file, and the status line.
    private void drawTitleAndStatus(GuiGraphicsExtractor graphics) {
        graphics.text(font, title, MARGIN, TITLE_Y, Palette.TEXT);
        if (draft.differs()) {
            graphics.text(font, DRAFT_BADGE, MARGIN + font.width(title) + TITLE_GAP, TITLE_Y, Palette.GOLD);
        }
        graphics.text(font, font.plainSubstrByWidth(status, width - MARGIN - MARGIN), MARGIN, height - STATUS_INSET,
                statusIsError ? Palette.DANGER : Palette.MUTED);
    }

    // The Prefabs tab's delete confirm, then the draft's confirm, each over the overlay stratum.
    private void drawDialogs(GuiGraphicsExtractor graphics) {
        if (tab == ManagerTab.VARIABLES) {
            variablesPane.drawDialog(graphics, font, targets);
        }
        draftPane.extract(graphics, font, targets);
    }

    // The active tab's open lists, which the overlay stratum draws.
    private List<OverlayStratum.Layer> overlayLayers() {
        return switch (tab) {
            case VALUES -> valuesPane.overlayLayers();
            case GROUPS -> groupsPane.overlayLayers();
            case VARIABLES -> variablesPane.overlayLayers();
            case PROCESSES -> processesPane.overlayLayers();
            case AUDIT -> List.of();
            case ABILITIES -> abilitiesPane.overlayLayers();
        };
    }

    @Override
    public void removed() {
        linkCursor.release();
        super.removed();
    }

    /**
     * @param values the Items tab, which answers each item's value and anchors
     * @return the item stacks every pane draws from, each icon hovering its item's value and anchors
     */
    static ItemIcons itemIconsOver(ValuesTab values) {
        // decision item-hover-lists-its-anchors
        return new ItemIcons(values::worthOf, values::anchorsOf);
    }

    // Decision tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room.
    static Optional<ClientTooltipPositioner> anchoredTooltip(ClickTargets targets, double mouseX, double mouseY) {
        return targets.hoveredArea(mouseX, mouseY).map(area -> TooltipAnchor.anchoredTo(area.bounds()));
    }

    // The tooltip draws on a stratum of its own over the frame, placed by the hovered area's bounds rather than the
    // cursor.
    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Optional<ClickTargets.HoveredArea> area = targets.hoveredArea(mouseX, mouseY);
        Optional<ClientTooltipPositioner> positioner = anchoredTooltip(targets, mouseX, mouseY);
        if (area.isEmpty() || positioner.isEmpty()) {
            return;
        }
        Optional<Component> explained = explainer.tooltipFor(area.get().hoverKey());
        Optional<List<TooltipLine>> hoveredLines = tooltipLines(area.get().hoverKey());
        List<ClientTooltipComponent> components;
        if (explained.isPresent()) {
            components = ExplainerTooltip.components(font, explained.get(), area.get().bounds(), width, height);
        } else if (hoveredLines.isPresent()) {
            components = ClientHooks.gatherTooltipComponentsFromElements(ItemStack.EMPTY,
                    tooltipElements(hoveredLines.get()), mouseX, width, height, font);
        } else {
            return;
        }
        graphics.nextStratum();
        graphics.tooltip(font, components, mouseX, mouseY, positioner.get(), null);
    }

    // A goo type, a control or a truncated group name names itself on one line; an item or a prefab names itself,
    // then its value.
    private Optional<List<TooltipLine>> tooltipLines(String hoverKey) {
        return oneLineTooltip(hoverKey).or(() -> icons.tooltipFor(hoverKey)
                .or(() -> PrefabHovers.tooltipFor(variablesTab, hoverKey))
                .or(() -> GroupHovers.tooltipFor(groupsTab, values, processesTab, hoverKey))
                .or(() -> ConversionHovers.tooltipFor(processesTab, variablesTab, hoverKey))
                // ability-heading-is-rail-icon-name-draft-badge-and-hovers-its-description
                .or(() -> AbilitiesPane.descriptionFor(hoverKey, key -> Language.getInstance().getOrDefault(key))
                        .map(text -> List.of(new TooltipLine.Text(Component.literal(text))))));
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the one line a goo type, a control, the vs of an explicit row or a truncated name shows, empty for any
     *     other key
     */
    static Optional<List<TooltipLine>> oneLineTooltip(String hoverKey) {
        return GooTypeIcons.tooltipFor(hoverKey).or(() -> BuilderOverlays.tooltipFor(hoverKey))
                .or(() -> ValuesPane.vsTooltipFor(hoverKey)).<TooltipLine>map(TooltipLine.Text::new).or(() -> ButtonPainter.wholeNameTooltip(hoverKey))
                .map(List::of);
    }

    // Text stays text; a run of goo amounts folds into one grid that reaches GooAmountGrid through the tooltip factory
    // registered for it, and the deferred call draws the tooltip over the frame (decisions
    // value-tooltips-draw-each-types-icon-before-its-amount,
    // tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
    static List<Either<FormattedText, TooltipComponent>> tooltipElements(List<TooltipLine> lines) {
        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>();
        List<TooltipLine.GooAmount> run = new ArrayList<>();
        for (TooltipLine line : lines) {
            if (line instanceof TooltipLine.GooAmount amount) {
                run.add(amount);
                continue;
            }
            endRun(elements, run);
            elements.add(elementOf(line));
        }
        endRun(elements, run);
        return elements;
    }

    private static Either<FormattedText, TooltipComponent> elementOf(TooltipLine line) {
        return switch (line) {
            case TooltipLine.Text text -> Either.left(text.text());
            case TooltipLine.GooAmount amount -> Either.right(new TooltipLine.GooAmounts(List.of(amount)));
            // Runs, a grid of runs, members and an amount grid each reach their tooltip component as they are.
            case TooltipComponent component -> Either.right(component);
        };
    }

    private static void endRun(List<Either<FormattedText, TooltipComponent>> elements,
                               List<TooltipLine.GooAmount> run) {
        if (!run.isEmpty()) {
            elements.add(Either.right(new TooltipLine.GooAmounts(run)));
            run.clear();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
