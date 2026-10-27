package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The values manager /goo workbench opens: the title row carrying Promote, Revert, Save and an X that closes it, a tab bar carrying every
 * tab of the page over one shared draft, each tab drawn by its pane, and a status line beneath
 * (decisions goo-workbench-opens-the-in-game-manager-screen, save-applies-the-draft-live-promote-renames-it). The
 * state lives in the tab classes and {@link ManagerConnection}; this class lays the panes out and forwards input.
 */
public final class ValuesManagerScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TITLE_Y = 6;
    private static final int TOPBAR_Y = 3;
    private static final int TAB_Y = 20;
    private static final int CONTENT_Y = 40;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_PADDING = 10;
    private static final int TAB_GAP = 2;
    private static final int TITLE_GAP = 8;
    private static final int STATUS_INSET = 12;
    private static final int STATUS_GAP = 4;
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int DRAFT_COLOR = 0xFFE0C060;
    private static final String TITLE = "Values manager";
    private static final String SAVE = "Save";
    private static final String PROMOTE = "Promote";
    // decision topbar-discard-reads-revert
    private static final String REVERT = "Revert";
    private static final String CLOSE = "X";
    private static final String DRAFT_BADGE = "draft";
    private static final String READING = "Reading the draft and the inventory; a fresh world dumps the inventory first.";
    private static final String SAVING = "saving";
    private static final String SAVED = "saved to the draft";
    private static final String UNSAVED = "unsaved changes";
    private static final String NOTHING_POSTED = "";
    private static final String NO_STATUS = "";

    private final ManagerConnection connection;
    private final ManagerDraft draft = new ManagerDraft();
    private final ValuesTab values = new ValuesTab(draft);
    private final GroupsTab groupsTab = new GroupsTab(draft);
    private final VariablesTab variablesTab = new VariablesTab(draft);
    private final ProcessesTab processesTab = new ProcessesTab(draft);
    private final AuditTab auditTab = new AuditTab();
    private final ItemIcons icons = new ItemIcons(values::worthOf);
    private final ClickTargets targets = new ClickTargets();
    private final Map<ManagerTab, Button> tabButtons = new EnumMap<>(ManagerTab.class);
    private final ValuesPane valuesPane;
    private final GroupsPane groupsPane;
    private final VariablesPane variablesPane;
    private final ProcessesPane processesPane;
    private final AuditPane auditPane;
    private final DraftConfirm draftConfirm = new DraftConfirm();
    private final DraftPane draftPane = new DraftPane(draftConfirm, this::runDraftAction);
    private boolean unsaved;
    private ManagerTab tab = ManagerTab.VALUES;
    private String status = READING;
    private boolean statusIsError;
    private boolean loaded;

    /**
     * @param connection the line to the server's workbench operations
     */
    public ValuesManagerScreen(ManagerConnection connection) {
        super(Component.literal(TITLE));
        this.connection = connection;
        ManagerActions actions = new ScreenActions();
        valuesPane = new ValuesPane(values, icons, actions, builderPane(actions));
        groupsPane = new GroupsPane(groupsTab, values, icons, actions, builderPane(actions));
        variablesPane = new VariablesPane(variablesTab, draft, icons, actions, builderPane(actions));
        processesPane = new ProcessesPane(processesTab, draft, actions, icons, () -> variablesTab.blocks().stream()
                .filter(block -> VariablesTab.TREE_KIND.equals(block.kind())).map(VariablesTab.ConstantBlock::name)
                .toList());
        auditPane = new AuditPane(auditTab, draft, actions);
    }

    // A builder over the server's items and the draft's prefabs (decision
    // expression-builder-composes-every-form-without-typing).
    private ExpressionBuilderPane builderPane(ManagerActions actions) {
        return new ExpressionBuilderPane(actions, icons, values::entries, () -> variablesTab.blocks().stream()
                .map(block -> new BuilderGrammar.Prefab(block.name(), VariablesTab.TREE_KIND.equals(block.kind())))
                .toList());
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
        int x = MARGIN;
        for (ManagerTab each : ManagerTab.values()) {
            int tabWidth = font.width(each.label()) + BUTTON_PADDING;
            Button button = Button.builder(Component.literal(each.label()), pressed -> selectTab(each))
                    .bounds(x, TAB_Y, tabWidth, BUTTON_HEIGHT).build();
            tabButtons.put(each, addRenderableWidget(button));
            x += tabWidth + TAB_GAP;
        }
        initTitleRow();
        valuesPane.init(font);
        groupsPane.init(font);
        variablesPane.init(font);
        processesPane.init(font);
        selectTab(tab);
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    // The topbar stands on the title row, right-aligned and sized to its labels, so the tab row keeps the width.
    // decision x-in-the-top-right-closes-the-manager
    private void initTitleRow() {
        TitleRow row = titleRowOf(width);
        addRenderableWidget(Button.builder(Component.literal(CLOSE), button -> onClose())
                .bounds(row.closeLeft(), TOPBAR_Y, BUTTON_HEIGHT, BUTTON_HEIGHT).build());
        int right = row.topbarRight();
        right = topbarButton(SAVE, right, () -> save(() -> { }));
        right = topbarButton(REVERT, right, () -> draftConfirm.askRevert(draft.path()));
        topbarButton(PROMOTE, right, () -> draftConfirm.askPromote(draft.path(), unsaved));
    }

    /**
     * Where the title row's controls stand across the screen's width.
     *
     * @param closeLeft   the X's left edge
     * @param closeRight  the X's right edge, at the screen's right margin
     * @param topbarRight the right edge of the topbar's first button
     */
    record TitleRow(int closeLeft, int closeRight, int topbarRight) {
    }

    /**
     * @param screenWidth the screen's width
     * @return the title row laid across it
     */
    static TitleRow titleRowOf(int screenWidth) {
        int closeRight = screenWidth - MARGIN;
        int closeLeft = closeRight - BUTTON_HEIGHT;
        return new TitleRow(closeLeft, closeRight, closeLeft - TAB_GAP);
    }

    /**
     * @return the topbar's labels in the order init lays them leftward from the title row's right edge
     */
    static List<String> topbarLabels() {
        return List.of(SAVE, REVERT, PROMOTE);
    }

    // Answers the x the next button to its left ends at.
    private int topbarButton(String label, int right, Runnable pressed) {
        int buttonWidth = font.width(label) + BUTTON_PADDING;
        addRenderableWidget(Button.builder(Component.literal(label), button -> pressed.run())
                .bounds(right - buttonWidth, TOPBAR_Y, buttonWidth, BUTTON_HEIGHT).build());
        return right - buttonWidth - TAB_GAP;
    }

    private GroupsPane.Bounds contentBounds() {
        return new GroupsPane.Bounds(MARGIN, CONTENT_Y, width - MARGIN, height - STATUS_INSET - STATUS_GAP);
    }

    private void selectTab(ManagerTab chosen) {
        tab = chosen;
        tabButtons.forEach((each, button) -> button.active = each != chosen);
        valuesPane.show(chosen == ManagerTab.VALUES);
        groupsPane.show(chosen == ManagerTab.GROUPS);
        variablesPane.show(chosen == ManagerTab.VARIABLES);
        processesPane.show(chosen == ManagerTab.PROCESSES);
    }

    private void load() {
        request(WorkbenchOperations.MODEL, NOTHING_POSTED, model -> {
            values.readModel(model);
            request(WorkbenchOperations.INVENTORY, NOTHING_POSTED, inventory -> {
                values.readInventory(inventory);
                setStatus(NO_STATUS, false);
                readDraftAfresh();
            });
        });
        auditPane.refresh();
    }

    /**
     * Has every tab read the draft afresh: the Values tab's resolve and uncovered items, the Groups tab's describe,
     * the Variables tab's evaluate and the Processes tab's description.
     */
    private void readDraftAfresh() {
        request(WorkbenchOperations.RESOLVE_VALUES, values.modelText(), resolved -> {
            values.readResolved(resolved);
        });
        request(WorkbenchOperations.UNCOVERED_VALUES, values.modelText(), values::readUncovered);
        request(WorkbenchOperations.DERIVED_FROM, values.modelText(), values::readDerivedFrom);
        groupsPane.refresh();
        variablesPane.refresh();
        processesPane.refresh();
    }

    /**
     * Writes the draft, which the server applies to every player at once
     * (decision save-applies-the-draft-live-promote-renames-it).
     *
     * @param after runs once the save answered
     */
    private void save(Runnable after) {
        setStatus(SAVING, false);
        request(WorkbenchOperations.SAVE_MODEL, values.modelText(), saved -> {
            values.readModel(saved);
            unsaved = false;
            setStatus(SAVED, false);
            readDraftAfresh();
            after.run();
        });
    }

    private void markUnsaved() {
        unsaved = true;
        setStatus(UNSAVED, false);
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
                        unsaved = false;
                        setStatus(draftConfirm.doneStatus(draft.path()), false);
                        draftConfirm.cancel();
                        readDraftAfresh();
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (draftPane.open()) {
            return targets.click(event.x(), event.y());
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        setFocused(null);
        return targets.click(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return targets.drag(event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return targets.release() || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return paneTakesKey(event.key()) || super.keyPressed(event);
    }

    private boolean paneTakesKey(int key) {
        return switch (tab) {
            case VALUES -> valuesPane.keyPressed(key);
            case GROUPS -> groupsPane.keyPressed(key);
            case VARIABLES -> variablesPane.keyPressed(key);
            case PROCESSES -> processesPane.keyPressed(key);
            default -> false;
        };
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (draftPane.open()) {
            return true;
        }
        switch (tab) {
            case VALUES -> valuesPane.scroll(x, MARGIN, scrollY);
            case GROUPS -> groupsPane.scroll(x, y, MARGIN, scrollY);
            case VARIABLES -> variablesPane.scroll(scrollY);
            case PROCESSES -> processesPane.scroll(scrollY);
            case AUDIT -> auditPane.scroll(y, scrollY);
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, PANEL_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        targets.clear();
        GroupsPane.Bounds content = contentBounds();
        switch (tab) {
            case VALUES -> valuesPane.extract(graphics, font, content, targets);
            case GROUPS -> groupsPane.extract(graphics, font, content, targets);
            case VARIABLES -> variablesPane.extract(graphics, font, content, targets, mouseX, mouseY);
            case PROCESSES -> processesPane.extract(graphics, font, content, targets);
            case AUDIT -> auditPane.extract(graphics, font, content, targets);
        }
        graphics.text(font, title, MARGIN, TITLE_Y, WHITE);
        if (draft.differs()) {
            graphics.text(font, DRAFT_BADGE, MARGIN + font.width(title) + TITLE_GAP, TITLE_Y, DRAFT_COLOR);
        }
        graphics.text(font, font.plainSubstrByWidth(status, width - MARGIN - MARGIN), MARGIN, height - STATUS_INSET,
                statusIsError ? RED : GRAY);
        draftPane.extract(graphics, font, width, height, targets);
        // decision goo-types-draw-as-icons-named-on-hover
        targets.hovered(mouseX, mouseY).flatMap(this::tooltipLines)
                .ifPresent(lines -> graphics.setComponentTooltipFromElementsForNextFrame(font, elementsOf(lines),
                        mouseX, mouseY, ItemStack.EMPTY));
    }

    // A goo type, a control or a truncated group name names itself on one line; an item or a prefab names itself,
    // then its value.
    private Optional<List<TooltipLine>> tooltipLines(String hoverKey) {
        Optional<TooltipLine> line = GooTypeIcons.tooltipFor(hoverKey).or(() -> BuilderOverlays.tooltipFor(hoverKey))
                .<TooltipLine>map(TooltipLine.Text::new).or(() -> GroupsPane.nameTooltipFor(hoverKey));
        return line.isPresent() ? Optional.of(List.of(line.get()))
                : icons.tooltipFor(hoverKey).or(() -> PrefabHovers.tooltipFor(variablesTab, hoverKey));
    }

    // Text stays text; a goo amount reaches GooAmountTooltipComponent through the tooltip factory registered for it,
    // and the deferred call draws the tooltip over the frame (decision
    // value-tooltips-draw-each-types-icon-before-its-amount).
    private static List<Either<FormattedText, TooltipComponent>> elementsOf(List<TooltipLine> lines) {
        return lines.stream().map(line -> switch (line) {
            case TooltipLine.Text text -> Either.<FormattedText, TooltipComponent>left(text.text());
            case TooltipLine.GooAmount amount -> Either.<FormattedText, TooltipComponent>right(amount);
        }).toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
