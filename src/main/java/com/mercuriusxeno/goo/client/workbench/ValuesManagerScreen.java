package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The values manager /goo workbench opens: a tab bar carrying every tab of the page over one shared draft; the
 * Values tab lists the inventory's items and tags beside the selected entry's expression and resolved values, the
 * Groups and Variables tabs draw through their panes, and Save writes the draft on the server
 * (decision goo-workbench-opens-the-in-game-manager-screen). The state lives in {@link ValuesTab},
 * {@link GroupsTab}, {@link VariablesTab} and {@link ManagerConnection}; this class draws it and forwards input.
 */
public final class ValuesManagerScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TITLE_Y = 8;
    private static final int TAB_Y = 22;
    private static final int TAB_PADDING = 12;
    private static final int TAB_GAP = 2;
    private static final int CONTENT_Y = 48;
    private static final int SIDEBAR_WIDTH = 180;
    private static final int SEARCH_HEIGHT = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int TEXT_OFFSET_Y = 6;
    private static final int LINE_HEIGHT = 11;
    private static final int PANE_GAP = 12;
    private static final int EDIT_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int STATUS_INSET = 14;
    private static final int EXPRESSION_MAX_LENGTH = 32767;
    private static final int HALF = 2;
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int SELECTED_ROW_COLOR = 0x60FFFFFF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final String TITLE = "Values manager";
    private static final String SAVE = "Save";
    private static final String SEARCH_HINT = "Search items and tags";
    private static final String EXPRESSION_LABEL = "Expression";
    private static final String NO_SELECTION = "Select an item or tag to read and edit its entry.";
    private static final String DENIED_LINE = "denied";
    private static final String NO_VALUE_LINE = "values nothing";
    private static final String AMOUNT_SEPARATOR = "  ";
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
    private final ItemIcons icons = new ItemIcons();
    private final ClickTargets targets = new ClickTargets();
    private final Map<ManagerTab, Button> tabButtons = new EnumMap<>(ManagerTab.class);
    private final GroupsPane groupsPane;
    private final VariablesPane variablesPane;
    private ManagerTab tab = ManagerTab.VALUES;
    private EditBox search;
    private EditBox expression;
    private int scroll;
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
        groupsPane = new GroupsPane(groupsTab, values, icons, actions);
        variablesPane = new VariablesPane(variablesTab, draft, icons, actions);
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
        public void draftChanged() {
            setStatus(UNSAVED, false);
            readDraftAfresh();
        }

        @Override
        public void openVariable(String name) {
            selectTab(ManagerTab.VARIABLES);
            variablesPane.jumpTo(name);
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
            int tabWidth = font.width(each.label()) + TAB_PADDING;
            Button button = Button.builder(Component.literal(each.label()), pressed -> selectTab(each))
                    .bounds(x, TAB_Y, tabWidth, BUTTON_HEIGHT).build();
            tabButtons.put(each, addRenderableWidget(button));
            x += tabWidth + TAB_GAP;
        }
        addRenderableWidget(Button.builder(Component.literal(SAVE), pressed -> save())
                .bounds(width - MARGIN - BUTTON_WIDTH, TAB_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        search = addRenderableWidget(new EditBox(font, MARGIN, CONTENT_Y, SIDEBAR_WIDTH, SEARCH_HEIGHT,
                Component.literal(SEARCH_HINT)));
        search.setHint(Component.literal(SEARCH_HINT));
        search.setResponder(text -> {
            values.search(text);
            scroll = 0;
        });
        expression = addRenderableWidget(new EditBox(font, paneLeft(), CONTENT_Y + LINE_HEIGHT * HALF,
                width - paneLeft() - MARGIN, EDIT_HEIGHT, Component.literal(EXPRESSION_LABEL)));
        expression.setMaxLength(EXPRESSION_MAX_LENGTH);
        expression.setResponder(this::edited);
        groupsPane.init(font);
        variablesPane.init(font);
        selectTab(tab);
        refreshExpression();
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private int paneLeft() {
        return MARGIN + SIDEBAR_WIDTH + PANE_GAP;
    }

    private GroupsPane.Bounds contentBounds() {
        return new GroupsPane.Bounds(MARGIN, CONTENT_Y, width - MARGIN, height - STATUS_INSET - LINE_HEIGHT);
    }

    private void selectTab(ManagerTab chosen) {
        tab = chosen;
        tabButtons.forEach((each, button) -> button.active = each != chosen);
        boolean onValues = chosen == ManagerTab.VALUES;
        search.visible = onValues;
        expression.visible = onValues && values.selectedRow().isPresent();
        groupsPane.show(chosen == ManagerTab.GROUPS);
        variablesPane.show(chosen == ManagerTab.VARIABLES);
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
    }

    /**
     * Has every tab read the draft afresh: the Values tab's resolve, the Groups tab's describe and the Variables
     * tab's evaluate.
     */
    private void readDraftAfresh() {
        resolve();
        groupsPane.refresh();
        variablesPane.refresh();
    }

    private void resolve() {
        request(WorkbenchOperations.RESOLVE_VALUES, values.modelText(), resolved -> {
            values.readResolved(resolved);
            refreshExpression();
        });
    }

    private void save() {
        setStatus(SAVING, false);
        request(WorkbenchOperations.SAVE_MODEL, values.modelText(), saved -> {
            values.readModel(saved);
            setStatus(SAVED, false);
            readDraftAfresh();
        });
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

    private void edited(String typed) {
        Optional<ValuesTab.EntryRow> row = values.selectedRow();
        if (row.isPresent() && !row.get().expression().equals(typed)) {
            values.edit(typed);
            setStatus(UNSAVED, false);
        }
    }

    private void refreshExpression() {
        Optional<ValuesTab.EntryRow> row = values.selectedRow();
        expression.visible = tab == ManagerTab.VALUES && row.isPresent();
        row.ifPresent(selected -> {
            if (!expression.getValue().equals(selected.expression())) {
                expression.setValue(selected.expression());
            }
        });
    }

    private int visibleRows() {
        return Math.max(0, (height - CONTENT_Y - SEARCH_HEIGHT - STATUS_INSET * HALF) / ROW_HEIGHT);
    }

    private int listTop() {
        return CONTENT_Y + SEARCH_HEIGHT + ICON_GAP;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        setFocused(null);
        if (targets.click(event.x(), event.y())) {
            return true;
        }
        if (tab == ManagerTab.VALUES && event.x() >= MARGIN && event.x() < MARGIN + SIDEBAR_WIDTH
                && event.y() >= listTop()) {
            int index = scroll + (int) ((event.y() - listTop()) / ROW_HEIGHT);
            List<ValuesTab.ManagerEntry> listed = values.listed();
            if (index < listed.size() && index - scroll < visibleRows()) {
                values.select(listed.get(index).id());
                refreshExpression();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab == ManagerTab.VARIABLES && variablesPane.keyPressed(event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        switch (tab) {
            case VALUES -> {
                if (x < MARGIN + SIDEBAR_WIDTH) {
                    int most = Math.max(0, values.listed().size() - visibleRows());
                    scroll = Math.clamp(scroll - (long) Math.signum(scrollY), 0, most);
                    return true;
                }
            }
            case GROUPS -> {
                groupsPane.scroll(x, MARGIN, scrollY);
                return true;
            }
            case VARIABLES -> {
                variablesPane.scroll(scrollY);
                return true;
            }
            default -> {
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
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
        graphics.text(font, title, MARGIN, TITLE_Y, WHITE);
        switch (tab) {
            case VALUES -> {
                drawEntries(graphics);
                drawSelected(graphics);
            }
            case GROUPS -> groupsPane.extract(graphics, font, contentBounds(), targets);
            case VARIABLES -> variablesPane.extract(graphics, font, contentBounds(), targets, mouseX, mouseY);
            default -> graphics.centeredText(font, tab.label(), width / HALF, CONTENT_Y, GRAY);
        }
        graphics.text(font, status, MARGIN, height - STATUS_INSET, statusIsError ? RED : GRAY);
    }

    private void drawEntries(GuiGraphicsExtractor graphics) {
        List<ValuesTab.ManagerEntry> listed = values.listed();
        Optional<String> selectedId = values.selectedRow().map(ValuesTab.EntryRow::id);
        int y = listTop();
        int last = Math.min(listed.size(), scroll + visibleRows());
        for (int index = scroll; index < last; index++) {
            ValuesTab.ManagerEntry entry = listed.get(index);
            if (selectedId.isPresent() && selectedId.get().equals(entry.id())) {
                graphics.fill(MARGIN, y, MARGIN + SIDEBAR_WIDTH, y + ROW_HEIGHT, SELECTED_ROW_COLOR);
            }
            graphics.item(icons.of(entry.id()), MARGIN, y + HALF);
            int textX = MARGIN + ICON_SIZE + ICON_GAP;
            graphics.text(font, font.plainSubstrByWidth(entry.name(), SIDEBAR_WIDTH - ICON_SIZE - ICON_GAP),
                    textX, y + TEXT_OFFSET_Y, WHITE);
            y += ROW_HEIGHT;
        }
    }

    private void drawSelected(GuiGraphicsExtractor graphics) {
        Optional<ValuesTab.EntryRow> selected = values.selectedRow();
        int x = paneLeft();
        if (selected.isEmpty()) {
            graphics.text(font, NO_SELECTION, x, CONTENT_Y, GRAY);
            return;
        }
        ValuesTab.EntryRow row = selected.get();
        graphics.item(icons.of(row.id()), x, CONTENT_Y - HALF);
        graphics.text(font, row.name() + AMOUNT_SEPARATOR + row.id(), x + ICON_SIZE + ICON_GAP, CONTENT_Y + HALF,
                WHITE);
        graphics.text(font, EXPRESSION_LABEL, x, CONTENT_Y + LINE_HEIGHT + HALF, GRAY);
        int y = CONTENT_Y + LINE_HEIGHT * HALF + EDIT_HEIGHT + ICON_GAP;
        if (row.denied()) {
            graphics.text(font, DENIED_LINE, x, y, GRAY);
            y += LINE_HEIGHT;
        } else if (row.amounts().isEmpty()) {
            graphics.text(font, NO_VALUE_LINE, x, y, GRAY);
            y += LINE_HEIGHT;
        }
        for (Map.Entry<String, String> amount : row.amounts().entrySet()) {
            graphics.text(font, amount.getKey() + AMOUNT_SEPARATOR + amount.getValue(), x, y, WHITE);
            y += LINE_HEIGHT;
        }
        if (!row.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(row.error(), width - x - MARGIN), x, y, RED);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
