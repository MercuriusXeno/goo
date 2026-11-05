package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
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
    private final ProcessesTab processes;

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
        ManagerDraft conversions = new ManagerDraft();
        conversions.read("""
                {"path": "base_values.json", "members": [
                  {"key": "_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "#logs", "keySeparator": null, "kind": "VALUE", "text": "\\"#stones @waxed @cooked\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "waxed", "keySeparator": null, "kind": "VALUE", "text": "\\"+$waxed\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "cooked", "keySeparator": null, "kind": "VALUE", "text": "\\"glow / 1 -> zeal / 2\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "oxidation_metal", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"metal / 4 -> yore / 9\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "oxidation_pulse", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"pulse / 4 -> yore / 9\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "exposed_metal", "keySeparator": null, "kind": "VALUE", "text": "\\"@oxidation_metal\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "exposed_pulse", "keySeparator": null, "kind": "VALUE", "text": "\\"@oxidation_pulse\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "#exposed", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"#copper @exposed_metal @exposed_pulse\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "#waxed_exposed", "keySeparator": null, "kind": "VALUE", "text": "\\"#exposed @waxed\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        processes = new ProcessesTab(conversions);
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
        return GroupHovers.tooltipFor(groups, values, processes, GroupHovers.hoverKey(group)).orElseThrow();
    }

    private List<TooltipLine> iteratorCard(String iterator) {
        return GroupHovers.tooltipFor(groups, values, processes, GroupHovers.iteratorHoverKey(iterator)).orElseThrow();
    }

    /**
     * An iterator's card reads the group it fills, its members and value, then what the iterator does: the group it
     * copies from, then each mutator it applies in the order its row reads them, an additive marked + and a formula
     * marked * (decisions iterator-hover-adds-its-ratio-and-mutators, group-hover-reads-source-arrow-then-plus-
     * additives-and-star-multipliers).
     */
    @Test
    void iteratorCardEndsOnItsSourceAndMarkedMutators() {
        List<TooltipLine> card = iteratorCard("#logs");
        assertEquals(hover("logs"), card.subList(0, card.size() - 1));
        assertEquals(new TooltipLine.Text(Component.literal("#stones -> +waxed, *cooked")), card.getLast());
    }

    @Test
    void iteratorApplyingAnAdditiveMarksItPlus() {
        assertEquals(new TooltipLine.Text(Component.literal("#exposed -> +waxed")),
                iteratorCard("#waxed_exposed").getLast());
    }

    @Test
    void iteratorApplyingStacksMarksEachStarByItsMutatorsName() {
        assertEquals(new TooltipLine.Text(Component.literal("#copper -> *exposed_metal, *exposed_pulse")),
                iteratorCard("#exposed").getLast());
    }

    @Test
    void valuedGroupReadsItsMemberSpritesThenItsValue() {
        assertEquals(List.of(new TooltipLine.Members(List.of("minecraft:oak_log"), 0),
                new TooltipLine.GooAmount("glow", "37")), hover("logs"));
    }

    /**
     * A group's value lines read the highest amount first, ties by type key, whatever order the member's resolved
     * value held (decision value-lists-order-highest-first-ties-by-type-key).
     */
    @Test
    void valuedGroupListsItsAmountsHighestFirstTiesByTypeKey() {
        values.readResolved("""
                {"entries": [{"key": "oak_log", "value": {"glow": "1/2", "zeal": "2", "metal": "2"},
                              "denied": false}]}
                """);
        assertEquals(List.of(new TooltipLine.Members(List.of("minecraft:oak_log"), 0),
                new TooltipLine.GooAmount("metal", "2"), new TooltipLine.GooAmount("zeal", "2"),
                new TooltipLine.GooAmount("glow", "1/2")), hover("logs"));
    }

    @Test
    void longMemberListCutsAtEightSpritesThenPlusNAndAnUnvaluedGroupReadsItsMembersAlone() {
        assertEquals(List.of(new TooltipLine.Members(List.of("minecraft:stone", "minecraft:granite",
                "minecraft:diorite", "minecraft:andesite", "minecraft:cobblestone", "minecraft:deepslate",
                "minecraft:tuff", "minecraft:calcite"), 2)), hover("stones"));
    }
}
