package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Draws the Values tab: the Items, Tags, Uncovered and Fractional set on a line of its own, the search box, each entry with its icon and where its
 * value comes from, and the selected entry's card above its expression, each list scrolling under the wheel, as
 * index.html's values section does (decision goo-workbench-opens-the-in-game-manager-screen); the list and the detail
 * each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ValuesTab}.
 */
final class ValuesPane {
    /** The Items list's width. */
    static final int SIDEBAR_WIDTH = 180;
    private static final int PANE_GAP = 12;
    /** A text line's height. */
    static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    /** An Items row's equality line's height, its sprite's and margin. */
    static final int ROW_HEIGHT = 18;
    /** An Items row's title line's height. */
    static final int TITLE_HEIGHT = LINE_HEIGHT;
    /** The pixel above an Items row's first line and below its last (decision items-row-pads-a-hair-all-round). */
    static final int ROW_PAD = 1;
    private static final int ICON_SIZE = 16;
    /** The room after an Items row's sprite and after its equals sign. */
    static final int ICON_GAP = 4;
    private static final String EQUALS = "=";
    private static final String PLUS = "+";
    private static final String FRACTION_DIVIDER = "/";
    private static final String MIXED_SPACE = " ";
    // Half scale doubles every coordinate the fraction draws at.
    private static final int FRACTION_SPACE = 2;
    // A stack count's right and bottom edges stand a pixel past the sprite's.
    private static final int COUNT_OVERHANG = 1;
    /** The room either side of the + between a recipe equivalency's inputs. */
    static final int EQUIVALENCY_GAP = 2;
    /** The room between the filter set and the search box below it. */
    static final int CHIP_LINE_GAP = 3;
    private static final int CHIP_TEXT_NUDGE = 3;
    /** The room between the detail page's card, its Value label and its error line. */
    static final int GAP = 3;
    /** The room between an Items row's name and its disposition symbol. */
    static final int SYMBOL_GAP = GAP;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    /** The color of no value, the one red source. */
    static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int DERIVED_COLOR = 0xFFA0C8F0;
    /** The room between two runs of an Items row's value. */
    static final int RUN_GAP = 3;
    /** The room between a run's type icon and its amount. */
    static final int RUN_ICON_GAP = 2;
    // The pixel above and the pixel below a value line's type icon.
    private static final int ICON_HAIR = 1;
    private static final int GOLD = 0xFFE0C060;
    private static final int CHIP_OFF = 0xFF808080;
    private static final int CHIP_HOLLOW = 0xFF202020;
    private static final int CHIP_FILLED_TEXT = 0xFF000000;
    private static final int SELECTED_ROW_COLOR = 0x60FFFFFF;
    private static final String ITEMS_CHIP = "Items";
    private static final String UNCOVERED_CHIP = "Uncovered ";
    private static final String FRACTIONAL_CHIP = "Fractional";
    private static final String SEARCH_HINT = "Search items";
    private static final String EXPRESSION_LABEL = "Value";
    private static final String NO_EXPRESSION = "(no entry)";
    private static final String REMOVE_ENTRY = "Remove entry";
    /** The Value label's line: tall enough for the Remove entry button standing on it, and a pixel under it. */
    static final int LABEL_LINE_HEIGHT = 12;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String NO_SELECTION = "Select an item to read and edit its entry.";
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String NO_TEXT = "";
    private static final int NO_REVEAL = -1;
    // A hover key no item id, prefab name or goo type key spells.
    private static final String GROUP_HOVER_PREFIX = "goo-group-members:";
    private static final int MEMBER_ICONS_SHOWN = 8;
    private static final Disposition VALUED_SYMBOL = new Disposition("✔", GREEN, false);
    private static final Disposition DERIVED_SYMBOL = new Disposition("=", GREEN, false);
    private static final Disposition GROUPED_SYMBOL = new Disposition("#", GREEN, false);
    private static final Disposition NO_VALUE_SYMBOL = new Disposition("?", RED, false);
    // The font's own cancel circle draws half size, so the denied symbol is drawn by pixel.
    private static final Disposition DENIED_SYMBOL = new Disposition("⊘", GREEN, true);
    // A glyph's ink stands this far short of the font's line height.
    private static final int GLYPH_LINE_MARGIN = 2;
    private static final double HALF_PIXEL = 0.5;

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final ScrollBar listBar = new ScrollBar(1);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private boolean visible;
    private int revealRow = NO_REVEAL;

    ValuesPane(ValuesTab values, ItemIcons icons, ManagerActions actions, ExpressionBuilderPane builder) {
        this.values = values;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        String searched = search == null ? NO_TEXT : search.getValue();
        search = actions.addWidget(new EditBox(font, 0, 0, SIDEBAR_WIDTH, BOX_HEIGHT, Component.literal(SEARCH_HINT)));
        search.setHint(Component.literal(SEARCH_HINT));
        search.setValue(searched);
        search.setResponder(text -> {
            values.search(text);
            listBar.reset();
        });
        builder.init(font);
        show(visible);
    }

