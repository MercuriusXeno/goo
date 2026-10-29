package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Values tab's state with no Screen: the inventory's items and tags filtered by the search text, each entry as
 * the server resolved it, the selected entry's row, and the edit a Save sends, as index.html's values section holds
 * them, over the draft every tab shares (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ValuesTab {
    /** The mark a tag's id starts with. */
    public static final String TAG_MARK = "#";
    private static final String ITEMS = "items";
    private static final String ITEM_TAGS = "item_tags";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String INPUTS = "inputs";
    private static final String COUNT = "count";
    private static final String OUTPUT = "output";
    private static final Recipe NO_RECIPE = new Recipe(List.of(), 1);
    private static final String MEMBERS = "members";
    private static final String ENTRIES = "entries";
    private static final String KEY = "key";
    private static final String KIND = "kind";
    private static final String TEXT = "text";
    private static final String VALUE = "value";
    private static final String ERROR = "error";
    private static final String DENIED = "denied";
    private static final String VALUE_KIND = "VALUE";
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String MEMBERS_SUFFIX = " members";
    private static final String NO_TEXT = "";
    private static final String GROUPS_SECTION = "_groups";
    private static final String RESTRICTED_SECTION = "_restricted";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final char FRACTION_DIVIDER = '/';
    // Decision items-rows-read-valued-derived-or-no-value-with-values-inline.
    private static final String ENTRY_SOURCE = "Valued";
    private static final String DENIED_BY = "denied by #";
    private static final String DERIVED_SOURCE = "derived";
    private static final String CONVERSIONS_SECTION = "_conversions";
    private static final String POST_CONVERSIONS_SECTION = "_post_conversions";
    private static final String NO_VALUE = "no value";
    // A new entry stands above the sections the file closes with, as the page and the server write one.
    private static final Set<String> CLOSING_SECTIONS = Set.of("_restricted", "_conversions", "_post_conversions");
    private static final String[] LAYOUT_FIELDS = {"keySeparator", "leading", "trailing", "closingGap"};

    private final ManagerDraft draft;
    private final List<ManagerEntry> entries = new ArrayList<>();
    private final Map<String, JsonObject> resolved = new HashMap<>();
    private final Map<String, Recipe> derivedFrom = new HashMap<>();
    private final Set<String> uncovered = new HashSet<>();
    private Filter filter = Filter.ITEMS;
    private String search = NO_TEXT;
    private @Nullable String selected;

    /**
     * A Values tab over a draft of its own, as a test drives it.
     */
    public ValuesTab() {
        this(new ManagerDraft());
    }

    /**
     * @param draft the draft every tab of the manager edits
     */
    public ValuesTab(ManagerDraft draft) {
        this.draft = draft;
    }

    /**
     * One item or tag the sidebar lists.
     *
     * @param id   the item's id, or the tag's id after #
     * @param name the item's name, or the tag's member count
     * @param tag  whether the entry is an item tag
     */
    public record ManagerEntry(String id, String name, boolean tag) {
    }

    /**
     * The selected entry as the right pane draws it.
     *
     * @param id         the entry's id
     * @param name       the entry's name
     * @param expression the entry's expression as the edit box shows it, empty where the file holds none
     * @param amounts    each type the entry resolves to, with its amount
     * @param denied     whether the entry denies the item
     * @param error      the loader's warnings for the entry, empty where it resolved clean
     */
    public record EntryRow(String id, String name, String expression, Map<String, String> amounts, boolean denied,
                           String error) {
    }

    /**
     * Lists the inventory's items, then its item tags.
     *
     * @param inventoryJson the inventory operation's reply
     */
    public void readInventory(String inventoryJson) {
        JsonObject inventory = JsonParser.parseString(inventoryJson).getAsJsonObject();
        entries.clear();
        for (JsonElement item : array(inventory, ITEMS)) {
            JsonObject object = item.getAsJsonObject();
            entries.add(new ManagerEntry(object.get(ID).getAsString(), object.get(NAME).getAsString(), false));
        }
        for (JsonElement tag : array(inventory, ITEM_TAGS)) {
            JsonObject object = tag.getAsJsonObject();
            entries.add(new ManagerEntry(TAG_PREFIX + object.get(ID).getAsString(),
                    array(object, MEMBERS).size() + MEMBERS_SUFFIX, true));
        }
    }

    /**
     * @param modelJson the model operation's reply, or a save's
     */
    public void readModel(String modelJson) {
        draft.read(modelJson);
    }

    /**
     * @param resolveJson the resolve operation's reply over the model
     */
    public void readResolved(String resolveJson) {
        resolved.clear();
        for (JsonElement entry : array(JsonParser.parseString(resolveJson).getAsJsonObject(), ENTRIES)) {
            JsonObject object = entry.getAsJsonObject();
            resolved.put(object.get(KEY).getAsString(), object);
        }
    }

    /**
     * @param derivedFromJson the derived-from operation's reply: each derived item's recipe inputs
     */
    public void readDerivedFrom(String derivedFromJson) {
        derivedFrom.clear();
        JsonParser.parseString(derivedFromJson).getAsJsonObject().getAsJsonObject(ITEMS).entrySet()
                .forEach(item -> derivedFrom.put(item.getKey(), recipeOf(item.getValue().getAsJsonObject())));
    }

    private static Recipe recipeOf(JsonObject recipe) {
        return new Recipe(array(recipe, INPUTS).asList().stream().map(JsonElement::getAsJsonObject)
                .map(input -> new Input(input.get(ID).getAsString(), input.get(COUNT).getAsInt())).toList(),
                recipe.get(OUTPUT).getAsInt());
    }

    /**
     * One input of a derived item's recipe.
     *
     * @param id    the input item's id
     * @param count how many the recipe takes, summed across its slots
     */
    public record Input(String id, int count) {
    }

    /**
     * The recipe a derived item's value comes from, as its equivalency reads it (decision
     * recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     *
     * @param inputs its inputs, grouped by item
     * @param output how many the recipe makes, each input's count reading over it as a fraction
     */
    public record Recipe(List<Input> inputs, int output) {
    }

    /**
     * @param id an item's namespaced id
     * @return the recipe its derived value comes from, no inputs making one where no recipe values it
     */
    public Recipe derivedFrom(String id) {
        return derivedFrom.getOrDefault(id, NO_RECIPE);
    }

    /**
     * @param text the search box's text
     */
    public void search(String text) {
        search = text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * @return every item and tag the inventory holds, unfiltered, as the group picker offers them
     */
    public List<ManagerEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * @return the entries the filter keeps whose id or name holds the search text, every one while it is blank
     */
    public List<ManagerEntry> listed() {
        return entries.stream().filter(this::keptByFilter).filter(entry -> search.isEmpty()
                || entry.id().toLowerCase(Locale.ROOT).contains(search)
                || entry.name().toLowerCase(Locale.ROOT).contains(search)).toList();
    }

    private boolean keptByFilter(ManagerEntry entry) {
        return switch (filter) {
            case ITEMS -> !entry.tag();
            case TAGS -> entry.tag();
            case UNCOVERED -> !entry.tag() && uncovered.contains(entry.id());
            case FRACTIONAL -> !entry.tag() && worthOf(entry.id()).values().stream().anyMatch(ValuesTab::fractional);
        };
    }

    // The server answers an amount as a whole number or a reduced n/d (decision
    // items-filter-set-takes-a-fractional-chip).
    private static boolean fractional(String amount) {
        int divider = amount.indexOf(FRACTION_DIVIDER);
        if (divider < 0) {
            return false;
        }
        try {
            long numerator = Long.parseLong(amount.substring(0, divider).trim());
            long denominator = Long.parseLong(amount.substring(divider + 1).trim());
            return denominator != 0 && numerator % denominator != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Which entries the sidebar lists, as the page's chips choose them.
     */
    public enum Filter {
        /** Every item. */
        ITEMS,
        /** Every item tag. */
        TAGS,
        /** The items the draft leaves with no value. */
        UNCOVERED,
        /** The items where any one goo type's amount has a part under 1. */
        FRACTIONAL
    }

    /**
     * Where an item's value comes from, as the page's source badge reads it.
     */
    public enum SourceKind {
        /** Its own entry. */
        ENTRY,
        /** A #group entry over a _groups group. */
        GROUP,
        /** A denial. */
        DENIED,
        /** Recipes value it, and no entry does. */
        DERIVED,
        /** Nothing values it. */
        NONE
    }

    /**
     * An item's source badge.
     *
     * @param label      what the badge reads: a group's bare name for a group source
     * @param kind       where the value comes from
     * @param restricted whether the file's _restricted list names the item
     */
    public record Source(String label, SourceKind kind, boolean restricted) {
    }

    /**
     * @param chosen which entries the sidebar lists from now
     */
    public void filter(Filter chosen) {
        filter = chosen;
    }

    /**
     * @return which entries the sidebar lists
     */
    public Filter filter() {
        return filter;
    }

    /**
     * @param uncoveredJson the uncovered values operation's reply: the items the draft leaves with no value
     */
    public void readUncovered(String uncoveredJson) {
        uncovered.clear();
        array(JsonParser.parseString(uncoveredJson).getAsJsonObject(), ITEMS)
                .forEach(item -> uncovered.add(item.getAsString()));
    }

    /**
     * @return how many items the draft leaves with no value
     */
    public int uncoveredCount() {
        return uncovered.size();
    }

    /**
     * Reads where an item's value comes from in the draft: its own entry, a #group entry over a _groups group,
     * a denial, or nothing in the file, with its restriction beside, as the page's sourcesFromModel does.
     *
     * @param id the item's namespaced id
     * @return its source badge
     */
    public Source sourceOf(String id) {
        Map<String, List<String>> groups = groupsOf();
        Source source = null;
        for (JsonObject entry : valueEntries()) {
            source = sourceFrom(entry, id, groups, source);
        }
        Source found = source != null ? source : unentered(id);
        return new Source(found.label(), found.kind(), restricted(groups).contains(id));
    }

    // The file's top-level value entries, then its conversions' and post-conversions', which value an item or a
    // group as an entry does; the 26.1 file values #concrete in _post_conversions alone
    // (decision diagnose-and-correct-group-derived-reading-derived-from).
    private List<JsonObject> valueEntries() {
        List<JsonObject> found = new ArrayList<>();
        for (JsonArray section : List.of(draft.members(), draft.sectionEntries(CONVERSIONS_SECTION),
                draft.sectionEntries(POST_CONVERSIONS_SECTION))) {
            for (JsonElement element : section) {
                JsonObject entry = element.getAsJsonObject();
                if (keyOf(entry) != null && VALUE_KIND.equals(kindOf(entry))) {
                    found.add(entry);
                }
            }
        }
        return found;
    }

    // An item's own entry overrides what stands; a group's entry stands only where nothing named the item first.
    private static @Nullable Source sourceFrom(JsonObject entry, String id, Map<String, List<String>> groups,
                                               @Nullable Source standing) {
        String key = String.valueOf(keyOf(entry));
        boolean denied = DENIED.equals(ManagerDraft.shownText(entry.get(TEXT).getAsString()));
        if (!key.startsWith(TAG_PREFIX)) {
            return namespaced(key).equals(id) ? itemSource(denied) : standing;
        }
        String group = key.substring(TAG_PREFIX.length());
        return standing == null && groups.getOrDefault(group, List.of()).contains(id) ? groupSource(group, denied)
                : standing;
    }

    private static Source itemSource(boolean denied) {
        return denied ? new Source(DENIED, SourceKind.DENIED, false)
                : new Source(ENTRY_SOURCE, SourceKind.ENTRY, false);
    }

    // A group source reads the group's bare name, which the Items row links to its group (decision
    // items-row-second-line-is-the-equality-expression).
    private static Source groupSource(String group, boolean denied) {
        return denied ? new Source(DENIED_BY + group, SourceKind.DENIED, false)
                : new Source(group, SourceKind.GROUP, false);
    }

    // An item no entry or group names reads derived where recipes value it, else no value.
    private Source unentered(String id) {
        return worthOf(id).isEmpty() ? new Source(NO_VALUE, SourceKind.NONE, false)
                : new Source(DERIVED_SOURCE, SourceKind.DERIVED, false);
    }

    private Map<String, List<String>> groupsOf() {
        Map<String, List<String>> groups = new HashMap<>();
        for (JsonElement element : draft.sectionEntries(GROUPS_SECTION)) {
            JsonObject group = element.getAsJsonObject();
            JsonElement members = parsedText(group);
            groups.put(keyOf(group), members != null && members.isJsonArray()
                    ? members.getAsJsonArray().asList().stream().map(JsonElement::getAsString)
                            .map(ValuesTab::namespaced).toList()
                    : List.of());
        }
        return groups;
    }

    private Set<String> restricted(Map<String, List<String>> groups) {
        Set<String> restricted = new HashSet<>();
        for (JsonElement element : draft.sectionEntries(RESTRICTED_SECTION)) {
            String entry = ManagerDraft.shownText(element.getAsJsonObject().get(TEXT).getAsString());
            if (entry.startsWith(TAG_PREFIX)) {
                restricted.addAll(groups.getOrDefault(entry.substring(TAG_PREFIX.length()), List.of()));
            } else {
                restricted.add(namespaced(entry));
            }
        }
        return restricted;
    }

    private static @Nullable JsonElement parsedText(JsonObject member) {
        try {
            return JsonParser.parseString(member.get(TEXT).getAsString());
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static String kindOf(JsonObject member) {
        return member.has(KIND) ? member.get(KIND).getAsString() : VALUE_KIND;
    }

    private static String namespaced(String id) {
        return id.contains(NAMESPACE_SEPARATOR) ? id : DEFAULT_NAMESPACE + id;
    }

    /**
     * @param id the entry to select
     */
    public void select(String id) {
        selected = id;
    }

    /**
     * Lists every item, the search cleared, and selects one, as a used-by icon's click opens it
     * (decision used-by-icons-link-to-items-and-hover-their-value).
     *
     * @param id the item's id
     * @return its place in the list, for the pane to scroll it into view, 0 where the list holds it nowhere
     */
    public int open(String id) {
        filter = Filter.ITEMS;
        search = NO_TEXT;
        selected = id;
        List<ManagerEntry> listed = listed();
        for (int index = 0; index < listed.size(); index++) {
            if (listed.get(index).id().equals(id)) {
                return index;
            }
        }
        return 0;
    }

    /**
     * @param id an item's namespaced id
     * @return each goo type the item resolves to with its amount, empty where nothing values it
     */
    public Map<String, String> worthOf(String id) {
        return amountsOf(resolved.get(entryKeyOf(id)));
    }

    /**
     * @return the selected entry's row, empty while none is selected
     */
    public Optional<EntryRow> selectedRow() {
        if (selected == null) {
            return Optional.empty();
        }
        String id = selected;
        String name = entries.stream().filter(entry -> entry.id().equals(id)).map(ManagerEntry::name).findFirst()
                .orElse(id);
        String key = entryKeyOf(id);
        JsonObject member = valueMember(key);
        JsonObject answer = resolved.get(key);
        boolean denied = answer != null && answer.has(DENIED) && answer.get(DENIED).getAsBoolean();
        return Optional.of(new EntryRow(id, name,
                member == null ? NO_TEXT : ManagerDraft.shownText(member.get(TEXT).getAsString()),
                amountsOf(answer), denied, errorOf(answer)));
    }

    private static Map<String, String> amountsOf(@Nullable JsonObject answer) {
        Map<String, String> amounts = new LinkedHashMap<>();
        if (answer != null && answer.has(VALUE) && answer.get(VALUE).isJsonObject()) {
            answer.getAsJsonObject(VALUE).entrySet().forEach(type -> amounts.put(type.getKey(),
                    type.getValue().getAsString()));
        }
        return amounts;
    }

    private static String errorOf(@Nullable JsonObject answer) {
        return answer == null || !answer.has(ERROR) || answer.get(ERROR).isJsonNull() ? NO_TEXT
                : answer.get(ERROR).getAsString();
    }

    /**
     * Writes the edit box's text as the selected entry's expression, adding the entry where the file holds none.
     *
     * @param typed the edit box's text
     */
    public void edit(String typed) {
        if (selected == null) {
            return;
        }
        String key = entryKeyOf(selected);
        JsonObject member = valueMember(key);
        if (member != null) {
            member.addProperty(TEXT, ManagerDraft.storedText(typed));
            return;
        }
        JsonArray members = draft.members();
        int at = members.size();
        while (at > 0 && CLOSING_SECTIONS.contains(keyOf(members.get(at - 1).getAsJsonObject()))) {
            at--;
        }
        JsonArray reordered = new JsonArray();
        for (int index = 0; index < members.size(); index++) {
            if (index == at) {
                reordered.add(newEntry(key, ManagerDraft.storedText(typed)));
            }
            reordered.add(members.get(index));
        }
        if (at == members.size()) {
            reordered.add(newEntry(key, ManagerDraft.storedText(typed)));
        }
        draft.replaceMembers(reordered);
    }

    /**
     * @return the text a save or a resolve posts: the model with every edit
     */
    public String modelText() {
        return draft.text();
    }

    private @Nullable JsonObject valueMember(String key) {
        for (JsonElement element : draft.members()) {
            JsonObject member = element.getAsJsonObject();
            if (VALUE_KIND.equals(member.has(KIND) ? member.get(KIND).getAsString() : VALUE_KIND)
                    && key.equals(keyOf(member))) {
                return member;
            }
        }
        return null;
    }

    /**
     * The file may key an item by its full id or, in the minecraft namespace, its bare path; a key the file
     * already holds is kept, else the bare form, as the page writes it.
     *
     * @param id the selected item's id or #tag
     * @return the key the file holds or would hold the entry under
     */
    private String entryKeyOf(String id) {
        for (JsonElement element : draft.members()) {
            if (id.equals(keyOf(element.getAsJsonObject()))) {
                return id;
            }
        }
        return id.startsWith(DEFAULT_NAMESPACE) ? id.substring(DEFAULT_NAMESPACE.length()) : id;
    }

    private static @Nullable String keyOf(JsonObject member) {
        JsonElement key = member.get(KEY);
        return key == null || key.isJsonNull() ? null : key.getAsString();
    }

    private static JsonObject newEntry(String key, String text) {
        JsonObject entry = new JsonObject();
        entry.addProperty(KEY, key);
        entry.addProperty(KIND, VALUE_KIND);
        entry.addProperty(TEXT, text);
        entry.add(ENTRIES, new JsonArray());
        for (String field : LAYOUT_FIELDS) {
            entry.add(field, JsonNull.INSTANCE);
        }
        return entry;
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
