package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Values tab's reads, each through the game's loader and derivation with no server: every entry resolved
 * or refused, the items nothing covers, and the hover card's recipe grid, ratios and value
 * (decision workbench-authors-base-values-whole).
 */
final class Valuations {
    private static final String ENTRIES = "entries";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String DENIED = "denied";
    private static final String RESTRICTED = "restricted";
    private static final String ERROR = "error";
    private static final String DENIED_TEXT = "\"denied\"";
    private static final String GROUPS = "_groups";
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
    private static final String ROWS = "rows";
    private static final String ROW_ID = "id";
    private static final String ROW_NAME = "name";
    private static final String ROW_DEFINED_AS = "definedAs";
    private static final String ROW_AMOUNTS = "amounts";
    private static final String ROW_MELTS = "melts";
    private static final String ROW_COSTS = "costs";
    private static final String ROW_SOURCE = "source";
    private static final String SOURCE_ENTRY = "entry";
    private static final String SOURCE_GROUP = "group #";
    private static final String SOURCE_DERIVED = "derived from recipe";
    private static final String SOURCE_RECIPE = "(recipe)";
    // A #name the file reads anywhere, whose group's members it then covers.
    private static final Pattern GROUP_REFERENCE = Pattern.compile("#([A-Za-z0-9_:./-]+)");

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
     * @param melts  what melting the item yields per type, each amount rounded down
     * @param costs  what fabricating the item costs per type, each amount rounded up
     * @param tags   the item tags holding it
     */
    record Hover(List<String> grid, int count, List<String> ratios, String value, String melts, String costs,
                 List<String> tags) {
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
        return resolved(model, GooBaseValuesPreview.load(model.serialize(), inventory.tagResolver()));
    }

    private static String resolved(BaseValuesModel model, GooBaseValuesPreview.Loaded loaded) {
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
        return root.toString();
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
     * An item is covered by its own entry or denial or restriction, or by belonging to a group the file reads
     * anywhere, as a #group entry or in a conversion.
     *
     * @param model     the model
     * @param inventory the inventory's item ids
     * @return the items nothing covers, in inventory order
     * @throws IOException where the model's text cannot be read
     */
    static List<String> uncovered(BaseValuesModel model, List<String> inventory) throws IOException {
        return uncovered(model, inventory, GooBaseValuesPreview.load(model.serialize()));
    }

    /**
     * @param model     the model
     * @param inventory the inventory, whose tags a #tag entry expands through
     * @return the items nothing covers, in inventory order
     */
    static List<String> uncovered(BaseValuesModel model, InventoryView inventory) {
        return uncovered(model, inventory.itemIds(), GooBaseValuesPreview.load(model.serialize(),
                inventory.tagResolver()));
    }

    private static List<String> uncovered(BaseValuesModel model, List<String> inventory,
                                          GooBaseValuesPreview.Loaded loaded) {
        Set<String> covered = new HashSet<>();
        loaded.baseValues().keySet().forEach(id -> covered.add(id.toString()));
        loaded.denied().forEach(id -> covered.add(id.toString()));
        loaded.restricted().forEach(id -> covered.add(id.toString()));
        Set<String> referenced = referencedGroups(model);
        GroupConvention.describe(model).stream().filter(group -> referenced.contains(group.name()))
                .forEach(group -> covered.addAll(group.members()));
        return inventory.stream().filter(id -> !AIR.equals(id) && !covered.contains(id)).toList();
    }

    private static Set<String> referencedGroups(BaseValuesModel model) {
        Set<String> referenced = new HashSet<>();
        for (BaseValuesModel.Member member : model.members()) {
            if (GROUPS.equals(member.key())) {
                continue;
            }
            collectReferences(member.key(), referenced);
            collectReferences(member.text(), referenced);
            for (BaseValuesModel.Member entry : member.entries()) {
                collectReferences(entry.key(), referenced);
                collectReferences(entry.text(), referenced);
            }
        }
        return referenced;
    }

    private static void collectReferences(@Nullable String text, Set<String> into) {
        if (text == null) {
            return;
        }
        Matcher match = GROUP_REFERENCE.matcher(text);
        while (match.find()) {
            into.add(match.group(1));
        }
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
                amounts(value, amount -> Integer.toString(amount.floor())),
                amounts(value, amount -> Integer.toString(amount.ceiling())), inventory.tagsOf(id));
    }

    /**
     * The Values table for an item's family: the item, the ingredients of the recipe that values it, and the
     * items whose valuing recipe takes it, each with how the file defines it, its amounts, what it melts to,
     * what it costs and where its value comes from.
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
        JsonArray rows = new JsonArray();
        for (Identifier member : members) {
            rows.add(familyRow(model, inventory, derived, loaded, member));
        }
        JsonObject root = new JsonObject();
        root.add(ROWS, rows);
        return root.toString();
    }

    private static JsonObject familyRow(BaseValuesModel model, InventoryView inventory,
                                        GooBaseValuesPreview.Derived derived, GooBaseValuesPreview.Loaded loaded,
                                        Identifier member) {
        String id = member.toString();
        GooValue value = derived.effective().get(member);
        BaseValuesModel.Member own = model.member(bare(id)) != null ? model.member(bare(id)) : model.member(id);
        String group = groupValuing(model, id);
        JsonObject row = new JsonObject();
        row.addProperty(ROW_ID, id);
        row.addProperty(ROW_NAME, inventory.nameOf(id));
        row.addProperty(ROW_DEFINED_AS, own != null ? own.text()
                : group != null ? TAG_PREFIX + group + CONTEXT_END + " " + model.member(TAG_PREFIX + group).text()
                : derived.derived().contains(member) ? SOURCE_RECIPE : "");
        row.add(ROW_AMOUNTS, amountsJson(value));
        row.addProperty(ROW_MELTS, amounts(value, amount -> Integer.toString(amount.floor())));
        row.addProperty(ROW_COSTS, amounts(value, amount -> Integer.toString(amount.ceiling())));
        row.addProperty(ROW_SOURCE, loaded.denied().contains(member) ? DENIED
                : own != null ? SOURCE_ENTRY
                : group != null ? SOURCE_GROUP + group
                : derived.derived().contains(member) ? SOURCE_DERIVED : NONE);
        return row;
    }

    private static @Nullable String groupValuing(BaseValuesModel model, String id) {
        return GroupConvention.describe(model).stream()
                .filter(group -> group.members().contains(id) && model.member(TAG_PREFIX + group.name()) != null)
                .map(GroupConvention.Group::name).findFirst().orElse(null);
    }

    /**
     * Derives once per model text and inventory: a hover and a family read derive over every recipe the
     * loadout holds, so the last derivation answers until the text changes.
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
        int top = (GRID_SIDE - height) / 2;
        int left = (GRID_SIDE - width) / 2;
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
            ratios.add(ONE + bare(id) + EQUALS + times + " " + bare(ingredient));
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
                joined.add(GooTypes.id(type) + " " + shown.apply(amount));
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