    /**
     * @param shown whether the Values tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        if (search != null) {
            search.visible = shown;
        }
        builder.show(shown);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the builder took the key
     */
    boolean keyPressed(int key) {
        return builder.keyPressed(key);
    }

    /**
     * Selects an item under the Items filter, the search cleared, and scrolls the list to it on the next frame
     * (decision used-by-icons-link-to-items-and-hover-their-value).
     *
     * @param id the item's id
     */
    void reveal(String id) {
        int index = values.open(id);
        if (search != null) {
            search.setValue(NO_TEXT);
        }
        builder.close();
        detailBar.reset();
        revealRow = index;
    }

    /**
     * @param x      the cursor's x
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, int left, double amount) {
        if (builder.scrollList(amount)) {
            return;
        }
        if (x < left + SIDEBAR_WIDTH) {
            listBar.wheel(amount);
        } else {
            detailBar.wheel(amount);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        int y = drawChips(graphics, font, bounds.left(), bounds.top(), targets);
        if (search != null) {
            search.setPosition(bounds.left(), y);
        }
        drawEntries(graphics, font, new GroupsPane.Bounds(bounds.left(), y + BOX_HEIGHT + GAP,
                bounds.left() + SIDEBAR_WIDTH, bounds.bottom()), targets);
        drawSelected(graphics, font, new GroupsPane.Bounds(bounds.left() + SIDEBAR_WIDTH + PANE_GAP, bounds.top(),
                bounds.right(), bounds.bottom()), targets);
        builder.drawOverlays(graphics, font, targets);
    }

    /**
     * One box of the Items, Uncovered and Fractional set.
     *
     * @param filter what clicking it lists
     * @param label  its label
     * @param left   its left edge
     * @param top    its top edge
     * @param width  its width
     * @param height its height
     * @param filled whether it is the filter the tab holds
     */
    record Chip(ValuesTab.Filter filter, String label, int left, int top, int width, int height, boolean filled) {
    }

    /**
     * @param chips     the set's boxes, left to right
     * @param searchTop the search box's top, one line below them
     */
    record ChipLayout(List<Chip> chips, int searchTop) {
    }

    /**
     * Lays the filters as one segmented set on a line of their own: adjoining boxes sharing their inner borders,
     * the one the tab holds filled (decision items-filters-sit-on-their-own-line-as-one-set).
     *
     * @param labels   the Items, Uncovered and Fractional labels, in that order (decisions
     *                 items-selector-set-reads-items-and-uncovered, items-filter-set-takes-a-fractional-chip)
     * @param width    a label's width
     * @param left     the set's left edge
     * @param top      the set's top edge
     * @param selected the filter the tab holds
     * @return the boxes and the search box's top below them
     */
    static ChipLayout layChips(List<String> labels, ToIntFunction<String> width, int left, int top,
                               ValuesTab.Filter selected) {
        List<Chip> chips = new ArrayList<>();
        int x = left;
        for (int index = 0; index < labels.size(); index++) {
            ValuesTab.Filter filter = ValuesTab.Filter.values()[index];
            String label = labels.get(index);
            int chipWidth = width.applyAsInt(label) + ButtonPainter.PADDING + ButtonPainter.PADDING;
            chips.add(new Chip(filter, label, x, top, chipWidth, ButtonPainter.HEIGHT, filter == selected));
            x += chipWidth - 1;
        }
        return new ChipLayout(chips, top + ButtonPainter.HEIGHT + CHIP_LINE_GAP);
    }

    // Answers the search box's top below the set.
    private int drawChips(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        int uncovered = values.uncoveredCount();
        ChipLayout layout = layChips(List.of(ITEMS_CHIP, UNCOVERED_CHIP + uncovered, FRACTIONAL_CHIP),
                font::width, left, top, values.filter());
        for (Chip chip : layout.chips()) {
            drawChip(graphics, font, chip, chip.filter() == ValuesTab.Filter.UNCOVERED && uncovered > 0 ? RED : WHITE,
                    targets);
        }
        return layout.searchTop();
    }

    // The filled box reads its label dark on its color; a hollow one reads its label in the color, dimmed.
    private void drawChip(GuiGraphicsExtractor graphics, Font font, Chip chip, int color, ClickTargets targets) {
        int right = chip.left() + chip.width();
        int bottom = chip.top() + chip.height();
        graphics.fill(chip.left(), chip.top(), right, bottom, chip.filled() ? color : CHIP_OFF);
        graphics.fill(chip.left() + 1, chip.top() + 1, right - 1, bottom - 1, chip.filled() ? color : CHIP_HOLLOW);
        graphics.text(font, chip.label(), chip.left() + ButtonPainter.PADDING, chip.top() + CHIP_TEXT_NUDGE,
                chip.filled() ? CHIP_FILLED_TEXT : color);
        targets.add(chip.left(), chip.top(), chip.width(), chip.height(), () -> {
            values.filter(chip.filter());
            listBar.reset();
        });
    }

