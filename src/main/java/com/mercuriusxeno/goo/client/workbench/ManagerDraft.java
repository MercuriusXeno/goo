package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.regex.Pattern;

/**
 * The draft's model the manager's tabs edit together, as the model operation answers it: the Values, Groups and
 * Variables tabs each read and write its members, and a save or a resolve posts it whole
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class ManagerDraft {
    private static final String MEMBERS = "members";
    private static final String KEY = "key";
    private static final String ENTRIES = "entries";
    private static final String PATH = "path";
    private static final String DRAFT = "draft";
    private static final String DIFFERS = "differs";
    private static final String NO_PATH = "the value file";
    private static final String OBJECT_START = "{";
    // An integer stays as written; anything else the box holds is an expression string, stored quoted.
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private JsonObject model = new JsonObject();

    /**
     * @param modelJson the model operation's reply, or a save's, a rename's or a delete's
     */
    public void read(String modelJson) {
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
     * @return the text a save or a resolve posts: the model with every edit
     */
    public String text() {
        return model.toString();
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
     * @return a request body carrying a copy of the draft's members, for the caller to add its fields to
     */
    public JsonObject request() {
        JsonObject request = new JsonObject();
        request.add(MEMBERS, members().deepCopy());
        return request;
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
