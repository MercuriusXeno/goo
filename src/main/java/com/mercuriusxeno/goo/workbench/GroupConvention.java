package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Groups pane's writes: a group's members in _groups, its one base line as a #group entry, and a line
 * per member that differs, a count over the standard plus the member's trait, such as
 * "donkey_spawn_egg": "5($spawn_egg + $donkey_trait)", the trait a tree constant
 * (decisions workbench-authors-base-values-whole, template-first-then-defaults-by-convention). Every line
 * stays in the syntax the loader already reads: a leading count multiplies the parenthesized sum.
 */
final class GroupConvention {
    private static final String GROUPS = "_groups";
    private static final String CONSTANTS = "_constants";
    private static final String TAG_PREFIX = "#";
    private static final String CONSTANT_PREFIX = "$";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String QUOTE = "\"";
    private static final String MEMBER_SEPARATOR = ",\n";
    private static final String NEWLINE = "\n";
    private static final String PLUS = " + ";
    private static final String EMPTY_ARRAY = "[]";
    private static final String ARRAY_OPEN = "[";
    private static final String NO_SEPARATOR = "";
    private static final String SUM_OPEN = "(";
    private static final String SUM_CLOSE = ")";
    private static final String NO_BASE_TO_TWEAK = " holds no base line to tweak";
    private static final int PLAIN_COUNT = 1;
    private static final int COUNT_GROUP = 1;
    private static final int TRAIT_GROUP = 2;
    // A member line: an optional count, then the base alone or the base plus one $trait, in parentheses or not.
    // The sections whose entries value an item or a #name as a top-level entry does.
    private static final Set<String> VALUE_SECTIONS = Set.of("_conversions", "_post_conversions");
    // A #name inside a value expression, such as #copper in "#copper @exposed_metal".
    private static final Pattern TAG_REFERENCE = Pattern.compile("#([A-Za-z0-9_:/.\\-]+)");
    private static final String TWEAK_FORMAT ="^(?:(\\d+)\\s*)?\\(?\\s*%s(?:\\s*\\+\\s*\\$([A-Za-z0-9_]+))?\\s*\\)?$";

    private GroupConvention() {
    }

    /**
     * A member that differs from its group's base.
     *
     * @param member    the member's namespaced id
     * @param count     the count its sum is multiplied by
     * @param trait     the trait constant's name, or null where the member adds none
     * @param traitText the trait constant's text, or null where it adds none
     */
    record Tweak(String member, int count, @Nullable String trait, @Nullable String traitText) {
    }

    /**
     * A group as the pane shows it.
     *
     * @param name    the group's name, as #name reads it
     * @param members the members' namespaced ids, in file order
     * @param base    the base expression the #name entry holds, or null where none stands
     * @param tweaks  the members that differ from the base
     */
    record Group(String name, List<String> members, @Nullable String base, List<Tweak> tweaks) {
    }

    /**
     * @param picks      item ids and #tag ids, in the order picked
     * @param tagMembers each item tag's member ids
     * @return the item ids, each tag expanded into its members, each id once
     */
    static List<String> expand(List<String> picks, Map<String, List<String>> tagMembers) {
        Set<String> members = new LinkedHashSet<>();
        for (String pick : picks) {
            if (pick.startsWith(TAG_PREFIX)) {
                members.addAll(tagMembers.getOrDefault(namespaced(pick.substring(TAG_PREFIX.length())), List.of()));
            } else {
                members.add(namespaced(pick));
            }
        }
        return List.copyOf(members);
    }

    /**
     * @param model   the model
     * @param group   the group's name
     * @param members the members' ids
     * @return the model with the group's array set, one member per line as the file writes its groups
     */
    static BaseValuesModel withGroup(BaseValuesModel model, String group, List<String> members) {
        if (members.isEmpty()) {
            return model.withEntry(GROUPS, group, EMPTY_ARRAY);
        }
        StringBuilder text = new StringBuilder(ARRAY_OPEN).append(NEWLINE);
        for (int i = 0; i < members.size(); i++) {
            text.append(i == 0 ? NO_SEPARATOR : MEMBER_SEPARATOR).append(model.indentUnit())
                    .append(QUOTE).append(fileId(members.get(i))).append(QUOTE);
        }
        return model.withEntry(GROUPS, group, text.append(NEWLINE).append(']').toString());
    }