    private void drawEntries(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds list, ClickTargets targets) {
        List<ValuesTab.ManagerEntry> listed = values.listed();
        if (listed.isEmpty()) {
            graphics.text(font, NO_ENTRIES, list.left(), list.top(), GRAY);
            return;
        }
        scrollToRevealed();
        int firstShown = Math.min(listBar.offset(), listed.size() - 1);
        graphics.enableScissor(list.left(), list.top(), list.right(), list.bottom());
        targets.clip(list.left(), list.top(), list.right(), list.bottom());
        int whole = drawRowsFrom(graphics, font, listed, firstShown, list, targets);
        targets.unclip();
        graphics.disableScissor();
        listBar.measure(listed.size(), Math.max(1, whole));
        listBar.draw(graphics, targets, list.right(), list.top(), list.bottom());
    }

    // Draws rows from the first shown until the list's bottom; answers how many stood whole inside it.
    private int drawRowsFrom(GuiGraphicsExtractor graphics, Font font, List<ValuesTab.ManagerEntry> listed,
                             int firstShown, GroupsPane.Bounds list, ClickTargets targets) {
        Optional<String> selectedId = values.selectedRow().map(ValuesTab.EntryRow::id);
        int right = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        int y = list.top();
        int whole = 0;
        for (int index = firstShown; index < listed.size() && y < list.bottom(); index++) {
            ValuesTab.ManagerEntry entry = listed.get(index);
            RowLayout.drawSeparatorAbove(graphics, index - firstShown, y, list.left(), right);
            y += drawEntry(graphics, font, entry, new GroupsPane.Bounds(list.left(), y, right, list.bottom()),
                    selectedId.filter(entry.id()::equals).isPresent(), targets);
            whole += y <= list.bottom() ? 1 : 0;
        }
        return whole;
    }

    // One Items row: the title and its symbol on line 1, then the equality line led by the item's sprite, the goo value
    // on value lines beside or under it, a pixel above and below the whole; answers its height (decisions
    // items-row-second-line-is-the-equality-expression, goo-runs-follow-on-their-own-lines-after-line-two,
    // items-row-pads-a-hair-all-round).
    private int drawEntry(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, boolean selected, ClickTargets targets) {
        Card card = layCard(font, entry, row);
        int height = card.layout().height();
        if (selected) {
            graphics.fill(row.left(), row.top(), row.right(), row.top() + height, SELECTED_ROW_COLOR);
        }
        targets.add(row.left(), row.top(), row.right() - row.left(), height, () -> {
            values.select(entry.id());
            detailBar.reset();
            builder.close();
        });
        drawCard(graphics, font, card, targets);
        return height;
    }

    // An entry's card as laid at its bounds: where its value comes from, its goo worth and each part's place.
    private record Card(ValuesTab.ManagerEntry entry, Optional<ValuesTab.Source> source, Map<String, String> worth,
                        EntryLayout layout) {
    }

    private Card layCard(Font font, ValuesTab.ManagerEntry entry, GroupsPane.Bounds bounds) {
        Optional<ValuesTab.Source> source = Optional.of(values.sourceOf(entry.id()));
        ValuesTab.SourceKind kind = source.map(ValuesTab.Source::kind).orElse(ValuesTab.SourceKind.NONE);
        Map<String, String> worth = carriesRuns(kind) ? values.worthOf(entry.id()) : Map.of();
        List<Integer> runWidths = worth.entrySet().stream()
                .map(amount -> runWidth(font, amount.getValue(), GooTypeIcons.sizeOf(amount.getKey()))).toList();
        int iconSize = worth.keySet().stream().mapToInt(GooTypeIcons::sizeOf).max().orElse(0);
        int symbolWidth = source.map(shown -> symbolSize(dispositionOf(shown.kind()), font::width, font.lineHeight)
                .width()).orElse(0);
        return new Card(entry, source, worth, layEntry(bounds, runWidths, new RowText(kind, font.lineHeight,
                symbolWidth, font.width(EQUALS)), iconSize));
    }

    // The card the Items list draws and the detail page copies verbatim (decision
    // detail-pane-top-is-the-items-card-verbatim).
    private void drawCard(GuiGraphicsExtractor graphics, Font font, Card card, ClickTargets targets) {
        drawTitle(graphics, font, card.entry(), card.source(), card.layout().title());
        drawEquality(graphics, font, card.entry(), card.source(), card.layout().equality(), targets);
        drawValueRuns(graphics, font, card.worth(), card.layout(), targets);
    }

    // Valued, derived and grouped items show their goo value; no value and denied have none to show.
    private static boolean carriesRuns(ValuesTab.SourceKind kind) {
        return kind == ValuesTab.SourceKind.ENTRY || kind == ValuesTab.SourceKind.DERIVED
                || kind == ValuesTab.SourceKind.GROUP;
    }

    // The item's name and its disposition symbol at the row's right edge.
    private static void drawTitle(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                                  Optional<ValuesTab.Source> source, Title title) {
        graphics.text(font, font.plainSubstrByWidth(entry.name(), title.nameWidth()),
                title.nameLeft(), title.nameTop(), WHITE);
        source.map(shown -> dispositionOf(shown.kind())).ifPresent(symbol -> drawSymbol(graphics, font, symbol,
                title));
    }

