package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.data.GooConstantResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private static final String READERS = "readers";
    private static final String EXPRESSION = "expression";
    private static final String ID = "id";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final String NOT_JSON = "not JSON: ";
    private static final String NOT_A_CONSTANT_NAME = "A constant's name holds letters, digits and underscores: ";
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
            throw new IllegalArgumentException(NOT_A_CONSTANT_NAME + to);
        }
        return new Rename(from, to);
    }

    /**
     * @param json a posted delete or hover, carrying name and the model's members
     * @return the name of the constant it acts on
     */
    static String nameFrom(String json) {
        return JsonParser.parseString(json).getAsJsonObject().get(NAME).getAsString();
    }

    /**
     * @param model      the model whose constants to resolve
     * @param tagMembers each item tag's member ids, keyed by the tag's namespaced id
     * @return each constant in file order: its kind, its value or per-type tree, its error, the terminus items
     *         it cascades into as usedBy (decision used-by-draws-the-terminus-items-alone) and its direct
     *         readers, which the delete modal's cascade walks
     */
    static String evaluated(BaseValuesModel model, Map<String, List<String>> tagMembers) {
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
            addUsage(json, model, name, tagMembers);
            constants.add(json);
        }
        JsonObject root = new JsonObject();
        root.add(CONSTANTS, constants);
        return root.toString();
    }

    /**
     * The constant hover card's answer: the constant as evaluated answers it beside its expression as the file
     * spells it (decision references-link-to-their-variable-and-hover-a-card).
     *
     * @param name       the hovered constant's name
     * @param model      the posted model
     * @param tagMembers each item tag's member ids, keyed by the tag's namespaced id
     * @return the hovered constant's name, expression, kind, value or per-type tree, error and usedBy, or empty
     *         where no constant holds the name
     */
    static Optional<String> hovered(String name, BaseValuesModel model, Map<String, List<String>> tagMembers) {
        BaseValuesModel.Member section = model.member(CONSTANTS_SECTION);
        Optional<BaseValuesModel.Member> entry = section == null ? Optional.empty()
                : section.entries().stream().filter(constant -> name.equals(constant.key())).findFirst();
        return entry.map(constant -> {
            JsonObject answer = JsonParser.parseString(evaluated(model, tagMembers)).getAsJsonObject()
                    .getAsJsonArray(CONSTANTS).asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(evaluatedConstant -> name.equals(evaluatedConstant.get(NAME).getAsString()))
                    .findFirst().orElseThrow();
            answer.addProperty(EXPRESSION, constant.text());
            return answer.toString();
        });
    }

    private static void addUsage(JsonObject json, BaseValuesModel model, String name,
                                 Map<String, List<String>> tagMembers) {
        JsonArray terminus = new JsonArray();
        model.terminusOf(name, tagMembers).forEach(terminus::add);
        json.add(USED_BY, terminus);
        json.add(READERS, readersJson(model.readersOf(name)));
    }

    /**
     * @param readers a constant's readers
     * @return each as {id, kind}, the kind lower case
     */
    static JsonArray readersJson(List<BaseValuesModel.Reader> readers) {
        JsonArray array = new JsonArray();
        for (BaseValuesModel.Reader reader : readers) {
            JsonObject json = new JsonObject();
            json.addProperty(ID, reader.id());
            json.addProperty(KIND, reader.kind().name().toLowerCase(Locale.ROOT));
            array.add(json);
        }
        return array;
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
