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
