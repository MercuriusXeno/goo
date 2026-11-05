package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooConstantParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Variables tab's state with no Screen: each constant's block from the draft and the constants evaluate
 * operation, which block's expression stands in the edit box, the rename, delete and hover requests, the delete's
 * cascade and the hover card, as index.html's variables section holds them (decisions
 * goo-workbench-opens-the-in-game-manager-screen, references-link-to-their-variable-and-hover-a-card).
 */
public final class VariablesTab {
    /** The kind of a constant resolving to a value per type. */
    public static final String TREE_KIND = "tree";
    private static final String CONSTANTS_SECTION = "_constants";
    private static final String CONSTANTS = "constants";
    private static final String ENTRIES = "entries";
    private static final String KEY = "key";
    private static final String TEXT = "text";
    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String TREE = TREE_KIND;
    private static final String SCALAR = "scalar";
    private static final String VALUE = "value";
    private static final String ERROR = "error";
    private static final String DIRECT = "direct";
    private static final String READERS = "readers";
    private static final String ID = "id";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String EXPRESSION = "expression";
    private static final String CONSTANT_KIND = "constant";
    private static final String GROUP_KIND = "group";
    private static final String OBJECT_START = "{";
    private static final String TYPE_SEPARATOR = " ";
    private static final String TYPES_SEPARATOR = ", ";
    private static final String EMPTY_TREE = "empty";
    private static final String NO_TEXT = "";
    private static final String PREFAB_MARK = "$";
    private static final String KEY_SEPARATOR = "keySeparator";
    private static final String VALUE_KIND = "VALUE";
    private static final String OBJECT_KIND = "OBJECT";
    private static final String[] LAYOUT_FIELDS = {"leading", "trailing", "closingGap"};

    private final ManagerDraft draft;
    private final Map<String, JsonObject> results = new LinkedHashMap<>();
    private @Nullable String editing;
    private @Nullable String selected;
    private Search.Query search = Search.EVERYTHING;

    /**
     * @param draft the draft every tab of the manager edits
     */
    public VariablesTab(ManagerDraft draft) {
        this.draft = draft;
    }

    /**
     * Something that reads a constant, which a delete of it removes.
     *
     * @param id   the reader's id: a constant's name, an item's id or a group's name
     * @param kind constant, item or group
     */
    public record Reader(String id, String kind) {
        /**
         * @return whether the reader is a prefab or a group, which Used by draws as a link
         */
        public boolean linked() {
            return CONSTANT_KIND.equals(kind) || GROUP_KIND.equals(kind);
        }
    }

    // Decision used-by-lists-direct-items-and-links-to-prefabs-and-groups.
    private static List<Reader> linksOf(List<Reader> readers) {
        return readers.stream().filter(Reader::linked).toList();
    }

    /**
     * One constant as its block draws.
     *
     * @param name       the constant's name
     * @param expression its expression as the edit box shows it
     * @param kind       scalar or tree
     * @param resolved   what it resolves to, empty before an evaluate answers
     * @param error      the loader's refusal, empty where it resolved
     * @param readers    what reads it directly
     * @param direct     the items whose own expression names it
     */
    public record ConstantBlock(String name, String expression, String kind, String resolved, String error,
                                List<Reader> readers, List<String> direct) {
        /**
         * @return the prefabs and groups reading it, in reader order
         */
        public List<Reader> links() {
            return linksOf(readers);
        }
    }


    /**
     * A prefab's evaluated value, as its hover reads it.
     *
     * @param name   the prefab's name
     * @param tree   each goo type a tree prefab resolves to with its amount, empty for a scalar
     * @param scalar a scalar prefab's value, empty for a tree
     * @param error  the evaluation's refusal, empty where it resolved
     */
    public record PrefabValue(String name, Map<String, String> tree, String scalar, String error) {
    }

    /**
     * @param name a prefab's name
     * @return its value from the last evaluate reply, empty where the reply names no such prefab
     */
    public Optional<PrefabValue> prefabValue(String name) {
        JsonObject result = results.get(name);
        if (result == null) {
            return Optional.empty();
        }
        Map<String, String> tree = new LinkedHashMap<>();
        if (TREE.equals(result.get(KIND).getAsString()) && result.has(TREE) && result.get(TREE).isJsonObject()) {
            result.getAsJsonObject(TREE).entrySet().forEach(type -> tree.put(type.getKey(),
                    type.getValue().getAsString()));
        }
        // Decision value-lists-order-highest-first-ties-by-type-key.
        return Optional.of(new PrefabValue(name, AmountOrder.highestFirst(tree), textOf(result, VALUE),
                textOf(result, ERROR)));
    }

