package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The values manager /goo workbench opens: a tab bar carrying every tab of the page, the Values tab listing the
 * inventory's items and tags beside the selected entry's expression and resolved values, and Save writing the draft
 * on the server (decision goo-workbench-opens-the-in-game-manager-screen). The state lives in {@link ValuesTab} and
 * {@link ManagerConnection}; this class draws it and forwards input.
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
    private static final String TAG_PREFIX = "#";
    private static final String NOTHING_POSTED = "";
    private static final String NO_STATUS = "";

    private final ManagerConnection connection;
    private final ValuesTab values = new ValuesTab();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final Map<ManagerTab, Button> tabButtons = new EnumMap<>(ManagerTab.class);
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

    private void selectTab(ManagerTab chosen) {
        tab = chosen;
        tabButtons.forEach((each, button) -> button.active = each != chosen);
        boolean onValues = chosen == ManagerTab.VALUES;
        search.visible = onValues;
        expression.visible = onValues && values.selectedRow().isPresent();
    }

    private void load() {
        request(WorkbenchOperations.MODEL, NOTHING_POSTED, model -> {
            values.readModel(model);
            request(WorkbenchOperations.INVENTORY, NOTHING_POSTED, inventory -> {
                values.readInventory(inventory);
                setStatus(NO_STATUS, false);
                resolve();
            });
        });
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
            resolve();
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == ManagerTab.VALUES && x < MARGIN + SIDEBAR_WIDTH) {
            int most = Math.max(0, values.listed().size() - visibleRows());
            scroll = Math.clamp(scroll - (long) Math.signum(scrollY), 0, most);
            return true;
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
        graphics.text(font, title, MARGIN, TITLE_Y, WHITE);
        if (tab == ManagerTab.VALUES) {
            drawEntries(graphics);
            drawSelected(graphics);
        } else {
            graphics.centeredText(font, tab.label(), width / HALF, CONTENT_Y, GRAY);
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
            graphics.item(iconOf(entry.id()), MARGIN, y + HALF);
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
        graphics.item(iconOf(row.id()), x, CONTENT_Y - HALF);
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

    /**
     * Each item draws from its own ItemStack, with no capture and no image route in-game; a tag draws no icon.
     *
     * @param id the entry's item id or #tag
     * @return the item's stack, empty for a tag or an unknown id
     */
    private ItemStack iconOf(String id) {
        return icons.computeIfAbsent(id, key -> {
            Identifier parsed = key.startsWith(TAG_PREFIX) ? null : Identifier.tryParse(key);
            return parsed == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getValue(parsed).getDefaultInstance();
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
