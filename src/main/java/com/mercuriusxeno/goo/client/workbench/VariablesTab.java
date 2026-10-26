package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private static final String USED_BY = "usedBy";
    private static final String DIRECT = "direct";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String READERS = "readers";
    private static final String ID = "id";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String EXPRESSION = "expression";
    private static final String CONSTANT_KIND = "constant";
    private static final String OBJECT_START = "{";
    private static final String TYPE_SEPARATOR = " ";
    private static final String TYPES_SEPARATOR = ", ";
    private static final String VALUE_PREFIX = "value ";
    private static final String EMPTY_TREE = "empty";
    private static final String NO_TEXT = "";

    private final ManagerDraft draft;
    private final Map<String, JsonObject> results = new LinkedHashMap<>();
    private @Nullable String editing;

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
     * @param indirect   the terminus items it cascades into, less the direct ones
     */
    public record ConstantBlock(String name, String expression, String kind, String resolved, String error,
                                List<Reader> readers, List<String> direct, List<String> indirect) {
    }

    /**
     * A constant's hover card.
     *
     * @param title      the prefab's name
     * @param expression its expression as the file spells it, unquoted
     * @param lines      one line per type it resolves to, its value, or its error
     * @param direct     the items whose own expression names it
     * @param indirect   the terminus items it cascades into, less the direct ones
     */
    public record HoverCard(String title, String expression, List<String> lines, List<String> direct,
                            List<String> indirect) {
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
                    result == null ? NO_TEXT : textOf(result, ERROR), readersOf(name), idsOf(result, DIRECT), indirectOf(result)));
        }
        return blocks;
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
     * A $name link opens its variable's edit box.
     *
     * @param name the variable the link names
     */
    public void jumpTo(String name) {
        editing = name;
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
     * @param name the constant hovered
     * @return the hover request
     */
    public String hoverRequest(String name) {
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

    /**
     * @param hoverJson the constants hover operation's reply
     * @return the card it draws
     */
    public HoverCard readHover(String hoverJson) {
        JsonObject hover = JsonParser.parseString(hoverJson).getAsJsonObject();
        String error = textOf(hover, ERROR);
        List<String> lines;
        if (!error.isEmpty()) {
            lines = List.of(error);
        } else if (TREE.equals(hover.get(KIND).getAsString())) {
            lines = hover.getAsJsonObject(TREE).entrySet().stream()
                    .map(type -> type.getKey() + TYPE_SEPARATOR + type.getValue().getAsString()).toList();
        } else {
            lines = List.of(VALUE_PREFIX + textOf(hover, VALUE));
        }
        return new HoverCard(hover.get(NAME).getAsString(),
                ManagerDraft.shownText(textOf(hover, EXPRESSION)), lines, idsOf(hover, DIRECT), indirectOf(hover));
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
        JsonObject result = results.get(name);
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

    // The terminus items less those naming the constant themselves, a bare id and its minecraft: form one item
    // (decision used-by-draws-the-terminus-items-alone).
    private static List<String> indirectOf(@Nullable JsonObject result) {
        Set<String> direct = idsOf(result, DIRECT).stream().map(VariablesTab::namespaced)
                .collect(Collectors.toSet());
        return idsOf(result, USED_BY).stream().filter(id -> !direct.contains(namespaced(id))).toList();
    }

    private static String namespaced(String id) {
        return id.indexOf(NAMESPACE_SEPARATOR) < 0 ? DEFAULT_NAMESPACE + id : id;
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
