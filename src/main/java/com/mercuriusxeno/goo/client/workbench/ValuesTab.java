package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final String ITEMS = "items";
    private static final String ITEM_TAGS = "item_tags";
    private static final String ID = "id";
    private static final String NAME = "name";
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
    // A new entry stands above the sections the file closes with, as the page and the server write one.
    private static final Set<String> CLOSING_SECTIONS = Set.of("_restricted", "_conversions", "_post_conversions");
    private static final String[] LAYOUT_FIELDS = {"keySeparator", "leading", "trailing", "closingGap"};

    private final ManagerDraft draft;
    private final List<ManagerEntry> entries = new ArrayList<>();
    private final Map<String, JsonObject> resolved = new HashMap<>();
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
     * @return the entries whose id or name holds the search text, every entry while it is blank
     */
    public List<ManagerEntry> listed() {
        return entries.stream().filter(entry -> search.isEmpty()
                || entry.id().toLowerCase(Locale.ROOT).contains(search)
                || entry.name().toLowerCase(Locale.ROOT).contains(search)).toList();
    }

    /**
     * @param id the entry to select
     */
    public void select(String id) {
        selected = id;
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