    private static void drawSymbol(GuiGraphicsExtractor graphics, Font font, Disposition symbol, Title title) {
        if (symbol.drawn()) {
            int side = symbolSize(symbol, font::width, font.lineHeight).height();
            drawCancelCircle(graphics, title.symbolLeft(), title.nameTop(), side, symbol.color());
        } else {
            graphics.text(font, symbol.glyph(), title.symbolLeft(), title.nameTop(), symbol.color());
        }
    }

    // The sprite, then by disposition: "=" and what the value equals, or the no-value or denied words; a tag reads its
    // member count.
    private void drawEquality(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                              Optional<ValuesTab.Source> source, Equality line, ClickTargets targets) {
        graphics.item(icons.of(entry.id()), line.spriteLeft(), line.spriteTop());
        ValuesTab.Source shown = source.orElse(new ValuesTab.Source(entry.name(), ValuesTab.SourceKind.DENIED, false));
        if (!carriesRuns(shown.kind())) {
            graphics.text(font, font.plainSubstrByWidth(shown.label(), line.right() - line.equalsLeft()),
                    line.equalsLeft(), line.textTop(), colorOf(shown.kind()));
            return;
        }
        graphics.text(font, EQUALS, line.equalsLeft(), line.textTop(), WHITE);
        if (shown.kind() == ValuesTab.SourceKind.GROUP) {
            drawGroupLink(graphics, font, shown.label(), line, targets);
        } else if (shown.kind() == ValuesTab.SourceKind.DERIVED) {
            drawInputs(graphics, font, entry.id(), line, targets);
        }
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the group's members as one row of item icons where the area is a group button's, empty otherwise
     */
    Optional<List<TooltipLine>> groupTooltipFor(String hoverKey) {
        if (!hoverKey.startsWith(GROUP_HOVER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(List.of(memberIcons(values.groupMembers(hoverKey.substring(GROUP_HOVER_PREFIX.length())))));
    }

    /**
     * @param members a group's members, in member order
     * @return the first eight as icons, then ..+n counting the rest, no label where every member draws (decisions
     *     group-link-hovers-member-icons-then-plus-n, more-button-reads-dot-dot-plus-n)
     */
    static TooltipLine.MemberIcons memberIcons(List<String> members) {
        int shown = Math.min(members.size(), MEMBER_ICONS_SHOWN);
        int hidden = members.size() - shown;
        return new TooltipLine.MemberIcons(members.subList(0, shown), hidden > 0 ? UsedByLine.moreLabel(hidden)
                : NO_TEXT);
    }

    // The group's name as a button opening its group on the Groups tab, hovering its members (decisions
    // blue-text-options-draw-as-bordered-buttons, group-link-hovers-member-icons-then-plus-n).
    private void drawGroupLink(GuiGraphicsExtractor graphics, Font font, String group, Equality line,
                               ClickTargets targets) {
        String shown = font.plainSubstrByWidth(group,
                line.right() - line.besideLeft() - ButtonPainter.PADDING - ButtonPainter.PADDING);
        ButtonPainter.draw(graphics, font, shown, line.besideLeft(), line.spriteTop() - 1, ROW_HEIGHT, targets,
                () -> actions.openGroup(group), GROUP_HOVER_PREFIX + group);
    }

    // A derived item's recipe equivalency; one recipes value with no inputs to name reads derived.
    private void drawInputs(GuiGraphicsExtractor graphics, Font font, String id, Equality line, ClickTargets targets) {
        ValuesTab.Recipe recipe = values.derivedFrom(id);
        if (recipe.inputs().isEmpty()) {
            graphics.text(font, values.sourceOf(id).label(), line.besideLeft(), line.textTop(),
                    colorOf(ValuesTab.SourceKind.DERIVED));
            return;
        }
        drawEquivalency(graphics, font, recipe, line, targets);
    }

    // The inputs' sprites, each its summed count over the recipe's output at its bottom right as an inventory draws a
    // stack and a link to its own row, a small + between them (decision
    // recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
    private void drawEquivalency(GuiGraphicsExtractor graphics, Font font, ValuesTab.Recipe recipe, Equality line,
                                 ClickTargets targets) {
        Equivalency laid = layEquivalency(line.besideLeft(), recipe.inputs().size(), font.width(PLUS));
        for (int index = 0; index < recipe.inputs().size(); index++) {
            ValuesTab.Input input = recipe.inputs().get(index);
            int x = laid.spriteLefts().get(index);
            ItemStack stack = icons.of(input.id());
            graphics.item(stack, x, line.spriteTop());
            drawCount(graphics, font, stack, countParts(input.count(), recipe.output()), x, line.spriteTop());
            ItemLinks.add(targets, actions, input.id(), x, line.spriteTop());
        }
        laid.plusLefts().forEach(x -> graphics.text(font, PLUS, x, line.textTop(), WHITE));
    }

    // The whole part draws full size as an inventory draws a stack count; the fraction draws at half scale in the
    // sprite's bottom-right corner as its numerator over a bar over its denominator, the whole just left of it.
    private static void drawCount(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                                  StackedFractions.Mixed parts, int x, int y) {
        CornerCount placed = placeCornerCount(x, y, font.width(parts.numerator()), font.width(parts.denominator()));
        if (!parts.whole().isEmpty()) {
            graphics.itemDecorations(font, stack, placed.wholeX(), y, parts.whole());
        }
        if (parts.stacked()) {
            StackedFractions.drawStack(graphics, font, parts, placed.stack(), WHITE);
        }
    }

    /**
     * @param count  an input's summed count
     * @param output how many the recipe makes
     * @return the count over the output as a reduced mixed number, 2 over 6 reading 1/3 and 9 over 4 reading 2 1/4,
     *     empty where it equals 1
     */
    static Optional<String> countLabel(int count, int output) {
        StackedFractions.Mixed parts = countParts(count, output);
        String fraction = parts.stacked() ? parts.numerator() + FRACTION_DIVIDER + parts.denominator() : NO_TEXT;
        String joined = parts.whole().isEmpty() || fraction.isEmpty() ? parts.whole() + fraction
                : parts.whole() + MIXED_SPACE + fraction;
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    /**
     * Where an input count draws on its sprite.
     *
     * @param wholeX the x the whole part's stack decoration takes, so it ends just left of the stack
     * @param stack  the fraction's stack, in half-scale space
     */
    record CornerCount(int wholeX, StackedFractions.Stack stack) {
    }

    /**
     * Stands the half-scale fraction stack in the sprite's bottom-right corner, its right and bottom edges where a
     * stack count's stand, and moves the whole part left by the stack's drawn width (decision
     * recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     *
     * @param spriteLeft       the sprite's left edge
     * @param spriteTop        the sprite's top edge
     * @param numeratorWidth   the numerator's width at full scale, 0 where the count is whole
     * @param denominatorWidth the denominator's width at full scale, 0 where the count is whole
     * @return the whole part's x and the stack
     */
    static CornerCount placeCornerCount(int spriteLeft, int spriteTop, int numeratorWidth, int denominatorWidth) {
        int barWidth = Math.max(numeratorWidth, denominatorWidth);
        int right = (spriteLeft + ICON_SIZE + COUNT_OVERHANG) * FRACTION_SPACE;
        int bottom = (spriteTop + ICON_SIZE + COUNT_OVERHANG) * FRACTION_SPACE;
        int wholeX = barWidth == 0 ? spriteLeft : spriteLeft - barWidth / FRACTION_SPACE - 1;
        return new CornerCount(wholeX, StackedFractions.layStack(right - barWidth,
                bottom - StackedFractions.STACK_HEIGHT, numeratorWidth, denominatorWidth));
    }

    /**
     * @param count  an input's summed count
     * @param output how many the recipe makes
     * @return the count over the output, reduced, split into its whole part and its fraction, all empty where it
     *     equals 1
     */
    static StackedFractions.Mixed countParts(int count, int output) {
        int made = Math.max(1, output);
        return count == made ? StackedFractions.NONE : StackedFractions.of(count, made);
    }

    /**
     * Where a recipe equivalency's parts stand.
     *
     * @param spriteLefts each input sprite's left edge, in order
     * @param plusLefts   each + between inputs' left edge
     */
    record Equivalency(List<Integer> spriteLefts, List<Integer> plusLefts) {
    }

    /**
     * Lays a recipe equivalency's inputs from a left edge: each input sprite, a small + a gap either side between
     * inputs, ending on the last input (decision recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     *
     * @param left      the first sprite's left edge
     * @param inputs    how many distinct inputs the recipe takes
     * @param plusWidth the + sign's width
     * @return each part's place
     */
    static Equivalency layEquivalency(int left, int inputs, int plusWidth) {
        List<Integer> sprites = new ArrayList<>();
        List<Integer> pluses = new ArrayList<>();
        int x = left;
        for (int index = 0; index < inputs; index++) {
            if (index > 0) {
                pluses.add(x + EQUIVALENCY_GAP);
                x += EQUIVALENCY_GAP + plusWidth + EQUIVALENCY_GAP;
            }
            sprites.add(x);
            x += ICON_SIZE;
        }
        return new Equivalency(sprites, pluses);
    }

    // Each type icon at its texture's own size, one texel per pixel, a pixel under its value line's top, its amount
    // beside it (decisions items-value-line-draws-type-icons-at-eleven-texels, value-line-is-as-tall-as-its-icon).
    private static void drawValueRuns(GuiGraphicsExtractor graphics, Font font, Map<String, String> worth,
                                      EntryLayout layout, ClickTargets targets) {
        Iterator<Spot> spots = layout.runs().spots().iterator();
        for (Map.Entry<String, String> amount : worth.entrySet()) {
            Spot spot = spots.next();
            int x = layout.runsLeft() + spot.x();
            int size = GooTypeIcons.sizeOf(amount.getKey());
            GooTypeIcons.draw(graphics, targets, amount.getKey(), x, layout.iconTop(spot.row()), size);
            StackedFractions.draw(graphics, font, amount.getValue(), x + size + RUN_ICON_GAP,
                    layout.amountTop(spot.row()), WHITE);
        }
    }

    /**
     * @param font     the screen's font
     * @param amount   a goo type's amount as drawn
     * @param iconSize the type icon's side
     * @return the run's width: the type icon, its gap and the amount
     */
    static int runWidth(Font font, String amount, int iconSize) {
        return iconSize + RUN_ICON_GAP + StackedFractions.width(font, StackedFractions.of(amount));
    }

    /**
     * Where one goo type's run stands in an Items row's value.
     *
     * @param x   its offset from the value's left edge
     * @param row its row, from 0
     */
    record Spot(int x, int row) {
    }

    /**
     * @param rows  how many 18px rows the value takes, at least one
     * @param spots each run's place, in order
     */
    record ValueRuns(int rows, List<Spot> spots) {
    }

    /**
     * Lays an item's goo value as runs, one per type, wrapping onto a new row where the next run would pass the
     * width (decision items-rows-read-valued-derived-or-no-value-with-values-inline).
     *
     * @param runWidths each run's width, its type icon and amount
     * @param width     the width the value fills
     * @return the rows it takes and each run's place
     */
    static ValueRuns layValueRuns(List<Integer> runWidths, int width) {
        List<Spot> spots = new ArrayList<>();
        int x = 0;
        int row = 0;
        for (int runWidth : runWidths) {
            if (x > 0 && x + runWidth > width) {
                row++;
                x = 0;
            }
            spots.add(new Spot(x, row));
            x += runWidth + RUN_GAP;
        }
        return new ValueRuns(row + 1, spots);
    }

    /**
     * What an Items row's layout reads from its item and the font.
     *
     * @param kind        where the item's value comes from, which places its goo value; a tag lays as no value
     * @param lineHeight  the font's glyph line height
     * @param symbolWidth the disposition symbol's width, 0 for a row drawing none
     * @param equalsWidth the equals sign's width
     */
    record RowText(ValuesTab.SourceKind kind, int lineHeight, int symbolWidth, int equalsWidth) {
    }

    /**
     * How an item's value disposition reads on its title line.
     *
     * @param glyph the symbol
     * @param color its color
     * @param drawn whether it draws by pixel at the check's size rather than as the font's glyph
     */
    record Disposition(String glyph, int color, boolean drawn) {
    }

    /**
     * @param width  the room a symbol takes on its line
     * @param height its ink's height
     */
    record SymbolSize(int width, int height) {
    }

    /**
     * A glyph symbol takes its font width and the glyph ink's height; the drawn cancel circle takes the check's width
     * and a side that fits both the check's ink and the line (decision
     * denied-symbol-is-a-green-cancel-circle-at-symbol-size).
     *
     * @param symbol     a disposition symbol
     * @param width      a glyph's width in the font
     * @param lineHeight the font's line height
     * @return the room it takes and its ink's height
     */
    static SymbolSize symbolSize(Disposition symbol, ToIntFunction<String> width, int lineHeight) {
        int inkHeight = lineHeight - GLYPH_LINE_MARGIN;
        if (!symbol.drawn()) {
            return new SymbolSize(width.applyAsInt(symbol.glyph()), inkHeight);
        }
        int checkWidth = width.applyAsInt(VALUED_SYMBOL.glyph());
        return new SymbolSize(checkWidth, Math.min(checkWidth - 1, inkHeight));
    }

    // The cancel circle: a ring of the side's diameter and a slash from its top right to its bottom left.
    private static void drawCancelCircle(GuiGraphicsExtractor graphics, int left, int top, int side, int color) {
        double radius = (side - 1) / (double) HALF;
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                double distance = Math.hypot(column - radius, row - radius);
                boolean ring = Math.abs(distance - radius) < HALF_PIXEL;
                boolean slash = column + row == side - 1 && distance <= radius;
                if (ring || slash) {
                    graphics.fill(left + column, top + row, left + column + 1, top + row + 1, color);
                }
            }
        }
    }

    /**
     * Valued a green check, derived a green equals, grouped a green hash, no value a red question mark, denied a green
     * cancel circle at the check's size (decisions items-row-title-carries-a-disposition-symbol-at-the-right-edge,
     * denied-symbol-is-a-green-cancel-circle-at-symbol-size).
     *
     * @param kind where an item's value comes from
     * @return the symbol its title line reads
     */
    static Disposition dispositionOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> VALUED_SYMBOL;
            case DERIVED -> DERIVED_SYMBOL;
            case GROUP -> GROUPED_SYMBOL;
            case NONE -> NO_VALUE_SYMBOL;
            case DENIED -> DENIED_SYMBOL;
        };
    }

