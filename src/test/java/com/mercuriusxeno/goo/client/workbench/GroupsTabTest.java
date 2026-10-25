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
         * Each group lists as #name with its member count and how many of its members the inventory lacks.
         */
        @Test
        void groupRowsCountMembersAndUnknowns() {
            assertEquals(List.of(new GroupsTab.GroupRow("spawn_eggs", 2, 1), new GroupsTab.GroupRow("logs", 0, 0)),
                    tab.groupRows());
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
         * The picker offers the inventory's items and #tags whose id holds the typed text, ignoring case.
         */
        @Test
        void pickerOffersItemsAndTagsMatchingTheText() {
            assertEquals(List.of("minecraft:zombie_spawn_egg", "minecraft:oak_log", "#minecraft:logs"),
                    tab.pickerChoices("", INVENTORY));
            assertEquals(List.of("minecraft:oak_log", "#minecraft:logs"), tab.pickerChoices("LOG", INVENTORY));
        }
    }

    @Nested
    class Apply {

        /**
         * Adding a pick posts the draft's members, the selected group and its members with the pick appended.
         */
        @Test
        void addPickPostsTheGroupsMembersWithThePick() {
            tab.selectGroup("spawn_eggs");
            JsonObject request = parsed(tab.addPickRequest("#minecraft:logs"));
            assertEquals("spawn_eggs", request.get("group").getAsString());
            assertEquals(List.of("minecraft:zombie_spawn_egg", "minecraft:gone_spawn_egg", "#minecraft:logs"),
                    request.getAsJsonArray("picks").asList().stream().map(pick -> pick.getAsString()).toList());
            assertTrue(request.has("members"));
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
            assertEquals(List.of(new GroupsTab.GroupRow("logs", 1, 0)), tab.groupRows());
            assertEquals(2, parsed(draft.text()).getAsJsonArray("members").size());
            assertFalse(tab.selected().isPresent());
        }
    }
}
