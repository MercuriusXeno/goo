package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests what a #group link or an iterator link hovers: the group's members as sprites on one line, cut at eight
 * with +n for the rest, then its value's amounts where it has one (decisions group-referent-previews-shorthand-members-then-its-
 * value, group-link-hovers-member-icons-then-plus-n).
 */
class GroupHoversTest {
    private final ValuesTab values;
    private final GroupsTab groups;

    GroupHoversTest() {
        ManagerDraft draft = new ManagerDraft();
        values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:oak_log", "Oak Log")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "logs", "keySeparator": null, "kind": "VALUE", "text": "[\\"oak_log\\"]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 37}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_log", "value": {"glow": "37"}, "denied": false}]}
                """);
        groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [
                  {"name": "logs", "kind": "group", "members": [{"id": "minecraft:oak_log", "known": true}],
                   "base": "{\\"glow\\": 37}", "tweaks": [], "preview": []},
                  {"name": "stones", "kind": "group", "members": [{"id": "minecraft:stone", "known": true},
                     {"id": "minecraft:granite", "known": true}, {"id": "minecraft:diorite", "known": true},
                     {"id": "minecraft:andesite", "known": true}, {"id": "minecraft:cobblestone", "known": true},
                     {"id": "minecraft:deepslate", "known": true}, {"id": "minecraft:tuff", "known": true},
                     {"id": "minecraft:calcite", "known": true}, {"id": "minecraft:basalt", "known": true},
                     {"id": "minecraft:blackstone", "known": true}],
                   "base": null, "tweaks": [], "preview": []}]}
                """);
    }

    private List<TooltipLine> hover(String group) {
        return GroupHovers.tooltipFor(groups, values, GroupHovers.hoverKey(group)).orElseThrow();
    }

    @Test
    void valuedGroupReadsItsMemberSpritesThenItsValue() {
        assertEquals(List.of(new TooltipLine.Members(List.of("minecraft:oak_log"), 0),
                new TooltipLine.GooAmount("glow", "37")), hover("logs"));
    }

    @Test
    void longMemberListCutsAtEightSpritesThenPlusNAndAnUnvaluedGroupReadsItsMembersAlone() {
        assertEquals(List.of(new TooltipLine.Members(List.of("minecraft:stone", "minecraft:granite",
                "minecraft:diorite", "minecraft:andesite", "minecraft:cobblestone", "minecraft:deepslate",
                "minecraft:tuff", "minecraft:calcite"), 2)), hover("stones"));
    }
}