    /**
     * Where an Items row's goo value lines stand.
     *
     * @param left         their left edge
     * @param top          the first line's top
     * @param runs         the runs, none for an empty worth
     * @param lineHeight   a line's height, its type icon plus a pixel above and below
     * @param amountOffset an amount's top under its line's top, the glyph line centered on the icon
     * @param lines        how many lines the value takes, 0 for none
     */
    record ValueLines(int left, int top, ValueRuns runs, int lineHeight, int amountOffset, int lines) {
        int bottom() {
            return top + lines * lineHeight;
        }
    }

    /**
     * Where an Items row's title line stands.
     *
     * @param nameLeft   the name's left edge
     * @param nameTop    the name's and the symbol's top
     * @param nameWidth  the width the name is cut to
     * @param symbolLeft the disposition symbol's left edge
     */
    record Title(int nameLeft, int nameTop, int nameWidth, int symbolLeft) {
    }

    /**
     * Where an Items row's equality line stands.
     *
     * @param spriteLeft the item sprite's left edge
     * @param spriteTop  its top, and the top of every sprite on the line
     * @param equalsLeft the equals sign's left edge, or the no-value words'
     * @param besideLeft the left edge of what the item equals
     * @param textTop    the line's text top, the glyph line centered on it
     * @param right      the line's right edge
     */
    record Equality(int spriteLeft, int spriteTop, int equalsLeft, int besideLeft, int textTop, int right) {
    }

