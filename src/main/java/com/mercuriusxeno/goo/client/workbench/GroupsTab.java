package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.Processes;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Groups tab's state with no Screen: each group the groups describe operation answers, the selected group and
 * member, the picker's choices, and the apply-to-group requests, as index.html's groups section holds them
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public final class GroupsTab {
    private static final String GROUPS = "groups";
    private static final String GROUP = "group";
    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String KIND_TAG = "tag";
    private static final String MEMBERS = "members";
    private static final String ID = "id";
    private static final String KNOWN = "known";
    private static final String BASE = "base";
    private static final String TWEAKS = "tweaks";
    private static final String TWEAK = "tweak";
    private static final String MEMBER = "member";
    private static final String COUNT = "count";
    private static final String TRAIT = "trait";
    private static final String TRAIT_TEXT = "traitText";
    private static final String PREVIEW = "preview";
    private static final String PICKS = "picks";
    private static final String MODEL = "model";
    private static final String NOT_IN_INVENTORY = "not in the inventory";
    private static final String TIMES = " × ";
    private static final String TRAIT_MARK = "+$";
    private static final String NO_NOTE = "";
    private static final String CREATE = "create";
    private static final String CREATE_TAG = "createTag";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String TAG_MARK = "#";
    private static final String NAMESPACE_MARK = ":";
    private static final String KEY = "key";
    private static final String TEXT = "text";
    private static final String CONVERSIONS = "_conversions";
    private static final String ENTRIES = "entries";
    private static final String GROUPS_SECTION = "_groups";
    // A #name reference stands whole: no name character or namespace mark runs on either side of it.
    private static final String REFERENCE_START = "(?<![\\w:#])";
    private static final String REFERENCE_END = "(?![\\w:])";
    private static final String NAMESPACE_PATTERN = "^.*:";
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg$";
    private static final String TRAIT_SUFFIX = "_trait";

    private final ManagerDraft draft;
    private final List<Group> groups = new ArrayList<>();
    private @Nullable String selectedGroup;
    private @Nullable String selectedMember;
    private String searchText = NO_NOTE;
    private List<ValuesTab.ManagerEntry> searchNames = List.of();

    /**
     * @param draft the draft every tab of the manager edits
     */
    public GroupsTab(ManagerDraft draft) {
        this.draft = draft;
    }

    /**
     * One member of a group as the describe operation answers it.
     *
     * @param id    the member's item id or #tag
     * @param known whether the inventory holds it
     */
    public record Member(String id, boolean known) {
    }

    /**
     * One member's count and trait over the group's base.
     *
     * @param member    the member's id
     * @param count     how many standards the member's line multiplies
     * @param trait     the trait constant's name, null for none
     * @param traitText the trait constant's value, null for none
     */
    public record Tweak(String member, int count, @Nullable String trait, @Nullable String traitText) {
    }

    /**
     * One group as the describe operation answers it.
     *
     * @param name    the group's name, after #
     * @param members its members in file order
     * @param base    its base line, null where the file holds none
     * @param tweaks  each member's tweak
     * @param preview the lines the file holds for the group
     * @param tag     whether it is a tag referent, a game tag whose members the game holds, rather than a _groups
     *                group (decision tag-referent-lists-the-games-members-and-values-them)
     */
    public record Group(String name, List<Member> members, @Nullable String base, List<Tweak> tweaks,
                        List<String> preview, boolean tag) {
    }

    /**
     * One group in the list.
     *
     * @param name         the group's name
     * @param members      how many members it holds
     * @param unknownCount how many of them the inventory lacks
     */
    public record GroupRow(String name, List<String> members, int unknownCount) {
    }

    /**
     * One member card of the selected group.
     *
     * @param id    the member's id
     * @param name  the inventory's name for it, else its id
     * @param known whether the inventory holds it
     * @param note  its tweak as count and trait, or why it stands apart
     */
    public record MemberRow(String id, String name, boolean known, String note) {
    }

    /**
     * @param describeJson the groups describe operation's reply
     */
    public void readDescribed(String describeJson) {
        readGroups(JsonParser.parseString(describeJson).getAsJsonObject().getAsJsonArray(GROUPS));
    }

    /**
     * Takes an apply's reply: its model becomes the draft and its groups the listing.
     *
     * @param appliedJson the groups apply operation's reply
     */
    public void readApplied(String appliedJson) {
        JsonObject answer = JsonParser.parseString(appliedJson).getAsJsonObject();
        draft.adopt(answer.getAsJsonObject(MODEL));
        readGroups(answer.getAsJsonArray(GROUPS));
    }

    private void readGroups(JsonArray described) {
        groups.clear();
        for (JsonElement element : described) {
            JsonObject json = element.getAsJsonObject();
            List<Member> members = json.getAsJsonArray(MEMBERS).asList().stream().map(JsonElement::getAsJsonObject)
                    .map(member -> new Member(member.get(ID).getAsString(), member.get(KNOWN).getAsBoolean()))
                    .toList();
            List<Tweak> tweaks = json.getAsJsonArray(TWEAKS).asList().stream().map(JsonElement::getAsJsonObject)
                    .map(tweak -> new Tweak(tweak.get(MEMBER).getAsString(), tweak.get(COUNT).getAsInt(),
                            text(tweak, TRAIT), text(tweak, TRAIT_TEXT)))
                    .toList();
            List<String> preview = json.getAsJsonArray(PREVIEW).asList().stream().map(JsonElement::getAsString)
                    .toList();
            groups.add(new Group(json.get(NAME).getAsString(), members, text(json, BASE), tweaks, preview,
                    KIND_TAG.equals(text(json, KIND))));
        }
        if (selected().isEmpty()) {
            selectedGroup = null;
            selectedMember = null;
        }
    }

    /**
     * @param text      the typed search text
     * @param inventory the inventory's items and tags, for each member's name
     */
    public void search(String text, List<ValuesTab.ManagerEntry> inventory) {
        searchText = text.trim().toLowerCase(Locale.ROOT);
        searchNames = inventory;
    }

    // Decision groups-search-matches-a-name-or-any-member.
    private boolean matchesSearch(Group group) {
        return searchText.isEmpty() || holdsSearch(group.name()) || group.members().stream()
                .anyMatch(member -> holdsSearch(member.id()) || searchNames.stream()
                        .anyMatch(entry -> entry.id().equals(member.id()) && holdsSearch(entry.name())));
    }

    private boolean holdsSearch(String text) {
        return text.toLowerCase(Locale.ROOT).contains(searchText);
    }

    /**
     * @return each group's row in file order that the search keeps
     */
    public List<GroupRow> groupRows() {
        return groups.stream().filter(this::matchesSearch).map(group -> new GroupRow(group.name(),
                group.members().stream().map(Member::id).toList(),
                (int) group.members().stream().filter(member -> !member.known()).count())).toList();
    }

    /**
     * @param name the group to select; its member selection clears
     */
    public void selectGroup(String name) {
        selectedGroup = name;
        selectedMember = null;
    }

    /**
     * @param id the member whose tweak the editor shows
     */
    public void selectMember(@Nullable String id) {
        selectedMember = id;
    }

    /**
     * @return the selected group, empty while none is selected
     */
    public Optional<Group> selected() {
        return groups.stream().filter(group -> group.name().equals(selectedGroup)).findFirst();
    }

    /**
     * @param name a group's name
     * @return the group under it, empty where none stands
     */
    public Optional<Group> group(String name) {
        return groups.stream().filter(group -> group.name().equals(name)).findFirst();
    }

    /**
     * @param name a group's name
     * @return the iterators whose source names it, by key, in file order
     */
    public List<String> referents(String name) {
        // Decision group-card-lists-its-referents-as-iterator-links.
        List<String> found = new ArrayList<>();
        for (JsonElement element : draft.sectionEntries(CONVERSIONS)) {
            JsonObject entry = element.getAsJsonObject();
            String key = text(entry, KEY);
            if (key != null && sourceOf(key, text(entry, TEXT)).filter(name::equals).isPresent()) {
                found.add(key);
            }
        }
        return List.copyOf(found);
    }

    // The group an iterator's assignment copies from, empty for a mutator or a line no form reads.
    private static Optional<String> sourceOf(String key, @Nullable String value) {
        if (value == null || !ProcessesTab.isIterator(key)) {
            return Optional.empty();
        }
        return Processes.parse(key, value) instanceof Processes.Assignment assignment
                ? Optional.of(assignment.source()) : Optional.empty();
    }

    /**
     * @param name      a tag referent's name, as the file spells it after #
     * @param inventory the client's items and tags
     * @return the tag's fully qualified #namespace:path id, from the listed tag it names, the minecraft namespace
     *     where none is listed (decision detail-is-members-plus-or-tag-members-with-the-tag-id)
     */
    static String tagId(String name, List<ValuesTab.ManagerEntry> inventory) {
        String id = inventory.stream().filter(ValuesTab.ManagerEntry::tag).map(ValuesTab.ManagerEntry::id)
                .filter(entry -> referentName(entry).equals(name)).findFirst()
                .orElse(name.contains(NAMESPACE_MARK) ? name : DEFAULT_NAMESPACE + name);
        return id.startsWith(TAG_MARK) ? id : TAG_MARK + id;
    }

    /**
     * @return the selected member's id, empty while none is selected
     */
    public Optional<String> selectedMember() {
        return Optional.ofNullable(selectedMember);
    }

    /**
     * @param member the member
     * @return the selected group's tweak for it, empty where it holds none
     */
    public Optional<Tweak> tweakOf(String member) {
        return selected().flatMap(group -> group.tweaks().stream().filter(tweak -> tweak.member().equals(member))
                .findFirst());
    }

    /**
     * @param inventory the inventory's items and tags, for each member's name
     * @return the selected group's member cards, none while no group is selected
     */
    public List<MemberRow> memberRows(List<ValuesTab.ManagerEntry> inventory) {
        return selected().map(group -> group.members().stream().map(member -> new MemberRow(member.id(),
                inventory.stream().filter(entry -> entry.id().equals(member.id())).map(ValuesTab.ManagerEntry::name)
                        .findFirst().orElse(member.id()), member.known(), noteOf(member))).toList())
                .orElse(List.of());
    }

    private String noteOf(Member member) {
        if (!member.known()) {
            return NOT_IN_INVENTORY;
        }
        return tweakOf(member.id()).map(tweak -> (tweak.count() == 1 ? NO_NOTE : tweak.count() + TIMES)
                + (tweak.trait() == null ? NO_NOTE : TRAIT_MARK + tweak.trait())).orElse(NO_NOTE);
    }

    /**
     * @param pick the item or #tag to add
     * @return the apply request adding it to the selected group
     */
    public String addPickRequest(String pick) {
        List<String> picks = new ArrayList<>(memberIds());
        picks.add(pick);
        return picksRequest(picks);
    }

    /**
     * @param member the member to remove
     * @return the apply request leaving the selected group's other members
     */
    public String removeMemberRequest(String member) {
        return picksRequest(memberIds().stream().filter(id -> !id.equals(member)).toList());
    }

    /**
     * @param base the group's base line
     * @return the apply request writing it
     */
    public String baseRequest(String base) {
        JsonObject request = groupRequest();
        request.addProperty(BASE, base);
        return request.toString();
    }

    /**
     * A blank trait value writes no trait, as the page's editor does.
     *
     * @param member    the member
     * @param count     how many standards its line multiplies
     * @param trait     the trait constant's name
     * @param traitText the trait constant's value, blank for no trait
     * @return the apply request writing the member's line
     */
    public String tweakRequest(String member, int count, String trait, String traitText) {
        boolean hasTrait = !traitText.isBlank();
        JsonObject tweak = new JsonObject();
        tweak.addProperty(MEMBER, member);
        tweak.addProperty(COUNT, count);
        if (hasTrait) {
            tweak.addProperty(TRAIT, trait.trim());
            tweak.addProperty(TRAIT_TEXT, traitText.trim());
        } else {
            tweak.add(TRAIT, JsonNull.INSTANCE);
            tweak.add(TRAIT_TEXT, JsonNull.INSTANCE);
        }
        JsonObject request = groupRequest();
        request.add(TWEAK, tweak);
        return request.toString();
    }

    /**
     * Writes the selected group under a new name in the draft: its _groups entry, its #name value and every #name
     * reference, an iterator's among them, so its members and value carry over (decision
     * group-detail-puts-plus-and-rename-beside-the-name).
     *
     * @param typed the new name, its # optional
     * @return whether the group took the name: false where it is blank or another group holds it
     */
    public boolean renameSelected(String typed) {
        String name = referentName(typed.trim());
        String from = selectedGroup;
        if (from == null || !isFreeName(name)) {
            return false;
        }
        Pattern reference = Pattern.compile(REFERENCE_START + Pattern.quote(TAG_MARK + from) + REFERENCE_END);
        String replacement = Matcher.quoteReplacement(TAG_MARK + name);
        for (JsonElement element : draft.members()) {
            renameIn(element.getAsJsonObject(), from, name, reference, replacement);
        }
        // Decision group-card-is-name-then-members-then-value: the card reads the new name from this frame, before
        // the next describe answers.
        groups.replaceAll(group -> group.name().equals(from) ? new Group(name, group.members(), group.base(),
                group.tweaks(), group.preview(), group.tag()) : group);
        selectedGroup = name;
        return true;
    }

    /**
     * @return the entries every tab marked for the next save to remove
     */
    RemovalMarks removals() {
        return draft.removals();
    }

    /**
     * Deletes a group from the draft: its _groups entry and its #name value, dropping the selection where it held
     * the group (decision rename-is-a-pencil-and-delete-a-red-x-everywhere). References to #name elsewhere stay, as
     * a deleted mutator's do, and read as unknown until rewritten.
     *
     * @param name the group's name, no #
     * @return whether the draft held the group
     */
    public boolean deleteGroup(String name) {
        com.google.gson.JsonArray members = draft.members();
        boolean removed = false;
        for (int index = members.size() - 1; index >= 0; index--) {
            JsonObject member = members.get(index).getAsJsonObject();
            String held = keyOf(member);
            if ((TAG_MARK + name).equals(held)) {
                members.remove(index);
                removed = true;
            } else if (GROUPS_SECTION.equals(held)) {
                removed |= removeEntry(member, name);
            }
        }
        if (name.equals(selectedGroup)) {
            selectedGroup = null;
        }
        return removed;
    }

    private static boolean removeEntry(JsonObject section, String key) {
        JsonElement entries = section.get(ENTRIES);
        if (entries == null || !entries.isJsonArray()) {
            return false;
        }
        com.google.gson.JsonArray array = entries.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            if (key.equals(keyOf(array.get(index).getAsJsonObject()))) {
                array.remove(index);
                return true;
            }
        }
        return false;
    }

    private boolean isFreeName(String name) {
        return !name.isEmpty() && groups.stream().noneMatch(group -> group.name().equals(name));
    }

    // Renames the group's _groups entry and its #name member, and rewrites its references in every text.
    private static void renameIn(JsonObject member, String from, String to, Pattern reference, String replacement) {
        String held = keyOf(member);
        if ((TAG_MARK + from).equals(held)) {
            member.addProperty(KEY, TAG_MARK + to);
        }
        rewriteReferences(member, reference, replacement);
        boolean groupsSection = GROUPS_SECTION.equals(held);
        for (JsonObject child : entriesOf(member)) {
            if (groupsSection && from.equals(keyOf(child))) {
                child.addProperty(KEY, to);
            }
            renameIn(child, from, to, reference, replacement);
        }
    }

    private static void rewriteReferences(JsonObject member, Pattern reference, String replacement) {
        JsonElement text = member.get(TEXT);
        if (text != null && text.isJsonPrimitive()) {
            member.addProperty(TEXT, reference.matcher(text.getAsString()).replaceAll(replacement));
        }
    }

    private static @Nullable String keyOf(JsonObject member) {
        JsonElement key = member.get(KEY);
        return key == null || key.isJsonNull() ? null : key.getAsString();
    }

    private static List<JsonObject> entriesOf(JsonObject member) {
        JsonElement entries = member.get(ENTRIES);
        return entries != null && entries.isJsonArray()
                ? entries.getAsJsonArray().asList().stream().map(JsonElement::getAsJsonObject).toList() : List.of();
    }

    private List<String> memberIds() {
        return selected().map(group -> group.members().stream().map(Member::id).toList()).orElse(List.of());
    }

    private String picksRequest(List<String> picks) {
        JsonObject request = groupRequest();
        JsonArray array = new JsonArray();
        picks.forEach(array::add);
        request.add(PICKS, array);
        return request.toString();
    }

    private JsonObject groupRequest() {
        JsonObject request = draft.request();
        request.addProperty(GROUP, selectedGroup);
        return request;
    }

    private static @Nullable String text(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /**
     * A member's own line as the builder opens it: count times the group's value plus the extra its trait adds.
     *
     * @param count how many times the line takes the group's value
     * @param trait the trait prefab's name, which holds the extra
     * @param extra the trait's value as the draft shows it, empty for none
     */
    public record MemberLine(int count, String trait, String extra) {
    }

    /**
     * @param row   a group's row
     * @param names an item's display name by its id
     * @return the row's runs: its #name, then each member as its item icon
     *         (decision groups-list-members-as-icons-without-headers-or-count)
     */
    public static List<ExpressionLines.Run> rowRuns(GroupRow row, Function<String, String> names) {
        List<ExpressionLines.Run> runs = new ArrayList<>();
        runs.add(new ExpressionLines.Run(ExpressionLines.Kind.TEXT, TAG_MARK + row.name(), null));
        row.members().forEach(id -> runs.add(new ExpressionLines.Run(ExpressionLines.Kind.ITEM, names.apply(id), id)));
        return runs;
    }

    /**
     * @param member a member of the selected group
     * @return its line prefilled from its tweak, or count 1 and a trait named for it where it holds none
     *         (decision group-controls-name-add-and-build)
     */
    public MemberLine memberLine(String member) {
        return tweakOf(member).map(tweak -> new MemberLine(tweak.count(),
                        tweak.trait() == null ? traitNameFor(member) : tweak.trait(),
                        tweak.traitText() == null ? NO_NOTE : tweak.traitText()))
                .orElseGet(() -> new MemberLine(1, traitNameFor(member), NO_NOTE));
    }

    private static String traitNameFor(String member) {
        return member.replaceFirst(NAMESPACE_PATTERN, NO_NOTE).replaceFirst(SPAWN_EGG_SUFFIX, NO_NOTE) + TRAIT_SUFFIX;
    }

    /**
     * @param inventory the inventory's items and tags
     * @return a dropdown over every item tag no listed row names, each its #id and member count
     *         (decision add-tag-picks-a-game-tag-and-writes-its-entry)
     */
    SearchDropdown addTagChoices(List<ValuesTab.ManagerEntry> inventory) {
        List<String> listed = groups.stream().map(Group::name).toList();
        return SearchDropdown.overTags(inventory.stream().filter(ValuesTab.ManagerEntry::tag)
                .filter(entry -> !listed.contains(referentName(entry.id()))).toList());
    }

    /**
     * @param tagId the picked tag's #id
     * @return the apply request writing the tag's #tag entry, which the next describe lists as a referent
     */
    public String createTagRequest(String tagId) {
        JsonObject request = draft.request();
        request.addProperty(GROUP, referentName(tagId));
        request.addProperty(CREATE_TAG, true);
        return request.toString();
    }

    /**
     * @param tagId a tag's #id, namespaced or bare
     * @return the tag's name as the file spells it after #, bare in the minecraft namespace
     */
    static String referentName(String tagId) {
        String name = tagId.startsWith(TAG_MARK) ? tagId.substring(TAG_MARK.length()) : tagId;
        return name.startsWith(DEFAULT_NAMESPACE) ? name.substring(DEFAULT_NAMESPACE.length()) : name;
    }

    /**
     * @param name the new group's name, after #
     * @return the apply request creating it with no member (decision group-controls-name-add-and-build)
     */
    public String createRequest(String name) {
        JsonObject request = draft.request();
        request.addProperty(GROUP, name.trim());
        request.addProperty(CREATE, true);
        return request.toString();
    }
}