    /**
     * @param model the model
     * @param group the group's name
     * @return whether _groups names it
     */
    static boolean holdsGroup(BaseValuesModel model, String group) {
        BaseValuesModel.Member groups = model.member(GROUPS);
        return groups != null && groups.entries().stream().anyMatch(entry -> group.equals(entry.key()));
    }

    /**
     * @param model the model
     * @param group the group's name
     * @param base  the base expression every member takes, or blank to drop the base line
     * @return the model with the #group line set
     */
    static BaseValuesModel withBase(BaseValuesModel model, String group, String base) {
        String key = TAG_PREFIX + group;
        return base.isBlank() ? model.withoutTopLevel(key) : model.withTopLevel(key, quoted(base.trim()));
    }

    /**
     * @param model the model
     * @param tag   the tag's name, as the file spells it after #
     * @return the model with a #tag entry of no value text where the file holds none, where a #group base line goes
     */
    static BaseValuesModel withTagEntry(BaseValuesModel model, String tag) {
        String key = TAG_PREFIX + tag;
        return model.member(key) != null ? model : model.withTopLevel(key, quoted(NO_SEPARATOR));
    }

    /**
     * Writes a member's line from its count and trait, and the trait constant beside the others; a member with
     * count 1 and no trait takes the base alone, so its line is dropped.
     *
     * @param model     the model
     * @param group     the group's name, whose base the line reads
     * @param member    the member's id
     * @param count     the count the sum is multiplied by
     * @param trait     the trait constant's name, or null for none
     * @param traitText the trait constant's text, or null to leave the constant as it stands
     * @return the model with the member's line set
     */
    static BaseValuesModel withTweak(BaseValuesModel model, String group, String member, int count,
                                     @Nullable String trait, @Nullable String traitText) {
        String base = baseOf(model, group);
        if (base == null) {
            throw new IllegalArgumentException(TAG_PREFIX + group + NO_BASE_TO_TWEAK);
        }
        BaseValuesModel withTrait = trait != null && traitText != null
                ? model.withEntry(CONSTANTS, trait, traitText) : model;
        String key = existingKey(withTrait, member);
        if (count == PLAIN_COUNT && trait == null) {
            return withTrait.withoutTopLevel(key);
        }
        String sum = trait == null ? base : base + PLUS + CONSTANT_PREFIX + trait;
        String line = count == PLAIN_COUNT ? sum : count + SUM_OPEN + sum + SUM_CLOSE;
        return withTrait.withTopLevel(key, quoted(line));
    }

    /**
     * @param model     the model
     * @param group     the group's name
     * @param inventory the inventory's item and block ids
     * @return the group's members the inventory does not hold
     */
    static List<String> missing(BaseValuesModel model, String group, Set<String> inventory) {
        return membersOf(model, group).stream().filter(member -> !inventory.contains(member)).toList();
    }

    /**
     * @param model the model
     * @return every group, its members, its base and the members that differ, in file order
     */
    static List<Group> describe(BaseValuesModel model) {
        BaseValuesModel.Member groups = model.member(GROUPS);
        if (groups == null) {
            return List.of();
        }
        List<Group> described = new ArrayList<>();
        for (BaseValuesModel.Member entry : groups.entries()) {
            String name = entry.key();
            List<String> members = membersOf(model, name);
            String base = baseOf(model, name);
            described.add(new Group(name, members, base, base == null ? List.of() : tweaksOf(model, members, base)));
        }
        return described;
    }

    /**
     * Every #name the file values or references that no _groups entry holds: a #name key at top level, in
     * _conversions or in _post_conversions, or a #name inside a value expression there.
     *
     * @param model the model
     * @return each such name, as the file spells it after #, once, in file order
     */
    // decision groups-lists-custom-groups-and-every-tag-a-valuation-references
    static List<String> referencedTags(BaseValuesModel model) {
        Set<String> names = new LinkedHashSet<>();
        for (BaseValuesModel.Member member : model.members()) {
            if (member.kind() == BaseValuesModel.Kind.VALUE) {
                collectTags(member, names);
            } else if (VALUE_SECTIONS.contains(member.key())) {
                member.entries().forEach(entry -> collectTags(entry, names));
            }
        }
        names.removeIf(name -> holdsGroup(model, name));
        return List.copyOf(names);
    }

    private static void collectTags(BaseValuesModel.Member entry, Set<String> names) {
        String key = entry.key();
        if (key != null && key.startsWith(TAG_PREFIX)) {
            names.add(key.substring(TAG_PREFIX.length()));
        }
        Matcher reference = TAG_REFERENCE.matcher(entry.text());
        while (reference.find()) {
            names.add(reference.group(1));
        }
    }