    /**
     * Where an Items row's parts stand.
     *
     * @param title    the title line
     * @param equality the equality line
     * @param value    the goo value's lines
     * @param height   the row's whole height
     */
    record EntryLayout(Title title, Equality equality, ValueLines value, int height) {
        int runsLeft() {
            return value.left();
        }

        int runsTop() {
            return value.top();
        }

        ValueRuns runs() {
            return value.runs();
        }

        /**
         * @param line a value line, from 0
         * @return its top
         */
        int lineTop(int line) {
            return value.top() + line * value.lineHeight();
        }

        /**
         * @param line a value line, from 0
         * @return its type icons' top, a pixel under the line's top
         */
        int iconTop(int line) {
            return lineTop(line) + ICON_HAIR;
        }

        /**
         * @param line a value line, from 0
         * @return its amounts' top, the glyph line centered on the icon
         */
        int amountTop(int line) {
            return lineTop(line) + value.amountOffset();
        }
    }

    /**
     * Lays an Items row a pixel inside its top and bottom: line 1 the name to the disposition symbol at the right
     * edge, line 2 an 18px line led by the item's sprite and the equals sign, then the goo value on lines each the type
     * icon plus a pixel above and below, beside the equals sign for a valued item and from line 3 for a derived or
     * grouped one (decisions items-row-title-carries-a-disposition-symbol-at-the-right-edge,
     * items-row-second-line-is-the-equality-expression, goo-runs-follow-on-their-own-lines-after-line-two,
     * items-row-pads-a-hair-all-round, value-line-is-as-tall-as-its-icon).
     *
     * @param row       the row's bounds, its left, top and right edges
     * @param runWidths each goo run's width
     * @param text      the item's disposition and the font's measures
     * @param iconSize  the goo type icons' side
     * @return each part's place and the row's height
     */
    static EntryLayout layEntry(GroupsPane.Bounds row, List<Integer> runWidths, RowText text, int iconSize) {
        int left = row.left() + ROW_PAD;
        int titleTop = row.top() + ROW_PAD;
        int symbolLeft = row.right() - text.symbolWidth();
        int nameWidth = text.symbolWidth() == 0 ? row.right() - left : symbolLeft - SYMBOL_GAP - left;
        int lineTwo = titleTop + TITLE_HEIGHT;
        int equalsLeft = left + ICON_SIZE + ICON_GAP;
        Equality equality = new Equality(left, lineTwo + 1, equalsLeft, equalsLeft + text.equalsWidth() + ICON_GAP,
                lineTwo + (ROW_HEIGHT - text.lineHeight()) / HALF, row.right());
        ValueLines value = layValueLines(runWidths, text, iconSize, equality, lineTwo);
        int bottom = Math.max(lineTwo + ROW_HEIGHT, value.bottom());
        return new EntryLayout(new Title(left, titleTop, nameWidth, symbolLeft), equality, value,
                bottom + ROW_PAD - row.top());
    }

