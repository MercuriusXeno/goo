package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads base_values.json text through the game's own loader with no server, so a tool authoring the file
 * sees the values and groups the game would load (decision workbench-authors-base-values-whole).
 */
public final class GooBaseValuesPreview {

    private GooBaseValuesPreview() {
    }

    /**
     * @param json base_values.json text
     * @return each item's base value as the loader parses it, before any recipe derives
     * @throws IOException where the text cannot be read
     */
    public static Map<Identifier, GooValue> baseValues(String json) throws IOException {
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new LinkedHashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        GooValueLoader.parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), state);
        return state.baseValues;
    }

    /**
     * @param members a _groups entry's array
     * @return the item ids the loader reads from it
     */
    public static Set<Identifier> groupMembers(JsonArray members) {
        return GooGroupParser.parseGroupMembers(members);
    }
}
