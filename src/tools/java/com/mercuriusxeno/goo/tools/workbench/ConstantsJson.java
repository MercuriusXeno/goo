package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.data.GooConstantResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The constants pane's exchanges: each constant resolved through the game's parser beside its readers,
 * and a rename request (decision workbench-authors-base-values-whole).
 */
final class ConstantsJson {
    private static final String CONSTANTS_SECTION = "_constants";
    private static final String CONSTANTS = "constants";
    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String SCALAR = "scalar";
    private static final String TREE = "tree";
    private static final String VALUE = "value";
    private static final String ERROR = "error";
    private static final String USED_BY = "usedBy";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String NOT_JSON = "not JSON: ";
    // A constant's name is what $name reads: letters, digits and underscores.
    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private ConstantsJson() {
    }

    /**
     * A rename request.
     *
     * @param from the constant's name
     * @param to   its new name
     */
    record Rename(String from, String to) {
    }

    /**
     * @param json the posted rename, carrying from, to and the model's members
     * @return the rename it asks for
     * @throws IllegalArgumentException where the new name is no constant name
     */
    static Rename renameFrom(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String from = root.get(FROM).getAsString();
        String to = root.get(TO).getAsString();
        if (!CONSTANT_NAME.matcher(to).matches()) {
            throw new IllegalArgumentException("A constant's name holds letters, digits and underscores: " + to);
        }
        return new Rename(from, to);
    }

    /**
     * @param model the model whose constants to resolve
     * @return each constant in file order: its kind, its value or per-type tree, its error and its readers
     */
    static String evaluated(BaseValuesModel model) {
        Map<String, String> unparsed = new LinkedHashMap<>();
        JsonObject block = new JsonObject();
        List<String> order = new ArrayList<>();
        for (BaseValuesModel.Member section : model.members()) {
            if (!CONSTANTS_SECTION.equals(section.key())) {
                continue;
            }
            for (BaseValuesModel.Member entry : section.entries()) {
                order.add(entry.key());
                try {
                    block.add(entry.key(), JsonParser.parseString(entry.text()));
                } catch (JsonParseException e) {
                    unparsed.put(entry.key(), NOT_JSON + entry.text());
                }
            }
        }
        Map<String, GooConstantResolver.ResolvedConstant> resolved = new LinkedHashMap<>();
        GooConstantResolver.resolve(block).forEach(constant -> resolved.put(constant.name(), constant));
        JsonArray constants = new JsonArray();
        for (String name : order) {
            JsonObject json = new JsonObject();
            json.addProperty(NAME, name);
            GooConstantResolver.ResolvedConstant constant = resolved.get(name);
            if (constant == null) {
                json.addProperty(KIND, SCALAR);
                json.add(VALUE, JsonNull.INSTANCE);
                json.addProperty(ERROR, unparsed.get(name));
            } else {
                describe(constant, json);
            }
            JsonArray usedBy = new JsonArray();
            model.usagesOf(name).forEach(usedBy::add);
            json.add(USED_BY, usedBy);
            constants.add(json);
        }
        JsonObject root = new JsonObject();
        root.add(CONSTANTS, constants);
        return root.toString();
    }

    private static void describe(GooConstantResolver.ResolvedConstant constant, JsonObject json) {
        json.addProperty(KIND, constant.tree() ? TREE : SCALAR);
        if (constant.tree()) {
            JsonObject tree = new JsonObject();
            constant.perType().forEach(tree::addProperty);
            json.add(TREE, tree);
            json.add(VALUE, JsonNull.INSTANCE);
        } else {
            json.add(VALUE, constant.scalar() == null ? JsonNull.INSTANCE
                    : new JsonPrimitive(String.valueOf(constant.scalar())));
        }
        json.add(ERROR, constant.error() == null ? JsonNull.INSTANCE : new JsonPrimitive(constant.error()));
    }
}