    // A valued item's value starts beside the equals sign, centered on line 2; a derived or grouped item's starts on
    // line 3 at the row's left edge; the rest show none.
    private static ValueLines layValueLines(List<Integer> runWidths, RowText text, int iconSize, Equality equality,
                                            int lineTwo) {
        boolean beside = text.kind() == ValuesTab.SourceKind.ENTRY;
        int lineHeight = iconSize + ICON_HAIR * HALF;
        int left = beside ? equality.besideLeft() : equality.spriteLeft();
        int top = beside ? lineTwo + (ROW_HEIGHT - lineHeight) / HALF : lineTwo + ROW_HEIGHT;
        ValueRuns runs = layValueRuns(runWidths, equality.right() - left);
        int lines = runWidths.isEmpty() || !carriesRuns(text.kind()) ? 0 : runs.rows();
        return new ValueLines(left, top, runs, lineHeight, (lineHeight - text.lineHeight()) / HALF, lines);
    }

    private void scrollToRevealed() {
        if (revealRow != NO_REVEAL) {
            listBar.scrollTo(revealRow);
            revealRow = NO_REVEAL;
        }
    }

    /**
     * @param kind where an item's value comes from
     * @return its source's color, red for no value and nothing else
     */
    static int colorOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> GREEN;
            case GROUP -> GOLD;
            case DENIED -> GRAY;
            case DERIVED -> DERIVED_COLOR;
            case NONE -> RED;
        };
    }

    private void drawSelected(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds pane,
                              ClickTargets targets) {
        Optional<ValuesTab.EntryRow> selected = values.selectedRow();
        int x = pane.left();
        if (selected.isEmpty()) {
            graphics.text(font, NO_SELECTION, x, pane.top(), GRAY);
            return;
        }
        // The card, the value or its open builder, and the error line scroll as one page, so a builder taller than
        // the pane reaches its done control under the wheel (decision every-tab-draws-a-scroll-bar).
        GroupsPane.Bounds page = new GroupsPane.Bounds(x, pane.top(), pane.right() - ScrollBar.WIDTH - ScrollBar.GAP,
                pane.bottom());
        graphics.enableScissor(pane.left(), pane.top(), pane.right(), pane.bottom());
        targets.clip(pane.left(), pane.top(), pane.right(), pane.bottom());
        int end = drawPage(graphics, font, selected.get(), page, pane.top() - detailBar.offset(), targets);
        targets.unclip();
        graphics.disableScissor();
        detailBar.measureAndDraw(graphics, targets, end, pane.right(), pane.top(), pane.bottom());
    }

    // Draws the selected entry's page from a scrolled top: its Items card, the Value label, the value or its open
    // builder, and the loader error last (decision detail-pane-top-is-the-items-card-verbatim); answers the y its
    // content ended at.
    private int drawPage(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, GroupsPane.Bounds page,
                         int top, ClickTargets targets) {
        ValuesTab.ManagerEntry entry = new ValuesTab.ManagerEntry(row.id(), row.name(),
                row.id().startsWith(ValuesTab.TAG_MARK));
        Card card = layCard(font, entry, new GroupsPane.Bounds(page.left(), top, page.right(), page.bottom()));
        drawCard(graphics, font, card, targets);
        int cardHeight = card.layout().height();
        Page laid = layPage(top, cardHeight, 0, false);
        graphics.text(font, EXPRESSION_LABEL, page.left(), laid.labelTop(), GRAY);
        if (!builder.isOpen() && values.selectedHasEntry()) {
            ButtonPainter.drawAtText(graphics, font, REMOVE_ENTRY, page.left() + font.width(EXPRESSION_LABEL) + GAP
                    + ButtonPainter.PADDING, laid.labelTop(), targets, this::removeEntry);
        }
        int valueHeight = builder.isOpen() ? builder.extract(graphics, font, page, laid.valueTop(), targets)
                : drawValueView(graphics, font, row, page.left(), laid.valueTop(), page.right() - page.left(), targets);
        Page whole = layPage(top, cardHeight, valueHeight, !row.error().isEmpty());
        if (!row.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(row.error(), page.right() - page.left()), page.left(),
                    whole.errorTop(), RED);
        }
        return whole.end();
    }

    // A redundant entry, one recipes would derive, drops from the draft and the tabs read it afresh.
    private void removeEntry() {
        values.removeEntry();
        detailBar.reset();
        actions.draftChanged();
    }

    /**
     * Where the detail page's parts stand under its card.
     *
     * @param labelTop the Value label's top
     * @param valueTop the value's or its open builder's top
     * @param errorTop the loader error line's top
     * @param end      the page's end, below the error line where there is one
     */
    record Page(int labelTop, int valueTop, int errorTop, int end) {
    }

    /**
     * Lays the detail page down from its card: the Value label a gap under the card, the value on the line under the
     * label, and the loader error a gap under the value as the page's last line (decision
     * detail-pane-top-is-the-items-card-verbatim).
     *
     * @param top         the card's top
     * @param cardHeight  the card's height
     * @param valueHeight the value's or its open builder's height
     * @param hasError    whether the loader answered an error for the entry
     * @return each part's top and the page's end
     */
    static Page layPage(int top, int cardHeight, int valueHeight, boolean hasError) {
        int labelTop = top + cardHeight + GAP;
        int valueTop = labelTop + LABEL_LINE_HEIGHT;
        int errorTop = valueTop + valueHeight + GAP;
        return new Page(labelTop, valueTop, errorTop, errorTop + (hasError ? LINE_HEIGHT : 0));
    }

    // The entry's value curated, a plain left click on it opening the builder over it and a shift + left click on a
    // ref opening its prefab; answers the height it took.
    // Decision expression-builder-composes-every-form-without-typing.
    // Decision shift-left-click-follows-plain-left-modifies.
    private int drawValueView(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, int x, int y,
                              int width, ClickTargets targets) {
        List<ExpressionLines.Run> runs = row.expression().isBlank() ? ExpressionLines.textRuns(NO_EXPRESSION)
                : ExpressionLines.shownRuns(row.expression(), icons::nameOf);
        int height = ExpressionLines.height(font, runs, width);
        graphics.fill(x, y, x + width, y + height, READ_VIEW_COLOR);
        targets.add(x, y, width, height, () -> builder.open(row.expression(), typed -> {
            values.edit(typed);
            actions.markUnsaved();
        }, actions::draftChanged));
        ExpressionLines.draw(font, runs, x, y, width, new ExpressionLines.Painter(graphics, icons, targets, WHITE,
                LINK, ExpressionLines.followedOnShiftClick(targets, actions::openVariable)));
        return height;
    }
}
