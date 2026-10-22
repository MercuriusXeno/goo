package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The template a loadout starts from: every item as an empty entry and the goo types listed for reference, so a
 * heavily modded loadout starts from a file naming everything; and, for a file that stands, the items it leaves
 * out (decision template-first-then-defaults-by-convention). Tags are supported for valuing, not mandated: the
 * template makes no group from a tag and reports no tag as uncovered.
 */
final class ValuesTemplate {
    private static final String NEWLINE = "\n";
    private static final String INDENT = "    ";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    // An empty value object: the loader reads {} as no value, and throws on an empty expression string.
    private static final String EMPTY_VALUE = "{}";
    private static final String TAG_PREFIX = "#";
    private static final String AIR = "minecraft:air";
    // Sections the loader reads, empty in a template; _goo_types is a list the loader passes over, for reference.
    private static final List<String> EMPTY_SECTIONS = List.of(
            "\"_groups\": {}", "\"_restricted\": []", "\"_conversions\": {}", "\"_post_conversions\": {}");

    private ValuesTemplate() {
    }

    /**
     * What the inventory says the loadout holds.
     *
     * @param items    every item id, in inventory order
     * @param itemTags each item tag's member ids
     * @param gooTypes every goo type's id
     */
    record Loadout(List<String> items, Map<String, List<String>> itemTags, List<String> gooTypes) {
    }

    /**
     * @param loadout the loadout
     * @return the template as a model
     */
    static BaseValuesModel build(Loadout loadout) {
        List<String> lines = new ArrayList<>();
        lines.add("\"_constants\": {}");
        lines.addAll(EMPTY_SECTIONS);
        loadout.items().stream().filter(item -> !AIR.equals(item))
                .forEach(item -> lines.add(quote(fileId(item)) + ": " + EMPTY_VALUE));
        lines.add("\"_goo_types\": [" + String.join(", ", loadout.gooTypes().stream().map(ValuesTemplate::quote)
                .toList()) + "]");
        return BaseValuesModel.parse("{" + NEWLINE + INDENT + String.join("," + NEWLINE + INDENT, lines) + NEWLINE
                + "}" + NEWLINE);
    }

    /**
     * @param model   the standing file
     * @param loadout the loadout
     * @return the items no entry or valued group covers, in inventory order
     */
    static List<String> leftovers(BaseValuesModel model, Loadout loadout) {
        Set<String> covered = new HashSet<>();
        for (GroupConvention.Group group : GroupConvention.describe(model)) {
            if (model.member(TAG_PREFIX + group.name()) != null) {
                covered.addAll(group.members());
            }
        }
        return loadout.items().stream()
                .filter(item -> !AIR.equals(item) && !covered.contains(item))
                .filter(item -> model.member(item) == null && model.member(fileId(item)) == null)
                .toList();
    }

    /**
     * @param model   the standing file
     * @param loadout the loadout
     * @param path    the file the template would be saved to
     * @return the template and the items the standing file leaves out of it
     */
    static JsonObject answer(BaseValuesModel model, Loadout loadout, String path) {
        JsonObject answer = new JsonObject();
        answer.add("template", JsonParser.parseString(ModelJson.toJson(build(loadout), path)));
        JsonArray items = new JsonArray();
        leftovers(model, loadout).forEach(items::add);
        answer.add("uncoveredItems", items);
        return answer;
    }

    private static String fileId(String id) {
        return id.startsWith(DEFAULT_NAMESPACE) ? id.substring(DEFAULT_NAMESPACE.length()) : id;
    }

    private static String quote(String text) {
        return new JsonPrimitive(text).toString();
    }
}
