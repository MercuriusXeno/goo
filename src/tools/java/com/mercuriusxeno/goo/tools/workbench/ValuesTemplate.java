package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The template a loadout starts from: a group per item tag of two or more members, an empty #group entry for
 * each, every item no such tag covers as an empty entry, and the goo types listed for reference, so a heavily
 * modded loadout starts from a file naming everything; and, for a file that stands, what it leaves out
 * (decision template-first-then-defaults-by-convention).
 */
final class ValuesTemplate {
    private static final String NEWLINE = "\n";
    private static final String INDENT = "    ";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    // An empty value object: the loader reads {} as no value, and throws on an empty expression string.
    private static final String EMPTY_VALUE = "{}";
    private static final String GROUPS = "_groups";
    private static final String TAG_PREFIX = "#";
    private static final String AIR = "minecraft:air";
    private static final int GROUPED_AT = 2;
    // Sections the loader reads, empty in a template; _goo_types is a list the loader passes over, for reference.
    private static final List<String> EMPTY_SECTIONS = List.of(
            "\"_restricted\": []", "\"_conversions\": {}", "\"_post_conversions\": {}");

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
     * What a standing file leaves out.
     *
     * @param tags  the item tags of two or more members no group carries
     * @param items the items no entry or valued group covers, in inventory order
     */
    record Leftovers(List<String> tags, List<String> items) {
    }

    /**
     * @param loadout the loadout
     * @return the template as a model
     */
    static BaseValuesModel build(Loadout loadout) {
        List<String> tags = groupedTags(loadout);
        Set<String> grouped = new HashSet<>();
        tags.forEach(tag -> grouped.addAll(loadout.itemTags().get(tag)));
        List<String> lines = new ArrayList<>();
        lines.add("\"_constants\": {}");
        lines.add(groupsSection(tags, loadout));
        tags.forEach(tag -> lines.add(quote(TAG_PREFIX + groupName(tag)) + ": " + EMPTY_VALUE));
        loadout.items().stream().filter(item -> !AIR.equals(item) && !grouped.contains(item))
                .forEach(item -> lines.add(quote(fileId(item)) + ": " + EMPTY_VALUE));
        lines.addAll(EMPTY_SECTIONS);
        lines.add("\"_goo_types\": [" + String.join(", ", loadout.gooTypes().stream().map(ValuesTemplate::quote)
                .toList()) + "]");
        return BaseValuesModel.parse("{" + NEWLINE + INDENT + String.join("," + NEWLINE + INDENT, lines) + NEWLINE
                + "}" + NEWLINE);
    }

    /**
     * @param model   the standing file
     * @param loadout the loadout
     * @return the grouped tags no group carries and the items nothing covers
     */
    static Leftovers leftovers(BaseValuesModel model, Loadout loadout) {
        List<GroupConvention.Group> groups = GroupConvention.describe(model);
        Set<String> groupNames = new HashSet<>();
        Set<String> covered = new HashSet<>();
        for (GroupConvention.Group group : groups) {
            groupNames.add(group.name());
            if (model.member(TAG_PREFIX + group.name()) != null) {
                covered.addAll(group.members());
            }
        }
        List<String> tags = groupedTags(loadout).stream()
                .filter(tag -> !groupNames.contains(groupName(tag)) && !groupNames.contains(tag)).toList();
        List<String> items = loadout.items().stream()
                .filter(item -> !AIR.equals(item) && !covered.contains(item))
                .filter(item -> model.member(item) == null && model.member(fileId(item)) == null)
                .toList();
        return new Leftovers(tags, items);
    }

    private static List<String> groupedTags(Loadout loadout) {
        return loadout.itemTags().entrySet().stream().filter(tag -> tag.getValue().size() >= GROUPED_AT)
                .map(Map.Entry::getKey).sorted().toList();
    }

    private static String groupsSection(List<String> tags, Loadout loadout) {
        if (tags.isEmpty()) {
            return quote(GROUPS) + ": {}";
        }
        List<String> groups = new ArrayList<>();
        String memberIndent = INDENT.repeat(3);
        for (String tag : tags) {
            List<String> members = loadout.itemTags().get(tag).stream().map(id -> memberIndent + quote(fileId(id)))
                    .toList();
            groups.add(INDENT.repeat(2) + quote(groupName(tag)) + ": [" + NEWLINE
                    + String.join("," + NEWLINE, members) + NEWLINE + INDENT.repeat(2) + "]");
        }
        return quote(GROUPS) + ": {" + NEWLINE + String.join("," + NEWLINE, groups) + NEWLINE + INDENT + "}";
    }

    /**
     * A minecraft tag's group takes its bare path, which #name resolves through; any other keeps its id.
     */
    private static String groupName(String tag) {
        return fileId(tag);
    }

    private static String fileId(String id) {
        return id.startsWith(DEFAULT_NAMESPACE) ? id.substring(DEFAULT_NAMESPACE.length()) : id;
    }

    private static String quote(String text) {
        return new JsonPrimitive(text).toString();
    }
}
