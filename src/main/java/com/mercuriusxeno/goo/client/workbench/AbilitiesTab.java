package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mojang.serialization.DataResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The Abilities tab's state with no Screen: every ability the server's model lists with its numeric leaves, the
 * leaves edited since the last save, and the text a save posts, one change set per edited ability
 * (decisions abilities-tab-lists-every-numeric-param-as-expression-boxes,
 * ability-drafts-promote-per-file-like-base-values).
 */
public final class AbilitiesTab {
    private static final String ABILITIES = "abilities";
    private static final String ID = "id";
    private static final String GOO_TYPE = "gooType";
    private static final String DISPLAY_NAME = "displayName";
    private static final String DRAFT = "draft";
    private static final String LEAVES = "leaves";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private static final String KIND = "kind";
    private static final String NOT_A_NUMBER = "This box takes a number alone.";
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String NO_TEXT = "";
    private static final String LINE_JOIN = "; ";

    private final List<Ability> abilities = new ArrayList<>();
    private final Map<String, Map<String, String>> edits = new LinkedHashMap<>();

    /**
     * What a leaf takes, as the server's model marks it.
     */
    public enum Kind {
        /** A number alone. */
        NUMBER,
        /** A number or an expression over the host's variables. */
        EXPRESSION,
        /** A cost or an upkeep, built as a value of the ability's own goo type alone. */
        COST
    }

    /**
     * One numeric leaf of an ability.
     *
     * @param path  its path in the ability's JSON
     * @param value its value as the JSON writes it, a number's text or an expression
     * @param kind  what it takes
     */
    public record Leaf(String path, String value, Kind kind) {
    }

    /**
     * One ability.
     *
     * @param id          its id
     * @param gooType     its goo type's id
     * @param displayName its name's translation key
     * @param draft       whether a draft stands over its live file
     * @param leaves      its numeric leaves in the file's order
     */
    public record Ability(String id, String gooType, String displayName, boolean draft, List<Leaf> leaves) {
    }

    /**
     * @param modelJson the server's abilities model
     */
    public void readModel(String modelJson) {
        abilities.clear();
        for (JsonElement element : JsonParser.parseString(modelJson).getAsJsonObject().getAsJsonArray(ABILITIES)) {
            JsonObject ability = element.getAsJsonObject();
            List<Leaf> leaves = new ArrayList<>();
            for (JsonElement leaf : ability.getAsJsonArray(LEAVES)) {
                JsonObject each = leaf.getAsJsonObject();
                leaves.add(new Leaf(each.get(PATH).getAsString(), each.get(VALUE).getAsString(),
                        Kind.valueOf(each.get(KIND).getAsString())));
            }
            abilities.add(new Ability(ability.get(ID).getAsString(), stringOr(ability, GOO_TYPE),
                    stringOr(ability, DISPLAY_NAME), ability.get(DRAFT).getAsBoolean(), List.copyOf(leaves)));
        }
    }

    private static String stringOr(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? NO_TEXT : value.getAsString();
    }

    /**
     * @return every ability the model lists, by id
     */
    public List<Ability> abilities() {
        return List.copyOf(abilities);
    }

    /**
     * @param translate each translation key's text
     * @param typed     what the search box holds
     * @return the abilities whose name or id the search matches
     */
    public List<Ability> matching(Function<String, String> translate, String typed) {
        Search.Query query = Search.query(typed);
        return abilities.stream().filter(ability -> Search.matches(query, Search.Kind.ITEM,
                translate.apply(ability.displayName()), ability.id())).toList();
    }

    /**
     * @param ability an ability
     * @param leaf    one of its leaves
     * @return the leaf's value as edited, or as the model read it
     */
    public String valueOf(Ability ability, Leaf leaf) {
        return edits.getOrDefault(ability.id(), Map.of()).getOrDefault(leaf.path(), leaf.value());
    }

    /**
     * Holds the leaf's new value, or drops its edit where the value is the one the model read, so a cancelled edit
     * leaves nothing to save.
     *
     * @param ability an ability
     * @param leaf    one of its leaves
     * @param value   the leaf's new value
     */
    public void edit(Ability ability, Leaf leaf, String value) {
        Map<String, String> changes = edits.computeIfAbsent(ability.id(), id -> new LinkedHashMap<>());
        if (value.equals(leaf.value())) {
            changes.remove(leaf.path());
        } else {
            changes.put(leaf.path(), value);
        }
        if (changes.isEmpty()) {
            edits.remove(ability.id());
        }
    }

    /**
     * @return whether any leaf stands edited since the last save
     */
    public boolean edited() {
        return !edits.isEmpty();
    }

    /**
     * @param ability an ability
     * @param leaf    one of its leaves
     * @param worth   each item's or prefab's resolved amounts by goo type
     * @return why the leaf's value would not load, or the items its cost reads that hold none of the ability's type,
     *     empty where it reads
     */
    public String errorOf(Ability ability, Leaf leaf, Function<ExpressionBuilder.Ref, Map<String, String>> worth) {
        String value = valueOf(ability, leaf);
        if (leaf.kind() == Kind.NUMBER) {
            return NUMBER.matcher(value.trim()).matches() ? NO_TEXT : NOT_A_NUMBER;
        }
        Optional<String> parsed = Expr.parse(value).error().map(DataResult.Error::message);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        return leaf.kind() == Kind.COST ? String.join(LINE_JOIN, AbilityCosts.lacking(value, ability.gooType(), worth))
                : NO_TEXT;
    }

    /**
     * @return what a save posts: each edited ability's changed leaves by path, a number written as a number
     */
    public String postedText() {
        JsonObject edited = new JsonObject();
        edits.forEach((id, changes) -> {
            JsonObject leaves = new JsonObject();
            changes.forEach((path, value) -> leaves.add(path, posted(value)));
            edited.add(id, leaves);
        });
        JsonObject posted = new JsonObject();
        posted.add(ABILITIES, edited);
        return posted.toString();
    }

    private static JsonPrimitive posted(String value) {
        String trimmed = value.trim();
        if (!NUMBER.matcher(trimmed).matches()) {
            return new JsonPrimitive(trimmed);
        }
        BigDecimal number = new BigDecimal(trimmed).stripTrailingZeros();
        return number.scale() <= 0 ? new JsonPrimitive(number.toBigInteger()) : new JsonPrimitive(number);
    }

    /**
     * The save answered: the model reads afresh and no edit stands.
     *
     * @param modelJson the server's abilities model after the save
     */
    public void saved(String modelJson) {
        edits.clear();
        readModel(modelJson);
    }
}
