package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Groups pane's exchanges: every group described against the inventory, and one change applied to a
 * group (decisions workbench-authors-base-values-whole, template-first-then-defaults-by-convention).
 */
final class GroupsJson {
    private static final String GROUPS = "groups";
    private static final String NAME = "name";
    private static final String MEMBERS = "members";
    private static final String ID = "id";
    private static final String KNOWN = "known";
    private static final String BASE = "base";
    private static final String TWEAKS = "tweaks";
    private static final String MEMBER = "member";
    private static final String COUNT = "count";
    private static final String TRAIT = "trait";
    private static final String TRAIT_TEXT = "traitText";
    private static final String PREVIEW = "preview";
    private static final String GROUP = "group";
    private static final String PICKS = "picks";
    private static final String TWEAK = "tweak";
    private static final String CREATE = "create";
    private static final String MODEL = "model";
    private static final String ITEMS = "items";
    private static final String ITEM_TAGS = "item_tags";
    private static final String GOO_TYPES = "goo_types";
    private static final String CONSTANTS = "_constants";
    private static final String TAG_PREFIX = "#";
    private static final String KEY_SEPARATOR = ": ";
    private static final String QUOTE = "\"";
    private static final String DEFAULT_NAMESPACE_PATTERN = "^minecraft:";
    private static final String BLANK = "";

    private GroupsJson() {
    }

    /**
     * The inventory's ids and tags, read from its file where one stands.
     *
     * @param ids  every item id
     * @param tags each item tag's member ids
     */
    record Inventory(Set<String> ids, Map<String, List<String>> tags, List<String> items, List<String> gooTypes) {
        private static @Nullable Path cachedFile;
        private static @Nullable FileTime cachedTime;
        private static @Nullable Inventory cachedInventory;

        // decision inventory-parses-once-per-file-timestamp
        static synchronized Inventory read(Path file) throws IOException {
            if (!Files.isRegularFile(file)) {
                return new Inventory(Set.of(), Map.of(), List.of(), List.of());
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (cachedInventory == null || !file.equals(cachedFile) || !modified.equals(cachedTime)) {
                cachedInventory = parse(Files.readString(file));
                cachedFile = file;
                cachedTime = modified;
            }
            return cachedInventory;
        }

        private static Inventory parse(String text) {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            // Known ids come from items alone; a standing file's blocks section goes unread
            // (decision workbench-drops-the-blocks-list).
            Set<String> ids = new HashSet<>(idsIn(root, ITEMS));
            Map<String, List<String>> tags = new HashMap<>();
            for (JsonElement tag : root.getAsJsonArray(ITEM_TAGS)) {
                JsonObject json = tag.getAsJsonObject();
                tags.put(json.get(ID).getAsString(),
                        json.getAsJsonArray(MEMBERS).asList().stream().map(JsonElement::getAsString).toList());
            }
            return new Inventory(Set.copyOf(ids), Map.copyOf(tags), idsIn(root, ITEMS), idsIn(root, GOO_TYPES));
        }

        private static List<String> idsIn(JsonObject root, String section) {
            return root.getAsJsonArray(section).asList().stream()
                    .map(entry -> entry.getAsJsonObject().get(ID).getAsString()).toList();
        }
    }

    /**
     * @param model     the model
     * @param inventory the inventory, empty where none was dumped
     * @return every group with its members flagged against the inventory, its base, its tweaks and the lines
     *         the file holds for it
     */
    static JsonArray described(BaseValuesModel model, Inventory inventory) {
        JsonArray groups = new JsonArray();
        for (GroupConvention.Group group : GroupConvention.describe(model)) {
            JsonObject json = new JsonObject();
            json.addProperty(NAME, group.name());
            JsonArray members = new JsonArray();
            for (String member : group.members()) {
                JsonObject entry = new JsonObject();
                entry.addProperty(ID, member);
                entry.addProperty(KNOWN, inventory.ids().isEmpty() || inventory.ids().contains(member));
                members.add(entry);
            }
            json.add(MEMBERS, members);
            json.add(BASE, nullable(group.base()));
            JsonArray tweaks = new JsonArray();
            group.tweaks().forEach(tweak -> tweaks.add(tweakJson(tweak)));
            json.add(TWEAKS, tweaks);
            json.add(PREVIEW, preview(model, group));
            groups.add(json);
        }
        return groups;
    }

