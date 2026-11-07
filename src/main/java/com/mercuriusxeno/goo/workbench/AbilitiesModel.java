package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.StepType;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The Abilities tab's model with no server: every numeric leaf of each ability's own JSON by its path, a cost or an
 * upkeep marked a cost, a leaf the ability's codec reads as an expression marked so, and every other a number alone;
 * and an edit written back into the file's own JSON, since the decoded record rewrites its area and a re-encode would
 * not round-trip the file (decisions abilities-tab-lists-every-numeric-param-as-expression-boxes,
 * ability-cost-builds-as-a-one-type-by-type-value).
 */
public final class AbilitiesModel {
    /** The separator between a leaf path's segments. */
    public static final String PATH_SEPARATOR = "/";
    private static final String COST = "cost";
    private static final String UPKEEP = "upkeep";
    private static final List<String> COST_FIELDS = List.of(COST, UPKEEP);
    private static final String ABILITIES = "abilities";
    private static final String ID = "id";
    private static final String GOO_TYPE = "gooType";
    private static final String DISPLAY_NAME = "displayName";
    private static final String DRAFT = "draft";
    private static final String BADGE = "badge";
    private static final String ICON = "icon";
    private static final String NO_ICON = "";
    private static final String DESCRIPTION = "description";
    private static final String ORDER = "order";
    private static final String TYPE_ORDER = "typeOrder";
    private static final String DELIVERY = "delivery";
    private static final String KIND_FIELD = "kind";
    private static final String TYPE_FIELD = "type";
    private static final String MECHANISM = "mechanism";
    private static final String FIELD = "field";
    private static final Pattern ARRAY_INDEX = Pattern.compile("\\d+");
    private static final String LEAVES = "leaves";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private static final String KIND = "kind";
    private static final String NO_LEAF_AT = "No leaf stands at ";
    private static final JsonPrimitive NO_COST = new JsonPrimitive(0);
    // A string standing in for a number reads as the number to an expression field and refuses a number field.
    private static final JsonPrimitive PROBE_NUMBER = new JsonPrimitive(1);

    private AbilitiesModel() {
    }

    /**
     * What a leaf takes.
     */
    public enum Kind {
        /** A number alone, a field the codec refuses an expression in. */
        NUMBER,
        /** A number or an expression over the host's variables. */
        EXPRESSION,
        /** A cost or an upkeep, built as a value of the ability's own goo type alone. */
        COST
    }

    /**
     * One numeric leaf.
     *
     * @param path      its path from the file's root, segments joined by /, an array index a segment of digits
     * @param value     its value as the file writes it
     * @param kind      what it takes
     * @param mechanism what it tunes: the step type holding it, the delivery's kind, or its own root field
     * @param field     its own name, its path's last segment that is no array index
     */
    public record Leaf(String path, JsonPrimitive value, Kind kind, String mechanism, String field) {
    }

    /**
     * @param id   the ability's id
     * @param file the ability's own JSON
     * @return every numeric leaf in the file's order, the cost first and reading 0 where the file names none
     */
    public static List<Leaf> leaves(Identifier id, JsonObject file) {
        List<Leaf> leaves = new ArrayList<>();
        if (!file.has(COST)) {
            leaves.add(new Leaf(COST, NO_COST, Kind.COST, COST, COST));
        }
        walk(id, file, file, List.of(), leaves);
        return leaves;
    }

    /**
     * The mechanism a leaf tunes, which its sub-heading names: the nearest step holding it, by its type; the
     * delivery's kind for a delivery leaf; the root field itself for any other.
     * ability-params-read-named-under-their-mechanism-and-hover-a-description
     *
     * @param file the ability's JSON
     * @param path the leaf's path
     * @return the mechanism's plain key
     */
    static String mechanismOf(JsonObject file, List<String> path) {
        String first = path.getFirst();
        if (DELIVERY.equals(first)) {
            JsonElement kind = file.getAsJsonObject(DELIVERY).get(KIND_FIELD);
            return kind != null && kind.isJsonPrimitive() ? kind.getAsString() : DELIVERY;
        }
        String mechanism = first;
        JsonElement at = file;
        for (String segment : path.subList(0, path.size() - 1)) {
            at = at.isJsonArray() ? at.getAsJsonArray().get(Integer.parseInt(segment))
                    : at.getAsJsonObject().get(segment);
            mechanism = stepTypeOf(at).orElse(mechanism);
        }
        return mechanism;
    }

