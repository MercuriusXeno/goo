package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.RecipeInput;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The inventory dump as the Values tab reads it: items, item tags and recipes, each recipe both as the
 * derivation engine's input and as the grid the hover card draws (decision workbench-authors-base-values-whole).
 */
final class InventoryView {
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String IMAGE = "image";
    private static final String ITEMS = "items";
    private static final String ITEM_TAGS = "item_tags";
    private static final String MEMBERS = "members";
    private static final String RECIPES = "recipes";
    private static final String TYPE = "type";
    private static final String OUTPUT = "output";
    private static final String COUNT = "count";
    private static final String SLOTS = "slots";
    private static final String ALTERNATIVES = "alternatives";
    private static final String TAG = "tag";
    private static final String CONTAINERS = "containers";
    private static final String PATTERN = "pattern";
    private static final String KEY = "key";

    private final Map<String, String> names;
    private final Map<String, List<String>> itemTags;
    private final List<Recipe> recipes;

    private InventoryView(Map<String, String> names, Map<String, List<String>> itemTags, List<Recipe> recipes) {
        this.names = names;
        this.itemTags = itemTags;
        this.recipes = recipes;
    }

    /**
     * One ingredient slot.
     *
     * @param alternatives the item ids it accepts
     * @param tag          the tag it names, or null
     */
    record Slot(List<String> alternatives, @Nullable String tag) {
    }

    /**
     * A recipe as the dump lists it.
     *
     * @param id         the recipe's id
     * @param type       the recipe type's id
     * @param output     the output item id
     * @param count      the output count
     * @param slots      the ingredient slots
     * @param containers each ingredient that leaves an item behind, to the item it leaves
     * @param pattern    a shaped recipe's rows, empty otherwise
     * @param key        a shaped recipe's symbols, empty otherwise
     */
    record Recipe(String id, String type, String output, int count, List<Slot> slots, Map<String, String> containers,
                  List<String> pattern, Map<String, Slot> key) {

        /**
         * @return the recipe as the derivation engine reads it
         */
        RecipeInput toInput() {
            List<Set<Identifier>> alternatives = new ArrayList<>();
            List<Optional<Identifier>> tags = new ArrayList<>();
            for (Slot slot : slots) {
                Set<Identifier> ids = new LinkedHashSet<>();
                slot.alternatives().forEach(alternative -> ids.add(Identifier.parse(alternative)));
                alternatives.add(ids);
                tags.add(Optional.ofNullable(slot.tag()).map(Identifier::parse));
            }
            Map<Identifier, Identifier> left = new LinkedHashMap<>();
            containers.forEach((item, leftover) -> left.put(Identifier.parse(item), Identifier.parse(leftover)));
            return new RecipeInput(Identifier.parse(output), count, alternatives, left, tags);
        }
    }

    /**
     * @param json the inventory dump's text
     * @return its view
     */
    static InventoryView parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonElement item : root.getAsJsonArray(ITEMS)) {
            JsonObject entry = item.getAsJsonObject();
            names.put(entry.get(ID).getAsString(), entry.get(NAME).getAsString());
        }
        Map<String, List<String>> tags = new LinkedHashMap<>();
        for (JsonElement tag : root.getAsJsonArray(ITEM_TAGS)) {
            JsonObject entry = tag.getAsJsonObject();
            tags.put(entry.get(ID).getAsString(), strings(entry, MEMBERS));
        }
        List<Recipe> recipes = new ArrayList<>();
        for (JsonElement recipe : root.getAsJsonArray(RECIPES)) {
            JsonObject entry = recipe.getAsJsonObject();
            if (entry.has(OUTPUT)) {
                recipes.add(recipe(entry));
            }
        }
        return new InventoryView(names, tags, recipes);
    }

    private static Recipe recipe(JsonObject entry) {
        List<Slot> slots = new ArrayList<>();
        entry.getAsJsonArray(SLOTS).forEach(slot -> slots.add(slot(slot.getAsJsonObject())));
        Map<String, String> containers = new LinkedHashMap<>();
        if (entry.has(CONTAINERS)) {
            entry.getAsJsonObject(CONTAINERS).entrySet()
                    .forEach(container -> containers.put(container.getKey(), container.getValue().getAsString()));
        }
        Map<String, Slot> key = new LinkedHashMap<>();
        if (entry.has(KEY)) {
            entry.getAsJsonObject(KEY).entrySet()
                    .forEach(symbol -> key.put(symbol.getKey(), slot(symbol.getValue().getAsJsonObject())));
        }
        return new Recipe(entry.get(ID).getAsString(), entry.get(TYPE).getAsString(), entry.get(OUTPUT).getAsString(),
                entry.get(COUNT).getAsInt(), slots, containers,
                entry.has(PATTERN) ? strings(entry, PATTERN) : List.of(), key);
    }

    private static Slot slot(JsonObject slot) {
        return new Slot(strings(slot, ALTERNATIVES), slot.has(TAG) ? slot.get(TAG).getAsString() : null);
    }

    private static List<String> strings(JsonObject entry, String field) {
        return entry.getAsJsonArray(field).asList().stream().map(JsonElement::getAsString).toList();
    }

    /**
     * @return every item id, in inventory order
     */
    List<String> itemIds() {
        return List.copyOf(names.keySet());
    }

    /**
     * @param id an item id
     * @return its display name, or the id where the inventory holds none
     */
    String nameOf(String id) {
        return names.getOrDefault(id, id);
    }

    /**
     * @param id an item id
     * @return the item tags holding it, in inventory order
     */
    List<String> tagsOf(String id) {
        return itemTags.entrySet().stream().filter(tag -> tag.getValue().contains(id)).map(Map.Entry::getKey)
                .toList();
    }

    /**
     * @return reads an item tag's members from the inventory, standing in for the game's registry
     */
    Function<Identifier, Set<Identifier>> tagResolver() {
        return tag -> {
            Set<Identifier> members = new LinkedHashSet<>();
            itemTags.getOrDefault(tag.toString(), List.of()).forEach(member -> members.add(Identifier.parse(member)));
            return members;
        };
    }

    /**
     * @return every recipe naming an output
     */
    List<Recipe> recipes() {
        return recipes;
    }

    /**
     * @return every recipe as the derivation engine reads it
     */
    List<RecipeInput> recipeInputs() {
        return recipes.stream().map(Recipe::toInput).toList();
    }
}
