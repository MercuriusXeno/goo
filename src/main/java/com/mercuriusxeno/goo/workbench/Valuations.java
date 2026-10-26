package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRootRemainders;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Values tab's reads, each through the game's loader and derivation with no server: every entry resolved
 * or refused, the items nothing covers, and the hover card's recipe grid, ratios and value
 * (decision workbench-authors-base-values-whole).
 */
final class Valuations {
    private static final String ENTRIES = "entries";
    private static final String ITEMS = "items";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String DENIED = "denied";
    private static final String RESTRICTED = "restricted";
    private static final String ERROR = "error";
    private static final String DENIED_TEXT = "\"denied\"";
    private static final String RESTRICTED_SECTION = "_restricted";
    private static final String SECTION_PREFIX = "_";
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String CONTEXT_END = ":";
    private static final String WARNING_SEPARATOR = "; ";
    private static final String AIR = "minecraft:air";
    private static final String NONE = "none";
    private static final String AMOUNT_SEPARATOR = ", ";
    private static final String EQUALS = " = ";
    private static final String ONE = "1 ";
    private static final int GRID_SIDE = 3;
    // A narrow pattern is centred by splitting the grid's spare cells evenly to either side.
    private static final int SIDES = 2;
    private static final String BLANK = "";
    private static final String SPACE = " ";
    private static final String ROWS = "rows";
    private static final String ROW_ID = "id";
    private static final String ROW_NAME = "name";
    private static final String ROW_DEFINED_AS = "definedAs";
    private static final String ROW_AMOUNTS = "amounts";
    private static final String ROW_SOURCE = "source";
    private static final String SOURCE_ENTRY = "entry";
    private static final String SOURCE_GROUP = "group #";
    private static final String ROW_FROM = "from";
    private static final String FROM_KIND = "kind";
    private static final String FROM_ANCESTOR = "ancestor";
    private static final String FROM_RECIPE = "recipe";
    private static final String FROM_ITEM = "item";
    private static final String FROM_CHAIN = "chain";
    private static final String FROM_PRIME = "prime";
    private static final String FROM_GRID = "grid";
    private static final String FROM_COUNT = "count";
    private static final String FAMILY_ROOT = "root";
    private static final String ROOT_SOLVED = "solved";
    private static final String ROOT_SMALLEST_REMAINDER = "smallestRemainder";
    private static final String ROW_REMAINDER = "remainder";
    private static final String ROW_REMAINDER_EXACT = "remainderExact";
    // Rows whose recipe leaves a remainder at the root lead, the smallest first, then the recipes dividing whole;
    // a row with no recipe among the root's trails them all.
    private static final Comparator<GooRootRemainders.Row> REMAINDER_ORDER = Comparator
            .comparing((GooRootRemainders.Row row) -> row.remainder().isZero())
            .thenComparing(GooRootRemainders.Row::remainder);
    // An entry reading {}: an object of no types, which values nothing.
    private static final Pattern EMPTY_OBJECT = Pattern.compile("\\s*\\{\\s*}\\s*");

    private static String lastDerivedText;
    private static InventoryView lastDerivedInventory;
    private static GooBaseValuesPreview.Derived lastDerived;

    private Valuations() {
    }

    /**
     * The hover card for one item.
     *
     * @param grid   the recipe grid's nine cells in reading order, each an item id or null for an empty cell
     * @param count  the recipe's result count, 0 where no recipe makes the item
     * @param ratios the ratios through the item, each "1 item = k ingredient"
     * @param value  the item's value per type
     * @param tags   the item tags holding it
     */
    // values-tab-drops-melts-and-costs
    record Hover(List<String> grid, int count, List<String> ratios, String value, List<String> tags) {
    }

    /**
     * @param model the model
     * @return each item and #group entry as the loader values it: its amount per type, its denial, its
     *         restriction and the validator's warnings for it
     * @throws IOException where the model's text cannot be read
     */
    static String resolved(BaseValuesModel model) throws IOException {
        return resolved(model, GooBaseValuesPreview.load(model.serialize()));
    }

