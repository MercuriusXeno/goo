package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The workbench model as the page reads and saves it: the file's path and its members, each with its text
 * and, for a section, its entries. The layout fields travel too, so a member the page leaves alone saves
 * back as the file spelled it.
 */
final class ModelJson {
    private static final String PATH = "path";
    private static final String DRAFT = "draft";
    private static final String DIFFERS = "differs";
    private static final String CHECKS = "checks";
    private static final String MEMBERS = "members";
    private static final String KEY = "key";
    private static final String KEY_SEPARATOR = "keySeparator";
    private static final String KIND = "kind";
    private static final String TEXT = "text";
    private static final String ENTRIES = "entries";
    private static final String LEADING = "leading";
    private static final String TRAILING = "trailing";
    private static final String CLOSING_GAP = "closingGap";
    private static final String NO_MEMBERS_ARRAY = "The saved model holds no members array";
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
     * @param checks    the type-source checks' answers over the model
     * @return the model's JSON text, carrying the draft beside the live path and the checks beside the members
     */
    static String toJson(BaseValuesModel model, String path, String draftPath, boolean differs, JsonArray checks) {
        JsonObject root = toJsonObject(model, path);
        JsonObject draft = new JsonObject();
        draft.addProperty(PATH, draftPath);
        draft.addProperty(DIFFERS, differs);
        root.add(DRAFT, draft);
        root.add(CHECKS, checks);
        return root.toString();
    }

    private static JsonObject toJsonObject(BaseValuesModel model, String path) {
        JsonObject root = new JsonObject();
        root.addProperty(PATH, path);
        root.add(MEMBERS, membersJson(model.members()));
        return root;
    }

    /**
     * @param json the JSON text a save sends
     * @return the members it holds
     * @throws IllegalArgumentException where the text holds no members array
     */
    static List<BaseValuesModel.Member> membersFrom(String json) {
        JsonElement members = JsonParser.parseString(json).getAsJsonObject().get(MEMBERS);
        if (members == null || !members.isJsonArray()) {
            throw new IllegalArgumentException(NO_MEMBERS_ARRAY);
        }
        return membersFrom(members.getAsJsonArray());
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
