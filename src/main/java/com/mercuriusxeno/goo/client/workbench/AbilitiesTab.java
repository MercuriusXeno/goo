package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mojang.serialization.DataResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String BADGE = "badge";
    private static final String ORDER = "order";
    private static final String TYPE_ORDER = "typeOrder";
    private static final String ICON = "icon";
    private static final String MECHANISM = "mechanism";
    private static final String FIELD = "field";
    private static final String PATH_SEPARATOR = "/";
    // Decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides: a tier's orders read 10, 20, 30.
    private static final int ORDER_STEP = 10;
    private static final String DESCRIPTION = "description";
    private static final String NOT_A_NUMBER = "This box takes a number alone.";
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String NO_TEXT = "";
    private static final String LINE_JOIN = "; ";

    private final List<Ability> abilities = new ArrayList<>();
    private final List<String> typeOrder = new ArrayList<>();
    private final Set<String> closed = new HashSet<>();
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
     * @param path      its path in the ability's JSON
     * @param value     its value as the JSON writes it, a number's text or an expression
     * @param kind      what it takes
     * @param mechanism what it tunes, which its label's lang key reads through
     * @param field     its own name, which its label's lang key reads through
     */
    public record Leaf(String path, String value, Kind kind, String mechanism, String field) {
        // Each price field as its mechanism and field read: the cost and upkeep, a blink's trip prices, Zone's
        // way in and platform prices.
        private static final Set<String> PRICES = Set.of("cost.cost", "upkeep.upkeep",
                "cost_per_block.cost_per_block", "wall_cost.wall_cost", "node_percent.node_percent", "zone.enter",
                "zone.cell_cost", "zone.distance_cost");

        /**
         * @return whether the leaf prices the ability, which the tab shows; it shows no other param
         *     (decision abilities-tab-lists-every-numeric-param-as-expression-boxes)
         */
        public boolean prices() {
            return PRICES.contains(mechanism + '.' + field);
        }
    }

    /**
     * One ability.
     *
     * @param id          its id
     * @param gooType     its goo type's id
     * @param displayName its name's translation key
     * @param draft       whether a draft stands over its live file
     * @param leaves      its numeric leaves in the file's order
     * @param badge       the target kind it declares, which ranks it in the radial fan
     * @param order       its order field, which sorts it inside its badge tier
     * @param icon        its icon texture override, empty for the convention path
     * @param description its description's translation key, which its name hovers
     */
    public record Ability(String id, String gooType, String displayName, boolean draft, List<Leaf> leaves,
                          AbilityBadge badge, int order, String icon, String description) {
        /**
         * @return its price fields in the file's order, the leaves the tab shows
         */
        public List<Leaf> prices() {
            return leaves.stream().filter(Leaf::prices).toList();
        }
    }

    /**
     * One goo type's abilities, as the tab sections them.
     *
     * @param type      the goo type's id
     * @param abilities its abilities in the radial fan's order
     */
    public record Section(String type, List<Ability> abilities) {
    }

    /**
     * @param modelJson the server's abilities model
     */
    public void readModel(String modelJson) {
        abilities.clear();
        typeOrder.clear();
        JsonObject model = JsonParser.parseString(modelJson).getAsJsonObject();
        for (JsonElement element : model.getAsJsonArray(ABILITIES)) {
            JsonObject ability = element.getAsJsonObject();
            List<Leaf> leaves = new ArrayList<>();
            for (JsonElement leaf : ability.getAsJsonArray(LEAVES)) {
                leaves.add(leafOf(leaf.getAsJsonObject()));
            }
            JsonElement order = ability.get(ORDER);
            abilities.add(new Ability(ability.get(ID).getAsString(), stringOr(ability, GOO_TYPE),
                    stringOr(ability, DISPLAY_NAME), ability.get(DRAFT).getAsBoolean(), List.copyOf(leaves),
                    badgeOf(stringOr(ability, BADGE)), order == null ? 0 : order.getAsInt(), stringOr(ability, ICON),
                    stringOr(ability, DESCRIPTION)));
        }
        JsonElement types = model.get(TYPE_ORDER);
        if (types != null && types.isJsonArray()) {
            types.getAsJsonArray().forEach(type -> typeOrder.add(type.getAsString()));
        }
    }

    // A model carrying no mechanism reads the path's first segment, and no field its last.
    private static Leaf leafOf(JsonObject leaf) {
        String path = leaf.get(PATH).getAsString();
        List<String> segments = List.of(path.split(PATH_SEPARATOR));
        return new Leaf(path, leaf.get(VALUE).getAsString(), Kind.valueOf(leaf.get(KIND).getAsString()),
                leaf.has(MECHANISM) ? leaf.get(MECHANISM).getAsString() : segments.getFirst(),
                leaf.has(FIELD) ? leaf.get(FIELD).getAsString() : segments.getLast());
    }

    // The badge an ability's file names, the world badge where the model carries none it knows.
    private static AbilityBadge badgeOf(String named) {
        for (AbilityBadge badge : AbilityBadge.values()) {
            if (badge.getSerializedName().equals(named)) {
                return badge;
            }
        }
        return AbilityBadge.WORLD;
    }

    /**
     * One section per goo type holding an ability the search matches, in the goo type picker's order the model
     * carries, each section's abilities as the radial fan sorts them, badge tier then order field
     * (decision abilities-tab-sections-by-type-in-picker-order-fan-order-inside).
     *
     * @param translate each translation key's text
     * @param typed     what the search box holds
     * @return the sections, none empty
     */
    public List<Section> sections(Function<String, String> translate, String typed) {
        Map<String, List<Ability>> byType = new LinkedHashMap<>();
        for (Ability ability : matching(translate, typed)) {
            byType.computeIfAbsent(ability.gooType(), type -> new ArrayList<>()).add(ability);
        }
        Comparator<Ability> fan = AbilityBadge.fanOrder(Ability::badge, this::orderOf);
        return byType.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> typeRank(entry.getKey())))
                .map(entry -> new Section(entry.getKey(), entry.getValue().stream().sorted(fan).toList()))
                .toList();
    }

    /**
     * @param ability an ability
     * @return its order field as a rail drop rewrote it, or as the model read it
     */
    public int orderOf(Ability ability) {
        String edited = edits.getOrDefault(ability.id(), Map.of()).get(ORDER);
        return edited == null ? ability.order() : Integer.parseInt(edited);
    }

    /**
     * Moves an ability from one place to another in its type's section, as a rail dropped on a slot does; see
     * {@link #moveTo}.
     *
     * @param type the section's goo type
     * @param from the ability's index in the section
     * @param to   the index of the ability whose slot it drops on
     */
    public void move(String type, int from, int to) {
        sections(key -> key, NO_TEXT).stream().filter(section -> section.type().equals(type)).findFirst()
                .ifPresent(section -> moveTo(section.abilities().get(from).id(), section.abilities().get(to).id()));
    }

    /**
     * Moves an ability onto another's slot inside its badge tier and rewrites every order in that tier as 10, 20,
     * 30 in the new sequence, each an edit the next Save posts; a drop on another type, another tier or its own slot
     * changes nothing (decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides).
     *
     * @param movedId  the dragged ability's id
     * @param targetId the id of the ability whose slot it drops on
     */
    public void moveTo(String movedId, String targetId) {
        Optional<Ability> moved = byId(movedId);
        Optional<Ability> target = byId(targetId);
        if (moved.isEmpty() || target.isEmpty() || !sameTier(moved.get(), target.get())) {
            return;
        }
        List<Ability> tier = new ArrayList<>(abilities.stream().filter(each -> sameTier(each, moved.get()))
                .sorted(Comparator.comparingInt(this::orderOf)).toList());
        int slot = tier.indexOf(target.get());
        tier.remove(moved.get());
        tier.add(slot, moved.get());
        for (int index = 0; index < tier.size(); index++) {
            editOrder(tier.get(index), (index + 1) * ORDER_STEP);
        }
    }

    // Two abilities share a badge tier where they share a goo type and a badge.
    private static boolean sameTier(Ability one, Ability other) {
        return one.gooType().equals(other.gooType()) && one.badge() == other.badge();
    }

    private Optional<Ability> byId(String id) {
        return abilities.stream().filter(ability -> ability.id().equals(id)).findFirst();
    }

    // An order back at the one the model read leaves no edit.
    private void editOrder(Ability ability, int order) {
        Map<String, String> changes = edits.computeIfAbsent(ability.id(), id -> new LinkedHashMap<>());
        if (order == ability.order()) {
            changes.remove(ORDER);
        } else {
            changes.put(ORDER, Integer.toString(order));
        }
        if (changes.isEmpty()) {
            edits.remove(ability.id());
        }
    }

    // A type the model's order names ranks by its place there, any other after every named one.
    private int typeRank(String type) {
        int rank = typeOrder.indexOf(type);
        return rank < 0 ? typeOrder.size() : rank;
    }

    /**
     * Every section starts open, its abilities drawn with no click taken
     * (decision type-sections-fold-by-the-audit-chevron-and-start-expanded).
     *
     * @param type a section's goo type
     * @return whether its abilities draw
     */
    public boolean open(String type) {
        return !closed.contains(type);
    }

    /**
     * @param type a section's goo type, which closes if open and opens if closed
     */
    public void toggle(String type) {
        if (!closed.remove(type)) {
            closed.add(type);
        }
    }

    /**
     * @param types the sections the expand/collapse-all button opens and closes
     * @return whether any of them stands open, so the button reads Collapse all
     */
    public boolean anyOpen(Collection<String> types) {
        return types.stream().anyMatch(this::open);
    }

    /**
     * Closes every one of the sections while any stands open, else opens every one.
     *
     * @param types the sections the expand/collapse-all button opens and closes
     */
    public void toggleAll(Collection<String> types) {
        if (anyOpen(types)) {
            closed.addAll(types);
        } else {
            closed.removeAll(types);
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
