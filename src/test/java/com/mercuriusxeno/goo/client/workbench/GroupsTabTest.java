package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Groups tab's state from recorded groups describe and apply replies, with no Screen
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
class GroupsTabTest {

    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [],
               "leading": null, "trailing": null, "closingGap": null}
            ]}
            """;
    private static final String DESCRIBED = """
            {"groups": [
              {"name": "spawn_eggs", "members": [{"id": "minecraft:zombie_spawn_egg", "known": true},
                                                 {"id": "minecraft:gone_spawn_egg", "known": false}],
               "base": "$spawn_egg", "tweaks": [{"member": "minecraft:zombie_spawn_egg", "count": 2,
                                                 "trait": "zombie_trait", "traitText": "{\\"hex\\": 1}"}],
               "preview": ["\\"#spawn_eggs\\": \\"$spawn_egg\\""]},
              {"name": "logs", "members": [], "base": null, "tweaks": [], "preview": []}
            ]}
            """;
    private static final String APPLIED = """
            {"model": {"path": "", "members": [
               {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [],
                "leading": null, "trailing": null, "closingGap": null},
               {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "\\"$log\\"", "entries": [],
                "leading": null, "trailing": null, "closingGap": null}]},
             "groups": [{"name": "logs", "members": [{"id": "minecraft:oak_log", "known": true}], "base": "$log",
                         "tweaks": [], "preview": ["\\"#logs\\": \\"$log\\""]}]}
            """;
    private static final List<ValuesTab.ManagerEntry> INVENTORY = List.of(
            new ValuesTab.ManagerEntry("minecraft:zombie_spawn_egg", "Zombie Spawn Egg", false),
            new ValuesTab.ManagerEntry("minecraft:oak_log", "Oak Log", false),
            new ValuesTab.ManagerEntry("#minecraft:logs", "2 members", true));

    private ManagerDraft draft;
    private GroupsTab tab;

    @BeforeEach
    void readReplies() {
        draft = new ManagerDraft();
        draft.read(MODEL);
        tab = new GroupsTab(draft);
        tab.readDescribed(DESCRIBED);
    }

    private static JsonObject parsed(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Nested
    class Listing {

        /**
         * Each group lists with its members and how many of them the inventory lacks.
         */
        @Test
        void groupRowsHoldTheirMembers() {
            assertEquals(List.of(new GroupsTab.GroupRow("spawn_eggs",
                            List.of("minecraft:zombie_spawn_egg", "minecraft:gone_spawn_egg"), 1),
                    new GroupsTab.GroupRow("logs", List.of(), 0)), tab.groupRows());
        }

        /**
         * A group's row draws its #name and then each member as its item icon, no header and no count
         * (decision groups-list-members-as-icons-without-headers-or-count).
         */
        @Test
        void groupRowRunsAreItsNameAndItsMemberIcons() {
            List<ExpressionLines.Run> runs = GroupsTab.rowRuns(tab.groupRows().getFirst(),
                    id -> id.endsWith("zombie_spawn_egg") ? "Zombie Spawn Egg" : "Gone");
            assertEquals(List.of(new ExpressionLines.Run(ExpressionLines.Kind.TEXT, "#spawn_eggs", null),
                    new ExpressionLines.Run(ExpressionLines.Kind.ITEM, "Zombie Spawn Egg", "minecraft:zombie_spawn_egg"),
                    new ExpressionLines.Run(ExpressionLines.Kind.ITEM, "Gone", "minecraft:gone_spawn_egg")), runs);
        }

        /**
         * The selected group's members read their inventory name, whether the inventory holds them, and their
         * tweak as count and trait.
         */
        @Test
        void memberRowsReadNameKnownAndTweak() {
            tab.selectGroup("spawn_eggs");
            List<GroupsTab.MemberRow> members = tab.memberRows(INVENTORY);
            assertEquals(new GroupsTab.MemberRow("minecraft:zombie_spawn_egg", "Zombie Spawn Egg", true,
                    "2 × +$zombie_trait"), members.get(0));
            assertEquals(new GroupsTab.MemberRow("minecraft:gone_spawn_egg", "minecraft:gone_spawn_egg", false,
                    "not in the inventory"), members.get(1));
        }

        /**
         * A member's line opens prefilled from its tweak: its count, its trait's name and the extra its trait adds.
         */
        @Test
        void memberLineReadsCountTraitAndExtra() {
            tab.selectGroup("spawn_eggs");
            assertEquals(new GroupsTab.MemberLine(2, "zombie_trait", "{\"hex\": 1}"),
                    tab.memberLine("minecraft:zombie_spawn_egg"));
            assertEquals(new GroupsTab.MemberLine(1, "gone_trait", ""), tab.memberLine("minecraft:gone_spawn_egg"));
        }
    }

    /**
     * New group names a group, Add member picks an item or #tag into it, and the builder writes the group's value
     * and a member's line (decision group-controls-name-add-and-build).
     */
    @Nested
    class Controls {
        @Test
        void newGroupPostsItsNameToCreate() {
            JsonObject request = parsed(tab.createRequest("fresh"));
            assertEquals("fresh", request.get("group").getAsString());
            assertTrue(request.get("create").getAsBoolean());
            assertTrue(request.has("changed"));
        }

        @Test
        void addMemberPostsThePickedTag() {
            tab.selectGroup("logs");
            SearchDropdown dropdown = SearchDropdown.overMembers(INVENTORY);
            dropdown.search("logs");
            SearchDropdown.Choice tag = dropdown.choices().getFirst();
            assertEquals("#minecraft:logs", tag.id());
            JsonObject request = parsed(tab.addPickRequest(tag.id()));
            assertEquals(List.of("#minecraft:logs"),
                    request.getAsJsonArray("picks").asList().stream().map(pick -> pick.getAsString()).toList());
        }

        @Test
        void builtValueWritesTheGroupsLine() {
            tab.selectGroup("spawn_eggs");
            ExpressionBuilder builder = ExpressionBuilder.of(tab.selected().orElseThrow().base());
            builder.relate(new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "trait", "trait"));
            assertEquals("$spawn_egg + $trait", parsed(tab.baseRequest(builder.text())).get("base").getAsString());
        }

        @Test
        void builtMemberLinePostsCountTraitAndExtra() {
            tab.selectGroup("spawn_eggs");
            GroupsTab.MemberLine line = tab.memberLine("minecraft:zombie_spawn_egg");
            ExpressionBuilder extra = ExpressionBuilder.of(line.extra());
            extra.setRowNumber(0, "3");
            JsonObject tweak = parsed(tab.tweakRequest("minecraft:zombie_spawn_egg", 2, line.trait(),
                    extra.text())).getAsJsonObject("tweak");
            assertEquals(2, tweak.get("count").getAsInt());
            assertEquals("zombie_trait", tweak.get("trait").getAsString());
            assertEquals("{\"hex\": \"3\"}", tweak.get("traitText").getAsString());
        }
    }

    /**
     * A described row of kind tag reads as a tag referent, listed beside the custom groups, and its built value
     * posts as its base, which the server writes as its #tag entry (decision
     * tag-referent-lists-the-games-members-and-values-them).
     */
    @Nested
    class TagReferents {
        private static final String WITH_REFERENT = """
                {"groups": [
                  {"name": "logs", "kind": "group", "members": [], "base": null, "tweaks": [], "preview": []},
                  {"name": "saplings", "kind": "tag", "members": [{"id": "minecraft:oak_sapling", "known": true}],
                   "base": "$log / 2", "tweaks": [], "preview": []}]}
                """;

        @Test
        void tagRowReadsAsAReferentBesideTheGroups() {
            tab.readDescribed(WITH_REFERENT);
            assertEquals(List.of("logs", "saplings"), tab.groupRows().stream().map(GroupsTab.GroupRow::name).toList());
            tab.selectGroup("saplings");
            assertTrue(tab.selected().orElseThrow().tag());
            tab.selectGroup("logs");
            assertFalse(tab.selected().orElseThrow().tag());
        }

        /**
         * Add tag lists every inventory tag no referent names, each as its #id and member count, and the typed
         * search filters it (decision add-tag-picks-a-game-tag-and-writes-its-entry).
         */
        @Test
        void addTagListsTheInventorysTagsNoReferentNames() {
            tab.readDescribed(WITH_REFERENT);
            List<ValuesTab.ManagerEntry> inventory = List.of(
                    new ValuesTab.ManagerEntry("minecraft:oak_log", "Oak Log", false),
                    new ValuesTab.ManagerEntry("#minecraft:saplings", "9 members", true),
                    new ValuesTab.ManagerEntry("#minecraft:leaves", "8 members", true),
                    new ValuesTab.ManagerEntry("#minecraft:planks", "40 members", true));
            SearchDropdown dropdown = tab.addTagChoices(inventory);
            assertEquals(List.of(new SearchDropdown.Choice(SearchDropdown.Kind.TAG, "#minecraft:leaves",
                            "#minecraft:leaves, 8 members"),
                    new SearchDropdown.Choice(SearchDropdown.Kind.TAG, "#minecraft:planks",
                            "#minecraft:planks, 40 members")),
                    dropdown.choices());
            dropdown.search("lea");
            assertEquals(List.of("#minecraft:leaves"), dropdown.choices().stream().map(SearchDropdown.Choice::id)
                    .toList());
        }

        /**
         * Picking a tag posts a create-tag request naming the tag as the file spells it after #.
         */
        @Test
        void pickedTagPostsItsCreateTagRequest() {
            JsonObject request = parsed(tab.createTagRequest("#minecraft:leaves"));
            assertEquals("leaves", request.get("group").getAsString());
            assertTrue(request.get("createTag").getAsBoolean());
            assertEquals("create:tag", parsed(tab.createTagRequest("#create:tag")).get("group").getAsString());
        }

        @Test
        void referentsBuiltValuePostsAsItsBase() {
            tab.readDescribed(WITH_REFERENT);
            tab.selectGroup("saplings");
            ExpressionBuilder builder = ExpressionBuilder.of(tab.selected().orElseThrow().base());
            builder.relate(new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "wood", "wood"));
            JsonObject request = parsed(tab.baseRequest(builder.text()));
            assertEquals("saplings", request.get("group").getAsString());
            assertEquals("$log / 2 + $wood", request.get("base").getAsString());
            assertFalse(request.has("picks"));
        }
    }

    @Nested
    class Apply {

        /**
         * Adding a pick posts the draft's change set, the selected group and its members with the pick appended.
         */
        @Test
        void addPickPostsTheGroupsMembersWithThePick() {
            tab.selectGroup("spawn_eggs");
            JsonObject request = parsed(tab.addPickRequest("#minecraft:logs"));
            assertEquals("spawn_eggs", request.get("group").getAsString());
            assertEquals(List.of("minecraft:zombie_spawn_egg", "minecraft:gone_spawn_egg", "#minecraft:logs"),
                    request.getAsJsonArray("picks").asList().stream().map(pick -> pick.getAsString()).toList());
            assertTrue(request.has("changed"));
        }

        /**
         * Removing a member posts the group's other members as its picks.
         */
        @Test
        void removeMemberPostsTheOtherMembers() {
            tab.selectGroup("spawn_eggs");
            JsonObject request = parsed(tab.removeMemberRequest("minecraft:gone_spawn_egg"));
            assertEquals(List.of("minecraft:zombie_spawn_egg"),
                    request.getAsJsonArray("picks").asList().stream().map(pick -> pick.getAsString()).toList());
        }

        /**
         * A tweak posts the member, its count and its trait; an empty trait value posts no trait.
         */
        @Test
        void tweakPostsCountAndTrait() {
            tab.selectGroup("spawn_eggs");
            JsonObject tweak = parsed(tab.tweakRequest("minecraft:zombie_spawn_egg", 3, "zombie_trait",
                    "{\"hex\": 2}")).getAsJsonObject("tweak");
            assertEquals(3, tweak.get("count").getAsInt());
            assertEquals("zombie_trait", tweak.get("trait").getAsString());
            JsonObject plain = parsed(tab.tweakRequest("minecraft:zombie_spawn_egg", 1, "zombie_trait", " "))
                    .getAsJsonObject("tweak");
            assertTrue(plain.get("trait").isJsonNull());
            assertTrue(plain.get("traitText").isJsonNull());
        }

        /**
         * A base posts the group's base line.
         */
        @Test
        void basePostsTheBaseLine() {
            tab.selectGroup("logs");
            assertEquals("$log", parsed(tab.baseRequest("$log")).get("base").getAsString());
        }

        /**
         * The apply reply's model becomes the draft and its groups replace the listing.
         */
        @Test
        void appliedReplyReplacesDraftAndGroups() {
            tab.readApplied(APPLIED);
            assertEquals(List.of(new GroupsTab.GroupRow("logs", List.of("minecraft:oak_log"), 0)), tab.groupRows());
            assertEquals(2, parsed(draft.text()).getAsJsonArray("members").size());
            assertFalse(tab.selected().isPresent());
        }
    }
}