    /**
     * A tag referent as the pane shows it: its members are the inventory's, its base and tweaks the file's.
     *
     * @param model      the model
     * @param tag        the tag's name, as the file spells it after #
     * @param tagMembers each item tag's member ids
     * @return the tag as a group
     */
    // decision tag-referent-lists-the-games-members-and-values-them
    static Group describeTag(BaseValuesModel model, String tag, Map<String, List<String>> tagMembers) {
        List<String> members = tagMembers.getOrDefault(namespaced(tag), List.of());
        // An entry Add tag wrote holds no value text until the builder sets one.
        String written = baseOf(model, tag);
        String base = written == null || written.isBlank() ? null : written;
        return new Group(tag, members, base, base == null ? List.of() : tweaksOf(model, members, base));
    }

    private static List<Tweak> tweaksOf(BaseValuesModel model, List<String> members, String base) {
        Pattern line = Pattern.compile(TWEAK_FORMAT.formatted(Pattern.quote(base)));
        List<Tweak> tweaks = new ArrayList<>();
        for (String member : members) {
            BaseValuesModel.Member entry = model.member(existingKey(model, member));
            String expression = entry == null ? null : stringValue(entry.text());
            Matcher match = expression == null ? null : line.matcher(expression.trim());
            if (match == null || !match.matches()) {
                continue;
            }
            int count = match.group(COUNT_GROUP) == null ? PLAIN_COUNT : Integer.parseInt(match.group(COUNT_GROUP));
            String trait = match.group(TRAIT_GROUP);
            if (count != PLAIN_COUNT || trait != null) {
                tweaks.add(new Tweak(member, count, trait, trait == null ? null : constantText(model, trait)));
            }
        }
        return tweaks;
    }

    private static @Nullable String constantText(BaseValuesModel model, String name) {
        BaseValuesModel.Member constants = model.member(CONSTANTS);
        if (constants == null) {
            return null;
        }
        return constants.entries().stream().filter(entry -> name.equals(entry.key()))
                .map(BaseValuesModel.Member::text).findFirst().orElse(null);
    }

    private static List<String> membersOf(BaseValuesModel model, String group) {
        BaseValuesModel.Member groups = model.member(GROUPS);
        if (groups == null) {
            return List.of();
        }
        for (BaseValuesModel.Member entry : groups.entries()) {
            if (group.equals(entry.key())) {
                return idsIn(entry.text());
            }
        }
        return List.of();
    }

    private static List<String> idsIn(String arrayText) {
        try {
            JsonElement parsed = JsonParser.parseString(arrayText);
            if (!parsed.isJsonArray()) {
                return List.of();
            }
            JsonArray array = parsed.getAsJsonArray();
            return array.asList().stream().map(element -> namespaced(element.getAsString())).toList();
        } catch (JsonParseException | IllegalStateException e) {
            return List.of();
        }
    }

    private static @Nullable String baseOf(BaseValuesModel model, String group) {
        BaseValuesModel.Member line = model.member(TAG_PREFIX + group);
        return line == null ? null : stringValue(line.text());
    }

    private static @Nullable String stringValue(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonPrimitive() && parsed.getAsJsonPrimitive().isString() ? parsed.getAsString() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /**
     * The key the file already spells the member's line under, bare or namespaced, else the file's usual
     * spelling.
     *
     * @param model  the model
     * @param member the member's id
     * @return the key the member's line stands under
     */
    private static String existingKey(BaseValuesModel model, String member) {
        String namespacedId = namespaced(member);
        return model.member(namespacedId) != null ? namespacedId : fileId(namespacedId);
    }

    /**
     * An id as the file spells it: bare in the minecraft namespace, which Identifier.parse reads back.
     *
     * @param id the id, bare or namespaced
     * @return the id as the file spells it
     */
    private static String fileId(String id) {
        String namespacedId = namespaced(id);
        return namespacedId.startsWith(DEFAULT_NAMESPACE) ? namespacedId.substring(DEFAULT_NAMESPACE.length())
                : namespacedId;
    }

    private static String namespaced(String id) {
        return id.indexOf(':') < 0 ? DEFAULT_NAMESPACE + id : id;
    }

    private static String quoted(String expression) {
        com.google.gson.JsonPrimitive primitive = new com.google.gson.JsonPrimitive(expression);
        return primitive.toString();
    }
}