    /**
     * @param name a prefab's name
     * @return its expression as the draft holds it, a tree's as its object text, empty where no prefab is so named
     */
    public Optional<String> expressionOf(String name) {
        return constantEntries().stream().filter(entry -> entry.get(KEY).getAsString().equals(name)).findFirst()
                .map(entry -> entry.get(TEXT).getAsString());
    }

    /**
     * @param evaluateJson the constants evaluate operation's reply over the draft
     */
    public void readEvaluated(String evaluateJson) {
        results.clear();
        for (JsonElement element : JsonParser.parseString(evaluateJson).getAsJsonObject()
                .getAsJsonArray(CONSTANTS)) {
            JsonObject constant = element.getAsJsonObject();
            results.put(constant.get(NAME).getAsString(), constant);
        }
    }

    /**
     * @return each constant's block in file order
     */
    public List<ConstantBlock> blocks() {
        List<ConstantBlock> blocks = new ArrayList<>();
        for (JsonObject entry : constantEntries()) {
            String name = entry.get(KEY).getAsString();
            String text = entry.get(TEXT).getAsString();
            JsonObject result = results.get(name);
            String kind = result != null ? result.get(KIND).getAsString()
                    : text.trim().startsWith(OBJECT_START) ? TREE : SCALAR;
            blocks.add(new ConstantBlock(name, ManagerDraft.shownText(text), kind, resolvedText(result),
                    result == null ? NO_TEXT : textOf(result, ERROR), readersOf(name), idsOf(result, DIRECT)));
        }
        return blocks;
    }

    /**
     * @param text the Prefabs search box's text, matched against each prefab's name with case ignored
     */
    public void search(String text) {
        search = Search.query(text);
    }

    /**
     * The blocks the Prefabs list draws: those whose name holds the search, every one while it is blank; the
     * expression and kind never match (decision prefabs-tab-searches-its-list-by-name).
     *
     * @return the listed blocks in file order
     */
    public List<ConstantBlock> listed() {
        // Decision one-search-rule-serves-every-box-and-dropdown.
        return blocks().stream().filter(block -> Search.matches(search, Search.Kind.PREFAB, block.name())).toList();
    }

    /**
     * @param name the constant whose read view was clicked
     */
    public void startEditing(String name) {
        editing = name;
    }

    /**
     * The edit box lost focus, so its block draws its read view again.
     */
    public void stopEditing() {
        editing = null;
    }

    /**
     * A $name link lands on its variable selected and read-only, an edit standing open closing (decision
     * every-follow-lands-selected-and-read-only).
     *
     * @param name the variable the link names
     */
    public void jumpTo(String name) {
        editing = null;
        selected = name;
    }

    /**
     * @return the prefab a follow last landed on, empty before any
     */
    public Optional<String> selected() {
        return Optional.ofNullable(selected);
    }

    /**
     * @return the constant whose expression stands in the edit box, empty while every block reads
     */
    public Optional<String> editing() {
        return Optional.ofNullable(editing);
    }

    /**
     * @param name  the constant edited
     * @param typed the edit box's text
     */
    public void edit(String name, String typed) {
        constantEntries().stream().filter(entry -> name.equals(entry.get(KEY).getAsString())).findFirst()
                .ifPresent(entry -> entry.addProperty(TEXT, ManagerDraft.storedText(typed)));
    }

    /**
     * Adds a prefab with no value at the end of _constants, the file gaining the section where it holds none.
     * Decision goo-workbench-opens-the-in-game-manager-screen: the page's Variables pane added constants.
     *
     * @param typed the name box's text, its $ optional
     * @return the new prefab's name, empty where the name is blank, the loader's $name scan stops short of it, or a
     *     prefab already holds it
     */
    public Optional<String> createPrefab(String typed) {
        String trimmed = typed.trim();
        String name = trimmed.startsWith(PREFAB_MARK) ? trimmed.substring(PREFAB_MARK.length()) : trimmed;
        boolean legal = !name.isEmpty() && GooConstantParser.scanIdentifier(name, 0) == name.length();
        if (!legal || expressionOf(name).isPresent()) {
            return Optional.empty();
        }
        constantsSection().add(blankEntry(name, VALUE_KIND, ManagerDraft.storedText(NO_TEXT)));
        return Optional.of(name);
    }