    private static Optional<String> stepTypeOf(JsonElement at) {
        if (!at.isJsonObject()) {
            return Optional.empty();
        }
        JsonElement type = at.getAsJsonObject().get(TYPE_FIELD);
        return type != null && type.isJsonPrimitive() && stepNames().contains(type.getAsString())
                ? Optional.of(type.getAsString()) : Optional.empty();
    }

    private static Set<String> stepNames() {
        return StepTypes.all().stream().map(StepType::name).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param path a leaf's path
     * @return its last segment that is no array index
     */
    static String fieldOf(List<String> path) {
        for (int index = path.size() - 1; index > 0; index--) {
            if (!ARRAY_INDEX.matcher(path.get(index)).matches()) {
                return path.get(index);
            }
        }
        return path.getFirst();
    }

    private static void walk(Identifier id, JsonObject file, JsonElement at, List<String> path, List<Leaf> leaves) {
        if (at.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : at.getAsJsonObject().entrySet()) {
                walk(id, file, field.getValue(), appended(path, field.getKey()), leaves);
            }
        } else if (at.isJsonArray()) {
            JsonArray array = at.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                walk(id, file, array.get(index), appended(path, Integer.toString(index)), leaves);
            }
        } else if (at.isJsonPrimitive()) {
            leafOf(id, file, path, at.getAsJsonPrimitive()).ifPresent(leaves::add);
        }
    }

    private static List<String> appended(List<String> path, String segment) {
        List<String> longer = new ArrayList<>(path);
        longer.add(segment);
        return List.copyOf(longer);
    }

    // A number is a leaf; a string is one where it parses as an expression and the codec takes a number in its place.
    private static Optional<Leaf> leafOf(Identifier id, JsonObject file, List<String> path, JsonPrimitive value) {
        Optional<Kind> kind;
        if (path.size() == 1 && COST_FIELDS.contains(path.getFirst())) {
            kind = Optional.of(Kind.COST);
        } else if (value.isNumber()) {
            kind = Optional.of(numberKind(id, file, path, value));
        } else {
            kind = readsAsExpression(id, file, path, value) ? Optional.of(Kind.EXPRESSION) : Optional.empty();
        }
        return kind.map(taken -> new Leaf(String.join(PATH_SEPARATOR, path), value, taken, mechanismOf(file, path),
                fieldOf(path)));
    }

    // A number takes an expression where the codec reads its own text as a string in its place.
    private static Kind numberKind(Identifier id, JsonObject file, List<String> path, JsonPrimitive value) {
        return refusal(id, probed(file, path, new JsonPrimitive(value.getAsString()))).isEmpty() ? Kind.EXPRESSION
                : Kind.NUMBER;
    }

    private static boolean readsAsExpression(Identifier id, JsonObject file, List<String> path,
                                             JsonPrimitive value) {
        return value.isString() && Expr.parse(value.getAsString()).isSuccess()
                && refusal(id, probed(file, path, PROBE_NUMBER)).isEmpty();
    }

    private static JsonObject probed(JsonObject file, List<String> path, JsonPrimitive replacement) {
        JsonObject copy = file.deepCopy();
        put(copy, path, replacement);
        return copy;
    }

    /**
     * @param file    an ability's own JSON
     * @param changes each edited leaf's new value by its path
     * @return a copy of the file with each change at its path and everything else as written
     */
    public static JsonObject edited(JsonObject file, JsonObject changes) {
        JsonObject copy = file.deepCopy();
        for (Map.Entry<String, JsonElement> change : changes.entrySet()) {
            put(copy, List.of(change.getKey().split(PATH_SEPARATOR)), change.getValue());
        }
        return copy;
    }

    private static void put(JsonObject root, List<String> path, JsonElement value) {
        JsonElement parent = root;
        for (String segment : path.subList(0, path.size() - 1)) {
            parent = child(parent, segment, path);
        }
        String last = path.getLast();
        if (parent.isJsonArray()) {
            parent.getAsJsonArray().set(Integer.parseInt(last), value);
        } else {
            parent.getAsJsonObject().add(last, value);
        }
    }

    private static JsonElement child(JsonElement parent, String segment, List<String> path) {
        JsonElement child = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(segment))
                : parent.getAsJsonObject().get(segment);
        if (child == null) {
            throw new IllegalArgumentException(NO_LEAF_AT + String.join(PATH_SEPARATOR, path));
        }
        return child;
    }

    /**
     * @param id   the ability's id
     * @param file the ability's JSON
     * @return why the game would refuse to load it, empty where it loads
     */
    public static Optional<String> refusal(Identifier id, JsonObject file) {
        return AbilityDefinition.codecFor(id).parse(JsonOps.INSTANCE, file).error().map(DataResult.Error::message);
    }

    /**
     * @param live   every ability file the game's packs hold, by id
     * @param drafts every ability draft standing, by id, which stands over its live file
     * @return each ability, by id, with its goo type, its name key, whether a draft stands and its leaves
     */
    public static JsonObject model(Map<Identifier, JsonObject> live, Map<Identifier, JsonObject> drafts) {
        JsonArray abilities = new JsonArray();
        // The client sections and sorts the abilities, so the model lists them in no order of its own.
        Set<Identifier> ids = new LinkedHashSet<>(live.keySet());
        ids.addAll(drafts.keySet());
        for (Identifier id : ids) {
            JsonObject file = drafts.getOrDefault(id, live.get(id));
            abilities.add(abilityOf(id, file, drafts.containsKey(id)));
        }
        JsonObject model = new JsonObject();
        model.add(ABILITIES, abilities);
        // abilities-tab-sections-by-type-in-picker-order-fan-order-inside: the picker's type order, for the client
        JsonArray typeOrder = new JsonArray();
        GooTypes.order().forEach(type -> typeOrder.add(GooTypes.id(type)));
        model.add(TYPE_ORDER, typeOrder);
        return model;
    }

    private static JsonObject abilityOf(Identifier id, JsonObject file, boolean draft) {
        JsonObject ability = new JsonObject();
        ability.addProperty(ID, id.toString());
        ability.add(GOO_TYPE, file.get(GOO_TYPE));
        ability.add(DISPLAY_NAME, file.get(DISPLAY_NAME));
        // ability-heading-is-rail-icon-name-draft-badge-and-hovers-its-description
        ability.addProperty(ICON, file.has(ICON) ? file.get(ICON).getAsString() : NO_ICON);
        ability.addProperty(DESCRIPTION, file.has(DESCRIPTION) ? file.get(DESCRIPTION).getAsString()
                : AbilityDefinition.defaultDescription(file.has(DISPLAY_NAME) ? file.get(DISPLAY_NAME).getAsString()
                : id.getPath()));
        ability.add(BADGE, file.get(BADGE));
        ability.addProperty(ORDER, file.has(ORDER) ? file.get(ORDER).getAsInt() : 0);
        ability.addProperty(DRAFT, draft);
        JsonArray leaves = new JsonArray();
        for (Leaf leaf : leaves(id, file)) {
            JsonObject each = new JsonObject();
            each.addProperty(PATH, leaf.path());
            each.add(VALUE, leaf.value());
            each.addProperty(KIND, leaf.kind().name());
            each.addProperty(MECHANISM, leaf.mechanism());
            each.addProperty(FIELD, leaf.field());
            leaves.add(each);
        }
        ability.add(LEAVES, leaves);
        return ability;
    }
}
