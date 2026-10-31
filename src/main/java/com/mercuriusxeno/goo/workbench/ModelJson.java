package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The workbench model as the page reads and saves it: the file's path and its members, each with its text
 * and, for a section, its entries. The layout fields travel too, so a member the page leaves alone saves
 * back as the file spelled it.
 */
final class ModelJson {
    private static final String PATH = "path";
    private static final String DRAFT = "draft";
    private static final String DIFFERS = "differs";
    private static final String MEMBERS = "members";
    private static final String KEY = "key";
    private static final String KEY_SEPARATOR = "keySeparator";
    private static final String KIND = "kind";
    private static final String TEXT = "text";
    private static final String ENTRIES = "entries";
    private static final String LEADING = "leading";
    private static final String TRAILING = "trailing";
    private static final String CLOSING_GAP = "closingGap";
    private static final String CHANGED = "changed";
    private static final String REMOVED = "removed";
    private static final String AFTER = "after";
    private static final int NOT_HELD = -1;
    private static final String NO_TEXT = "";

    private ModelJson() {
    }

    /**
     * @param model the model
     * @param path  the file's path, as the top bar shows it
     * @return the model's JSON text
     */
    static String toJson(BaseValuesModel model, String path) {
        return toJsonObject(model, path).toString();
    }

    /**
     * @param model     the model
     * @param path      the live file's path, as the top bar shows it
     * @param draftPath the draft's path
     * @param differs   whether a draft stands and differs from the live file, which the page's draft badge shows
     * @return the model's JSON text, carrying the draft beside the live path
     */
    static String toJson(BaseValuesModel model, String path, String draftPath, boolean differs) {
        JsonObject root = toJsonObject(model, path);
        JsonObject draft = new JsonObject();
        draft.addProperty(PATH, draftPath);
        draft.addProperty(DIFFERS, differs);
        root.add(DRAFT, draft);
        return root.toString();
    }

    private static JsonObject toJsonObject(BaseValuesModel model, String path) {
        JsonObject root = new JsonObject();
        root.addProperty(PATH, path);
        root.add(MEMBERS, membersJson(model.members()));
        return root;
    }

    /**
     * Reads a post over the draft. A members array is a whole model, as the gradle dev page posts it, and stands in
     * place of the draft's members. A change set, as the in-game manager posts it, names the members changed since
     * its last save, each replacing the draft's member of its key or, where the draft holds none, landing after the
     * key its after field names, and the keys of the members removed; every member it names nowhere stands as the
     * draft holds it, and an empty post reads the draft alone.
     * decision requests-post-only-the-changed-members
     *
     * @param draft  the draft's model
     * @param posted the posted text, empty for a read
     * @return the draft with the post merged in
     */
    static BaseValuesModel merged(BaseValuesModel draft, String posted) {
        if (posted.isBlank()) {
            return draft;
        }
        JsonObject root = JsonParser.parseString(posted).getAsJsonObject();
        JsonElement whole = root.get(MEMBERS);
        if (whole != null && whole.isJsonArray()) {
            return draft.withMembers(membersFrom(whole.getAsJsonArray()));
        }
        List<BaseValuesModel.Member> members = new ArrayList<>(draft.members());
        Set<String> removed = new HashSet<>();
        array(root, REMOVED).forEach(key -> removed.add(key.getAsString()));
        members.removeIf(member -> removed.contains(member.key()));
        for (JsonElement element : array(root, CHANGED)) {
            placeChanged(members, element.getAsJsonObject());
        }
        return draft.withMembers(members);
    }

    // A changed member replaces the member of its key; a new one lands after the key it follows, first where it
    // follows none, last where the draft holds that key nowhere.
    private static void placeChanged(List<BaseValuesModel.Member> members, JsonObject changed) {
        JsonArray one = new JsonArray();
        one.add(changed);
        BaseValuesModel.Member member = membersFrom(one).getFirst();
        int held = indexOfKey(members, member.key());
        if (held >= 0) {
            members.set(held, member);
            return;
        }
        members.add(insertionPoint(members, string(changed, AFTER)), member);
    }

    private static int insertionPoint(List<BaseValuesModel.Member> members, @Nullable String after) {
        if (after == null) {
            return 0;
        }
        int follows = indexOfKey(members, after);
        return follows < 0 ? members.size() : follows + 1;
    }

    private static int indexOfKey(List<BaseValuesModel.Member> members, @Nullable String key) {
        for (int index = 0; index < members.size(); index++) {
            if (key != null && key.equals(members.get(index).key())) {
                return index;
            }
        }
        return NOT_HELD;
    }

    private static JsonArray array(JsonObject root, String field) {
        JsonElement value = root.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonArray membersJson(List<BaseValuesModel.Member> members) {
        JsonArray array = new JsonArray();
        for (BaseValuesModel.Member member : members) {
            JsonObject json = new JsonObject();
            json.add(KEY, nullable(member.key()));
            json.add(KEY_SEPARATOR, nullable(member.keySeparator()));
            json.addProperty(KIND, member.kind().name());
            json.addProperty(TEXT, member.text());
            json.add(ENTRIES, membersJson(member.entries()));
            json.add(LEADING, nullable(member.leading()));
            json.add(TRAILING, nullable(member.trailing()));
            json.add(CLOSING_GAP, nullable(member.closingGap()));
            array.add(json);
        }
        return array;
    }

    private static List<BaseValuesModel.Member> membersFrom(JsonArray array) {
        List<BaseValuesModel.Member> members = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            JsonElement entries = json.get(ENTRIES);
            members.add(new BaseValuesModel.Member(
                    string(json, KEY),
                    string(json, KEY_SEPARATOR),
                    json.has(KIND) ? BaseValuesModel.Kind.valueOf(json.get(KIND).getAsString())
                            : BaseValuesModel.Kind.VALUE,
                    json.has(TEXT) ? json.get(TEXT).getAsString() : NO_TEXT,
                    entries != null && entries.isJsonArray() ? membersFrom(entries.getAsJsonArray()) : List.of(),
                    string(json, LEADING),
                    string(json, TRAILING),
                    string(json, CLOSING_GAP)));
        }
        return members;
    }

    private static JsonElement nullable(@Nullable String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private static @Nullable String string(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