    /**
     * Applies the posted change, in order: the group created where it stands nowhere, the picked members, the base
     * line, one member's tweak.
     *
     * @param sent      the posted JSON: the model's members, the group's name and the change
     * @param layout    the file's layout the posted members take
     * @param inventory the inventory the picks' tags expand against
     * @return the changed model and every group described
     */
    static String applied(String sent, BaseValuesModel layout, Inventory inventory) {
        JsonObject root = JsonParser.parseString(sent).getAsJsonObject();
        BaseValuesModel model = ModelJson.merged(layout, sent);
        String group = root.get(GROUP).getAsString();
        model = created(model, root, group);
        if (root.has(PICKS)) {
            List<String> picks = root.getAsJsonArray(PICKS).asList().stream().map(JsonElement::getAsString).toList();
            model = GroupConvention.withGroup(model, group, GroupConvention.expand(picks, inventory.tags()));
        }
        if (root.has(BASE)) {
            model = GroupConvention.withBase(model, group, root.get(BASE).getAsString());
        }
        if (root.has(TWEAK)) {
            JsonObject tweak = root.getAsJsonObject(TWEAK);
            model = GroupConvention.withTweak(model, group, tweak.get(MEMBER).getAsString(),
                    tweak.get(COUNT).getAsInt(), text(tweak, TRAIT), text(tweak, TRAIT_TEXT));
        }
        JsonObject answer = new JsonObject();
        answer.add(MODEL, JsonParser.parseString(ModelJson.toJson(model, BLANK)));
        answer.add(GROUPS, described(model, inventory));
        return answer.toString();
    }

    // The create form lands a group no _groups entry names, with no member (decision group-controls-name-add-and-build).
    private static BaseValuesModel created(BaseValuesModel model, JsonObject root, String group) {
        return root.has(CREATE) && !GroupConvention.holdsGroup(model, group)
                ? GroupConvention.withGroup(model, group, List.of()) : model;
    }

    private static JsonObject tweakJson(GroupConvention.Tweak tweak) {
        JsonObject json = new JsonObject();
        json.addProperty(MEMBER, tweak.member());
        json.addProperty(COUNT, tweak.count());
        json.add(TRAIT, nullable(tweak.trait()));
        json.add(TRAIT_TEXT, nullable(tweak.traitText()));
        return json;
    }

    /**
     * The lines the file holds for a group's convention: its base line, each trait constant and each member line.
     *
     * @param model the model
     * @param group the group described
     * @return the group's lines, each as the file writes it
     */
    private static JsonArray preview(BaseValuesModel model, GroupConvention.Group group) {
        JsonArray lines = new JsonArray();
        BaseValuesModel.Member base = model.member(TAG_PREFIX + group.name());
        if (base != null) {
            lines.add(line(base.key(), base.text()));
        }
        BaseValuesModel.Member constants = model.member(CONSTANTS);
        for (GroupConvention.Tweak tweak : group.tweaks()) {
            if (tweak.trait() != null && constants != null) {
                constants.entries().stream().filter(entry -> tweak.trait().equals(entry.key())).findFirst()
                        .ifPresent(entry -> lines.add(line(entry.key(), entry.text())));
            }
            for (String key : List.of(tweak.member(), tweak.member().replaceFirst(DEFAULT_NAMESPACE_PATTERN, BLANK))) {
                BaseValuesModel.Member member = model.member(key);
                if (member != null) {
                    lines.add(line(member.key(), member.text()));
                    break;
                }
            }
        }
        return lines;
    }

    private static String line(String key, String text) {
        return QUOTE + key + QUOTE + KEY_SEPARATOR + text;
    }

    private static JsonElement nullable(@Nullable String value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private static @Nullable String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() || value.getAsString().isBlank() ? null : value.getAsString();
    }
}