    /**
     * @param model     the model
     * @param inventory the inventory, whose tags a #tag entry expands through
     * @return each item and #group entry as the loader values it
     */
    static String resolved(BaseValuesModel model, InventoryView inventory) {
        JsonObject root = resolvedRoot(model, GooBaseValuesPreview.load(model.serialize(), inventory.tagResolver()));
        JsonArray entries = root.getAsJsonArray(ENTRIES);
        // An item no entry names reads the value its recipes derive, so the Items tab shows what the game gives it
        // (decision derivation-solves-whichever-side-of-a-recipe-is-unknown).
        Map<Identifier, GooValue> effective = derivedFor(model, inventory).effective();
        for (String id : inventory.itemIds()) {
            Identifier item = Identifier.tryParse(id);
            GooValue value = item == null ? null : effective.get(item);
            if (valuesNothing(value) || model.member(bare(id)) != null || model.member(id) != null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty(KEY, bare(id));
            entry.add(VALUE, amountsJson(value));
            entry.addProperty(DENIED, false);
            entry.addProperty(RESTRICTED, false);
            entry.add(ERROR, JsonNull.INSTANCE);
            entries.add(entry);
        }
        return root.toString();
    }

    private static String resolved(BaseValuesModel model, GooBaseValuesPreview.Loaded loaded) {
        return resolvedRoot(model, loaded).toString();
    }

    private static JsonObject resolvedRoot(BaseValuesModel model, GooBaseValuesPreview.Loaded loaded) {
        JsonArray entries = new JsonArray();
        for (BaseValuesModel.Member member : model.members()) {
            if (member.kind() != BaseValuesModel.Kind.VALUE || member.key().startsWith(SECTION_PREFIX)) {
                continue;
            }
            Identifier valued = member.key().startsWith(TAG_PREFIX)
                    ? firstMember(model, member.key().substring(TAG_PREFIX.length()))
                    : Identifier.tryParse(member.key());
            JsonObject entry = new JsonObject();
            entry.addProperty(KEY, member.key());
            entry.add(VALUE, amountsJson(valued == null ? null : loaded.baseValues().get(valued)));
            entry.addProperty(DENIED, DENIED_TEXT.equals(member.text().trim()));
            entry.addProperty(RESTRICTED, valued != null && loaded.restricted().contains(valued));
            List<String> warnings = loaded.warnings().stream()
                    .filter(warning -> warning.startsWith(member.key() + CONTEXT_END)).toList();
            if (warnings.isEmpty()) {
                entry.add(ERROR, com.google.gson.JsonNull.INSTANCE);
            } else {
                entry.addProperty(ERROR, String.join(WARNING_SEPARATOR, warnings));
            }
            entries.add(entry);
        }
        JsonObject root = new JsonObject();
        root.add(ENTRIES, entries);
        return root;
    }

    private static @Nullable Identifier firstMember(BaseValuesModel model, String group) {
        return GroupConvention.describe(model).stream().filter(described -> group.equals(described.name()))
                .flatMap(described -> described.members().stream()).findFirst().map(Identifier::tryParse)
                .orElse(null);
    }

    private static JsonObject amountsJson(@Nullable GooValue value) {
        JsonObject amounts = new JsonObject();
        if (value != null) {
            value.exactAll().forEach((type, amount) -> amounts.addProperty(GooTypes.id(type), amount.toString()));
        }
        return amounts;
    }

    /**
     * @param model the model
     * @return every item id the file names, an entry's, a group member's or a restriction's, in file order
     */
    static List<String> namedIds(BaseValuesModel model) {
        Set<String> ids = new LinkedHashSet<>();
        for (BaseValuesModel.Member member : model.members()) {
            if (member.kind() == BaseValuesModel.Kind.VALUE && !member.key().startsWith(SECTION_PREFIX)
                    && !member.key().startsWith(TAG_PREFIX)) {
                ids.add(namespaced(member.key()));
            }
        }
        GroupConvention.describe(model).forEach(group -> ids.addAll(group.members()));
        BaseValuesModel.Member restricted = model.member(RESTRICTED_SECTION);
        if (restricted != null) {
            for (BaseValuesModel.Member entry : restricted.entries()) {
                String id = JsonParser.parseString(entry.text()).getAsString();
                if (!id.startsWith(TAG_PREFIX)) {
                    ids.add(namespaced(id));
                }
            }
        }
        return List.copyOf(ids);
    }

    /**
     * A missing value is an item the Values tab reads as none: no entry valuing it, no #group entry over it, no
     * derivation and no value from any other line (decision inventory-hydrates-the-tool-and-writes-no-template).
     *
     * @param model     the model
     * @param inventory the inventory's item ids, valued with no recipes
     * @return the items reading as none, in inventory order
     */
    static List<String> uncovered(BaseValuesModel model, List<String> inventory) {
        String text = model.serialize();
        return uncovered(model, inventory, GooBaseValuesPreview.derive(text, List.of()),
                GooBaseValuesPreview.load(text));
    }

    /**
     * @param model     the model
     * @param inventory the inventory, whose recipes derive and whose tags a #tag entry expands through
     * @return the items reading as none, in inventory order
     */
    static List<String> uncovered(BaseValuesModel model, InventoryView inventory) {
        return uncovered(model, inventory.itemIds(), derivedFor(model, inventory),
                GooBaseValuesPreview.load(model.serialize(), inventory.tagResolver()));
    }

    private static List<String> uncovered(BaseValuesModel model, List<String> inventory,
                                          GooBaseValuesPreview.Derived derived, GooBaseValuesPreview.Loaded loaded) {
        Set<String> grouped = groupedMembers(model);
        return inventory.stream().filter(id -> !AIR.equals(id) && !grouped.contains(id)).filter(id -> {
            Identifier item = Identifier.tryParse(id);
            return item != null && valuesNothing(derived.effective().get(item))
                    && valuesNothing(loaded.baseValues().get(item))
                    && NONE.equals(sourceOf(model, derived, loaded, item, null));
        }).toList();
    }

    private static boolean valuesNothing(@Nullable GooValue value) {
        return value == null || value.isEmpty();
    }

    private static Set<String> groupedMembers(BaseValuesModel model) {
        Set<String> grouped = new HashSet<>();
        GroupConvention.describe(model).stream().filter(group -> model.member(TAG_PREFIX + group.name()) != null)
                .forEach(group -> grouped.addAll(group.members()));
        return grouped;
    }

    /**
     * An entry reading {} values nothing, so it stands as no entry at all and hides no missing value.
     *
     * @param model the model
     * @param id    the item's namespaced id
     * @return the item's own entry, or null where none stands or it reads {}
     */
    private static BaseValuesModel.@Nullable Member ownEntry(BaseValuesModel model, String id) {
        BaseValuesModel.Member own = model.member(bare(id)) != null ? model.member(bare(id)) : model.member(id);
        return own == null || EMPTY_OBJECT.matcher(own.text()).matches() ? null : own;
    }

    /**
     * Where an item's value comes from, as the Values tab names it: denied, its entry, its group, a
     * derivation (blank, the row's from naming it) or none.
     *
     * @param model   the model
     * @param derived the derivation over the inventory's recipes
     * @param loaded  the loader's reading of the model
     * @param member  the item
     * @param group   the group valuing the item, or null where none does
     * @return the source's name
     */
    private static String sourceOf(BaseValuesModel model, GooBaseValuesPreview.Derived derived,
                                   GooBaseValuesPreview.Loaded loaded, Identifier member, @Nullable String group) {
        String id = member.toString();
        return loaded.denied().contains(member) ? DENIED
                : ownEntry(model, id) != null ? SOURCE_ENTRY
                : group != null ? SOURCE_GROUP + group
                : derived.derived().contains(member) ? BLANK : NONE;
    }

    /**
     * @param model     the model
     * @param inventory the inventory
     * @param id        the item's id
     * @return its hover card, valued by the game's loader and derivation over the inventory's recipes
     */
    static Hover hover(BaseValuesModel model, InventoryView inventory, String id) {
        GooBaseValuesPreview.Derived derived = derivedFor(model, inventory);
        Map<Identifier, GooValue> values = derived.effective();
        GooValue value = values.get(Identifier.parse(id));
        InventoryView.Recipe recipe = recipeFor(id, inventory, derived.recipes());
        List<String> grid = recipe == null ? Arrays.asList(new String[GRID_SIDE * GRID_SIDE]) : grid(recipe);
        List<String> ratios = recipe == null || value == null ? List.of() : ratios(id, value, recipe, values);
        return new Hover(grid, recipe == null ? 0 : recipe.count(), ratios, amounts(value, GooFraction::toString),
                inventory.tagsOf(id));
    }

    /**
     * The Values table for an item's family: the item, the ingredients of the recipe that values it, and the
     * items whose valuing recipe takes it, each with how the file defines it, its amounts and where its value
     * comes from.
     *
     * @param model     the model
     * @param inventory the inventory
     * @param id        the selected item's id
     * @return the rows as JSON
     * @throws IOException where the model's text cannot be read
     */
    static String family(BaseValuesModel model, InventoryView inventory, String id) throws IOException {
        GooBaseValuesPreview.Derived derived = derivedFor(model, inventory);
        GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(model.serialize(), inventory.tagResolver());
        Identifier selected = Identifier.parse(id);
        Set<Identifier> members = new LinkedHashSet<>();
        members.add(selected);
        for (RecipeInput recipe : derived.recipes()) {
            if (recipe.output().equals(selected)) {
                recipe.ingredientAlternatives().forEach(slot -> slot.stream().findFirst().ifPresent(members::add));
            }
        }
        for (RecipeInput recipe : derived.recipes()) {
            if (recipe.ingredientAlternatives().stream().anyMatch(slot -> slot.contains(selected))) {
                members.add(recipe.output());
            }
        }
        GooRootRemainders.RootSolution rootSolution = rootOf(selected,
                GooRootRemainders.solveEachRoot(derived.registry()));
        Map<RecipeInput, GooRootRemainders.Row> atRoot = new HashMap<>();
        if (rootSolution != null) {
            rootSolution.rows().forEach(row -> atRoot.put(row.recipe(), row));
        }
        Map<Identifier, RecipeInput> setting = derived.registry().valueSettingRecipeByItem();
        List<Identifier> ordered = new ArrayList<>(members);
        ordered.sort(Comparator.comparing(member -> atRoot.get(setting.get(member)),
                Comparator.nullsLast(REMAINDER_ORDER)));
        JsonArray rows = new JsonArray();
        for (Identifier member : ordered) {
            JsonObject row = familyRow(model, inventory, derived, loaded, member);
            GooRootRemainders.Row remainder = atRoot.get(setting.get(member));
            if (remainder != null) {
                row.addProperty(ROW_REMAINDER, GooFormat.formatMixed(remainder.remainder()));
                row.addProperty(ROW_REMAINDER_EXACT, remainder.remainder().toString());
            }
            rows.add(row);
        }
        JsonObject root = new JsonObject();
        root.add(FAMILY_ROOT, rootJson(rootSolution, inventory));
        root.add(ROWS, rows);
        return root.toString();
    }

    /**
     * The root is the unit of the family: the selected item where _roots names it, else the first root that
     * solves it (decision workbench-shows-each-roots-remainders-and-items-solved).
     *
     * @param selected  the selected item
     * @param solutions each root's solution
     * @return the family's root solution, or null where no root solves the item
     */
    private static GooRootRemainders.@Nullable RootSolution rootOf(Identifier selected,
                                                                  List<GooRootRemainders.RootSolution> solutions) {
        return solutions.stream().filter(solution -> solution.root().equals(selected)).findFirst()
                .or(() -> solutions.stream().filter(solution -> solution.solved().contains(selected)).findFirst())
                .orElse(null);
    }

    private static JsonElement rootJson(GooRootRemainders.@Nullable RootSolution solution, InventoryView inventory) {
        if (solution == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject root = new JsonObject();
        root.addProperty(ROW_ID, solution.root().toString());
        root.addProperty(ROW_NAME, inventory.nameOf(solution.root().toString()));
        root.addProperty(ROOT_SOLVED, solution.solved().size());
        root.addProperty(ROOT_SMALLEST_REMAINDER, GooFormat.formatMixed(solution.smallestRemainder()));
        return root;
    }

    private static JsonObject familyRow(BaseValuesModel model, InventoryView inventory,
                                        GooBaseValuesPreview.Derived derived, GooBaseValuesPreview.Loaded loaded,
                                        Identifier member) {
        String id = member.toString();
        GooValue value = derived.effective().get(member);
        BaseValuesModel.Member own = ownEntry(model, id);
        String group = groupValuing(model, id);
        JsonObject row = new JsonObject();
        row.addProperty(ROW_ID, id);
        row.addProperty(ROW_NAME, inventory.nameOf(id));
        row.addProperty(ROW_DEFINED_AS, own != null ? own.text()
                : group != null ? TAG_PREFIX + group + CONTEXT_END + SPACE + model.member(TAG_PREFIX + group).text()
                : BLANK);
        row.add(ROW_AMOUNTS, amountsJson(value));
        row.addProperty(ROW_SOURCE, sourceOf(model, derived, loaded, member, group));

        if (own == null && group == null && derived.derived().contains(member)) {
            row.add(ROW_FROM, from(member, inventory, derived, loaded.baseValues().keySet()));
        }
        return row;
    }

    /**
     * The items each derived item's value comes from, as the Items tab draws them after "derived from": for every
     * inventory item no entry or group values but recipes do, the recipe inputs its family row reports in its from
     * field, the first link of its ancestor chain or the distinct items its recipe grid takes
     * (decision items-rows-read-valued-derived-or-no-value-with-values-inline).
     *
     * @param model     the model
     * @param inventory the inventory, whose recipes derive
     * @return {"items": {id: [input ids]}} as JSON
     * @throws IOException where the model's text cannot be read
     */
    static String derivedFrom(BaseValuesModel model, InventoryView inventory) throws IOException {
        GooBaseValuesPreview.Derived derived = derivedFor(model, inventory);
        GooBaseValuesPreview.Loaded loaded = GooBaseValuesPreview.load(model.serialize(), inventory.tagResolver());
        JsonObject items = new JsonObject();
        for (String id : inventory.itemIds()) {
            Identifier item = Identifier.tryParse(id);
            if (item == null || !derived.derived().contains(item) || ownEntry(model, id) != null
                    || groupValuing(model, id) != null) {
                continue;
            }
            JsonArray inputs = new JsonArray();
            inputsOf(from(item, inventory, derived, loaded.baseValues().keySet())).forEach(inputs::add);
            items.add(id, inputs);
        }
        JsonObject root = new JsonObject();
        root.add(ITEMS, items);
        return root.toString();
    }

    private static Set<String> inputsOf(JsonObject from) {
        Set<String> inputs = new LinkedHashSet<>();
        if (FROM_ANCESTOR.equals(from.get(FROM_KIND).getAsString())) {
            inputs.add(from.getAsJsonArray(FROM_CHAIN).get(0).getAsString());
            return inputs;
        }
        from.getAsJsonArray(FROM_GRID).forEach(cell -> {
            if (!cell.isJsonNull()) {
                inputs.add(cell.getAsString());
            }
        });
        return inputs;
    }

    /**
     * Where a derived item's value comes from: up the chain of single-input recipes to the prime ancestor, the
     * first item the file values rather than derives, though a recipe may make it too, as nine raw iron from a
     * block; or, where the item's own recipe mixes inputs, that recipe. A chain stopping at a mixed recipe, or
     * looping back on itself, ends at an ancestor that is not prime.
     *
     * @param item      the derived item
     * @param inventory the inventory, whose recipe grid a mixed recipe draws
     * @param derived   the derivation over the inventory's recipes
     * @param handKeyed the items the file values directly
     * @return the ancestor chain, or the item's recipe grid, as JSON
     */
    private static JsonObject from(Identifier item, InventoryView inventory, GooBaseValuesPreview.Derived derived,
                                   Set<Identifier> handKeyed) {
        Map<Identifier, RecipeInput> byOutput = new HashMap<>();
        derived.recipes().forEach(recipe -> byOutput.putIfAbsent(recipe.output(), recipe));
        List<Identifier> chain = new ArrayList<>();
        Set<Identifier> seen = new HashSet<>(Set.of(item));
        Identifier current = item;
        RecipeInput recipe = byOutput.get(current);
        Identifier input = recipe == null ? null : soleInput(recipe);
        while (input != null && seen.add(input)) {
            chain.add(input);
            current = input;
            recipe = handKeyed.contains(current) ? null : byOutput.get(current);
            input = recipe == null ? null : soleInput(recipe);
        }
        JsonObject from = new JsonObject();
        if (chain.isEmpty()) {
            InventoryView.Recipe drawn = recipeFor(item.toString(), inventory, derived.recipes());
            from.addProperty(FROM_KIND, FROM_RECIPE);
            JsonArray grid = new JsonArray();
            (drawn == null ? Arrays.asList(new String[GRID_SIDE * GRID_SIDE]) : grid(drawn)).forEach(grid::add);
            from.add(FROM_GRID, grid);
            from.addProperty(FROM_COUNT, drawn == null ? 0 : drawn.count());
            return from;
        }
        from.addProperty(FROM_KIND, FROM_ANCESTOR);
        from.addProperty(FROM_ITEM, current.toString());
        JsonArray ids = new JsonArray();
        chain.forEach(id -> ids.add(id.toString()));
        from.add(FROM_CHAIN, ids);
        from.addProperty(FROM_PRIME, handKeyed.contains(current));
        return from;
    }

    /**
     * @param recipe a valuing recipe
     * @return the one item every slot takes, or null where the slots mix items or take a tag's alternatives
     */
    private static @Nullable Identifier soleInput(RecipeInput recipe) {
        Set<Set<Identifier>> slots = new HashSet<>(recipe.ingredientAlternatives());
        if (slots.size() != 1) {
            return null;
        }
        Set<Identifier> only = slots.iterator().next();
        return only.size() == 1 ? only.iterator().next() : null;
    }

    private static @Nullable String groupValuing(BaseValuesModel model, String id) {
        return GroupConvention.describe(model).stream()
                .filter(group -> group.members().contains(id) && model.member(TAG_PREFIX + group.name()) != null)
                .map(GroupConvention.Group::name).findFirst().orElse(null);
    }

    /**
     * Derives once per model text and inventory: a hover and a family read derive over every recipe the
     * loadout holds, so the last derivation answers until the text changes.
     *
     * @param model     the model
     * @param inventory the inventory, whose recipes derive
     * @return the derivation for the model's text and the inventory
     */
    private static synchronized GooBaseValuesPreview.Derived derivedFor(BaseValuesModel model,
                                                                      InventoryView inventory) {
        String text = model.serialize();
        if (!text.equals(lastDerivedText) || inventory != lastDerivedInventory) {
            lastDerived = GooBaseValuesPreview.derive(text, inventory.recipeInputs(), inventory.tagResolver());
            lastDerivedText = text;
            lastDerivedInventory = inventory;
        }
        return lastDerived;
    }

    /**
     * The inventory recipe the derivation chose for the item, else the first making it.
     *
     * @param id        the item's id
     * @param inventory the inventory
     * @param winning   the recipes the derivation chose
     * @return the recipe, or null where none makes the item
     */
    private static InventoryView.@Nullable Recipe recipeFor(String id, InventoryView inventory,
                                                           List<RecipeInput> winning) {
        Identifier output = Identifier.parse(id);
        RecipeInput chosen = winning.stream().filter(recipe -> recipe.output().equals(output)).findFirst()
                .orElse(null);
        List<InventoryView.Recipe> making = inventory.recipes().stream().filter(recipe -> id.equals(recipe.output()))
                .toList();
        return making.stream().filter(recipe -> chosen != null
                        && recipe.toInput().ingredientAlternatives().equals(chosen.ingredientAlternatives()))
                .findFirst().orElse(making.isEmpty() ? null : making.getFirst());
    }

    /**
     * A shaped recipe's pattern centred in the grid, as the mockup draws a narrow one; any other's slots in
     * reading order.
     *
     * @param recipe the recipe
     * @return the grid's nine cells in reading order, null for an empty cell
     */
    private static List<String> grid(InventoryView.Recipe recipe) {
        String[] cells = new String[GRID_SIDE * GRID_SIDE];
        if (recipe.pattern().isEmpty()) {
            for (int i = 0; i < recipe.slots().size() && i < cells.length; i++) {
                cells[i] = recipe.slots().get(i).alternatives().getFirst();
            }
            return Arrays.asList(cells);
        }
        int height = recipe.pattern().size();
        int width = recipe.pattern().stream().mapToInt(String::length).max().orElse(0);
        int top = (GRID_SIDE - height) / SIDES;
        int left = (GRID_SIDE - width) / SIDES;
        for (int row = 0; row < height; row++) {
            String line = recipe.pattern().get(row);
            for (int column = 0; column < line.length(); column++) {
                InventoryView.Slot slot = recipe.key().get(String.valueOf(line.charAt(column)));
                if (slot != null) {
                    cells[(top + row) * GRID_SIDE + left + column] = slot.alternatives().getFirst();
                }
            }
        }
        return Arrays.asList(cells);
    }

    /**
     * For each distinct ingredient, how many of it one item is worth: the item's total goo over the
     * ingredient's.
     *
     * @param id     the item's id
     * @param value  the item's value
     * @param recipe the recipe making the item
     * @param values every item's value
     * @return each ingredient's ratio, as "1 item = k ingredient"
     */
    private static List<String> ratios(String id, GooValue value, InventoryView.Recipe recipe,
                                       Map<Identifier, GooValue> values) {
        List<String> ratios = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (InventoryView.Slot slot : recipe.slots()) {
            String ingredient = slot.alternatives().getFirst();
            GooValue ingredientValue = values.get(Identifier.parse(ingredient));
            if (!seen.add(ingredient) || ingredientValue == null || !ingredientValue.totalExact().isPositive()) {
                continue;
            }
            GooFraction item = value.totalExact();
            GooFraction each = ingredientValue.totalExact();
            GooFraction times = GooFraction.of(Math.multiplyExact(item.numerator(), each.denominator()),
                    Math.multiplyExact(item.denominator(), each.numerator()));
            ratios.add(ONE + bare(id) + EQUALS + times + SPACE + bare(ingredient));
        }
        return ratios;
    }

    private static String amounts(@Nullable GooValue value, Function<GooFraction, String> shown) {
        if (value == null || value.isEmpty()) {
            return NONE;
        }
        StringJoiner joined = new StringJoiner(AMOUNT_SEPARATOR);
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            GooFraction amount = value.exact(type);
            if (!amount.isZero()) {
                joined.add(GooTypes.id(type) + SPACE + shown.apply(amount));
            }
        }
        return joined.toString();
    }

    private static String bare(String id) {
        return id.startsWith(DEFAULT_NAMESPACE) ? id.substring(DEFAULT_NAMESPACE.length()) : id;
    }

    private static String namespaced(String id) {
        return id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + id : id;
    }
}
