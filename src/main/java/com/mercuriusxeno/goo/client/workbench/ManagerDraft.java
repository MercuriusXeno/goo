package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The draft's model the manager's tabs edit together, as the model operation answers it: the Values, Groups and
 * Variables tabs each read and write its members, and a save or a resolve posts the members changed since the last
 * save (decisions goo-workbench-opens-the-in-game-manager-screen, requests-post-only-the-changed-members).
 */
public final class ManagerDraft {
    private static final String MEMBERS = "members";
    private static final String KEY = "key";
    private static final String ENTRIES = "entries";
    private static final String PATH = "path";
    private static final String DRAFT = "draft";
    private static final String DIFFERS = "differs";
    private static final String NO_PATH = "the value file";
    private static final String CHANGED = "changed";
    private static final String REMOVED = "removed";
    private static final String AFTER = "after";
    private static final String OBJECT_START = "{";
    // An integer stays as written; anything else the box holds is an expression string, stored quoted.
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private JsonObject model = new JsonObject();
    // The members by key as the server's draft holds them, as the last model reply or save answered them.
    private Map<String, JsonObject> saved = new HashMap<>();

    /**
     * Reads a model the server's draft holds, which the change set diffs against from now.
     *
     * @param modelJson the model operation's reply, or a save's, a promote's or a revert's
     */
    public void read(String modelJson) {
        readEdited(modelJson);
        saved = new HashMap<>();
        for (JsonElement element : members()) {
            JsonObject member = element.getAsJsonObject();
            keyOf(member).ifPresent(key -> saved.put(key, member.deepCopy()));
        }
    }

    /**
     * Reads a model the server edited and did not save, whose changes go on posting until a save.
     *
     * @param modelJson a rename's or a delete's reply
     */
    public void readEdited(String modelJson) {
        model = JsonParser.parseString(modelJson).getAsJsonObject();
    }

    /**
     * Takes the model a groups apply answers, keeping the path the draft stood at.
     *
     * @param applied the apply reply's model
     */
    public void adopt(JsonObject applied) {
        model.add(MEMBERS, applied.getAsJsonArray(MEMBERS));
    }

    /**
     * @return the model with every edit
     */
    public String text() {
        return model.toString();
    }

    /**
     * The members whose JSON differs from the server's draft, each whole, a member the draft gained carrying the
     * key it follows, and the keys of the members removed; the server merges it into the draft it holds.
     * decision requests-post-only-the-changed-members
     *
     * @return the change set, a copy the caller may add its fields to
     */
    public JsonObject changeSet() {
        JsonArray changed = new JsonArray();
        Set<String> held = new HashSet<>();
        String follows = null;
        for (JsonElement element : members()) {
            JsonObject member = element.getAsJsonObject();
            Optional<String> key = keyOf(member);
            if (key.isEmpty()) {
                continue;
            }
            held.add(key.get());
            changedFrom(member, saved.get(key.get()), follows).ifPresent(changed::add);
            follows = key.get();
        }
        JsonObject changes = new JsonObject();
        changes.add(CHANGED, changed);
        changes.add(REMOVED, removedFrom(held));
        return changes;
    }

    // A member the server's draft holds nowhere posts with the key it follows; one it holds posts where it differs.
    private static Optional<JsonObject> changedFrom(JsonObject member, @Nullable JsonObject was,
                                                    @Nullable String follows) {
        if (was == null) {
            JsonObject added = member.deepCopy();
            added.add(AFTER, follows == null ? JsonNull.INSTANCE : new JsonPrimitive(follows));
            return Optional.of(added);
        }
        return was.equals(member) ? Optional.empty() : Optional.of(member.deepCopy());
    }

    private JsonArray removedFrom(Set<String> held) {
        JsonArray removed = new JsonArray();
        saved.keySet().stream().filter(key -> !held.contains(key)).sorted().forEach(removed::add);
        return removed;
    }

    /**
     * @return the text a model-taking request posts: the change set
     */
    public String changeSetText() {
        return changeSet().toString();
    }

    private static Optional<String> keyOf(JsonObject member) {
        JsonElement key = member.get(KEY);
        return key == null || key.isJsonNull() ? Optional.empty() : Optional.of(key.getAsString());
    }

    /**
     * @return the draft's members, live: a change to them is a change to the draft
     */
    public JsonArray members() {
        JsonElement members = model.get(MEMBERS);
        if (members == null || !members.isJsonArray()) {
            JsonArray none = new JsonArray();
            model.add(MEMBERS, none);
            return none;
        }
        return members.getAsJsonArray();
    }

    /**
     * @return the value file the last model reply named, the one Promote overwrites
     */
    public String path() {
        JsonElement path = model.get(PATH);
        return path == null || path.isJsonNull() ? NO_PATH : path.getAsString();
    }

    /**
     * @return whether the last model reply said the draft holds changes the value file does not
     */
    public boolean differs() {
        JsonElement draft = model.get(DRAFT);
        return draft != null && draft.isJsonObject() && draft.getAsJsonObject().has(DIFFERS)
                && draft.getAsJsonObject().get(DIFFERS).getAsBoolean();
    }

    /**
     * @param section the key of a section member, such as _conversions
     * @return that section's entries, live, empty where the draft holds no such section
     */
    public JsonArray sectionEntries(String section) {
        for (JsonElement element : members()) {
            JsonObject member = element.getAsJsonObject();
            JsonElement entries = member.get(ENTRIES);
            if (isKeyed(member, section) && entries != null && entries.isJsonArray()) {
                return entries.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private static boolean isKeyed(JsonObject member, String key) {
        JsonElement held = member.get(KEY);
        return held != null && !held.isJsonNull() && key.equals(held.getAsString());
    }

    /**
     * @param members the members the draft holds from now
     */
    public void replaceMembers(JsonArray members) {
        model.add(MEMBERS, members);
    }

    /**
     * @return a request body carrying the change set, for the caller to add its fields to
     */
    public JsonObject request() {
        return changeSet();
    }

    /**
     * The box shows an expression string without its JSON quotes; a number or an object stays as written.
     *
     * @param text the member's text as the file holds it
     * @return the text the edit box shows
     */
    public static String shownText(String text) {
        try {
            JsonElement value = JsonParser.parseString(text);
            return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : text;
        } catch (JsonParseException e) {
            return text;
        }
    }

    /**
     * @param typed the edit box's text
     * @return the text the file holds: an integer or an object as written, else a quoted expression string
     */
    public static String storedText(String typed) {
        String trimmed = typed.trim();
        if (INTEGER.matcher(trimmed).matches() || trimmed.startsWith(OBJECT_START)) {
            return trimmed;
        }
        JsonArray quoted = new JsonArray();
        quoted.add(typed);
        String array = quoted.toString();
        return array.substring(1, array.length() - 1);
    }
}