    // The _constants entries, live; a draft holding none gains the section first, ahead of every other member.
    private JsonArray constantsSection() {
        JsonArray members = draft.members();
        for (JsonElement element : members) {
            JsonObject member = element.getAsJsonObject();
            JsonElement key = member.get(KEY);
            if (key != null && !key.isJsonNull() && CONSTANTS_SECTION.equals(key.getAsString())) {
                return member.getAsJsonArray(ENTRIES);
            }
        }
        JsonObject section = blankEntry(CONSTANTS_SECTION, OBJECT_KIND, NO_TEXT);
        members.asList().addFirst(section);
        return section.getAsJsonArray(ENTRIES);
    }

    // A member as the model spells one: its key, kind and text, no entries and no layout of its own.
    private static JsonObject blankEntry(String key, String kind, String text) {
        JsonObject entry = new JsonObject();
        entry.addProperty(KEY, key);
        entry.add(KEY_SEPARATOR, JsonNull.INSTANCE);
        entry.addProperty(KIND, kind);
        entry.addProperty(TEXT, text);
        entry.add(ENTRIES, new JsonArray());
        for (String layout : LAYOUT_FIELDS) {
            entry.add(layout, JsonNull.INSTANCE);
        }
        return entry;
    }

    /**
     * @param from the constant's name
     * @param to   its new name
     * @return the rename request
     */
    public String renameRequest(String from, String to) {
        JsonObject request = draft.request();
        request.addProperty(FROM, from);
        request.addProperty(TO, to);
        return request.toString();
    }

    /**
     * @param name the constant to delete with its cascade
     * @return the delete request
     */
    public String deleteRequest(String name) {
        return namedRequest(name);
    }

    /**
     * Every reader deleting the constant removes: its readers, then each removed constant's readers, each once,
     * as BaseValuesModel.deletionCascade walks them (decision
     * deleting-a-variable-inlines-its-expression-into-each-reader).
     *
     * @param name the constant to delete
     * @return the readers the delete removes, in the order the walk meets them
     */
    public List<Reader> deletionCascade(String name) {
        Set<String> visited = new HashSet<>(Set.of(name));
        Set<Reader> seen = new HashSet<>();
        List<Reader> removed = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of(name));
        while (!pending.isEmpty()) {
            for (Reader reader : readersOf(pending.removeFirst())) {
                boolean constant = CONSTANT_KIND.equals(reader.kind());
                if (constant ? !visited.add(reader.id()) : !seen.add(reader)) {
                    continue;
                }
                removed.add(reader);
                if (constant) {
                    pending.addLast(reader.id());
                }
            }
        }
        return removed;
    }

    private String namedRequest(String name) {
        JsonObject request = draft.request();
        request.addProperty(NAME, name);
        return request.toString();
    }

    private List<JsonObject> constantEntries() {
        for (JsonElement element : draft.members()) {
            JsonObject member = element.getAsJsonObject();
            JsonElement key = member.get(KEY);
            if (key != null && !key.isJsonNull() && CONSTANTS_SECTION.equals(key.getAsString())) {
                return member.getAsJsonArray(ENTRIES).asList().stream().map(JsonElement::getAsJsonObject).toList();
            }
        }
        return List.of();
    }

    private List<Reader> readersOf(String name) {
        return readersIn(results.get(name));
    }

    private static List<Reader> readersIn(@Nullable JsonObject result) {
        if (result == null || !result.has(READERS)) {
            return List.of();
        }
        return result.getAsJsonArray(READERS).asList().stream().map(JsonElement::getAsJsonObject)
                .map(reader -> new Reader(reader.get(ID).getAsString(), reader.get(KIND).getAsString())).toList();
    }

    private static List<String> idsOf(@Nullable JsonObject result, String field) {
        if (result == null || !result.has(field)) {
            return List.of();
        }
        JsonArray ids = result.getAsJsonArray(field);
        return ids.asList().stream().map(JsonElement::getAsString).toList();
    }

    private static String resolvedText(@Nullable JsonObject result) {
        if (result == null) {
            return NO_TEXT;
        }
        if (TREE.equals(result.get(KIND).getAsString())) {
            String types = result.getAsJsonObject(TREE).entrySet().stream()
                    .map(type -> type.getKey() + TYPE_SEPARATOR + type.getValue().getAsString())
                    .collect(Collectors.joining(TYPES_SEPARATOR));
            return types.isEmpty() ? EMPTY_TREE : types;
        }
        return textOf(result, VALUE);
    }

    private static String textOf(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? NO_TEXT : value.getAsString();
    }
}
