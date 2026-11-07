package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests each tab's explainer text against the paragraph the operator wrote for it in the idea
 * values-manager-explainer-mode-anchored-tooltips-and-numbered-member-rails, verbatim (decision
 * explainer-lines-are-the-operators-tab-descriptions).
 */
class ExplainerTextsTest {

    @Test
    void itemsTextIsTheOperatorsItemsParagraph() {
        assertEquals("Search for items, view their explicit or derived values, and recipe ratios to help make decisions"
                + " on values. This is where to look for unvalued items, and where to set explicit values to `anchor"
                + " items`. `Anchor items` cascade their values to other things: e.g. iron nugget value x9 => iron"
                + " ingot (so you only have to define either and not both). You can use the filters to search for"
                + " things missing a value, as well as review fractional values, if you want to tune fractional"
                + " values.", ExplainerTexts.ITEMS);
    }

    @Test
    void groupsTextIsTheOperatorsGroupsParagraph() {
        assertEquals("Establish custom sets of items, or refer to an existing #tag (you can use forge and vanilla tags"
                + " to equate things). This can be used to give all the items in the set the same value, and it can"
                + " also be used to create parallel sets of things that relate congrously with another set, e.g."
                + " #copper and #waxed_copper. #copper defines all the copper blocks that can be `$waxed`."
                + " #waxed_copper defines all the items after being `$waxed`. See `Conversions` for more"
                + " information.", ExplainerTexts.GROUPS);
    }

    @Test
    void prefabsTextIsTheOperatorsPrefabsParagraph() {
        assertEquals("Create consistent static or dynamic values or expressions. A prefab can be a scalar (a constant"
                + " value, e.g. $base = 6) or an object (leaf = $base) assigning a value to a specific goo type"
                + " consistently. Prefabs can also refer to other items, and other prefabs, allowing you to create"
                + " relationships that are predefined, e.g. $waxed = minecraft:honeycomb.weird (this gives $waxed the"
                + " weird goo from honeycomb). Prefabs can be used in value expressions and conversions and make it"
                + " easier to establish value ratios relative to other things.", ExplainerTexts.PREFABS);
    }

    @Test
    void conversionsTextIsTheOperatorsConversionsParagraph() {
        assertEquals("Conversions describe processes that are consistent, such as waxing copper blocks, or copper"
                + " blocks oxidizing, or stripping logs. The defaults define those processes as having some change to"
                + " their goo values that is relative to their original goo values (in some cases by scaling:"
                + " oxidizing copper turns some metal and pulse into yore at a loss). It can also be a way to"
                + " `iterate` over one #group of items and copy their values onto another #group. e.g. #copper copies"
                + " onto #waxed_copper, mutated by adding $waxed to each entry. `Note: the groups must be"
                + " parallel/congruent. Note that order matters!`", ExplainerTexts.CONVERSIONS);
    }

    @Test
    void auditTextIsTheOperatorsAuditParagraph() {
        assertEquals("The mod packages some value-validation rules that were created to help define the defaults for"
                + " the vanilla minecraft taxonomy. They were included in this workbench in case modpack authors and"
                + " server ops find them useful too. You can run an audit to gather information about your current"
                + " value set and tell you what is \"wrong\" with it. In some cases they are just warnings. There's a"
                + " lot of flexibility in the system, so some rules are okay to bend. Goo isn't like EMC. A feedback"
                + " loop through conversions may be desirable to you, depending on what you're trying to do. Treat"
                + " what the audit reports as informational, not a passing/failing grade. Use it to make decisions"
                + " about your values; you can't \"fail\" an audit.", ExplainerTexts.AUDIT);
    }

    /**
     * The Abilities tab's line is the operator's words from the idea abilities-tunable-from-the-workbench-priced-off-items.
     */
    @Test
    void abilitiesTextIsTheOperatorsAbilitiesIdea() {
        assertEquals("I want the abilities to be tunable from the workbench, because I want to base costs on items."
                + " Every numeric param of every ability shows as an expression box; a cost is built like a by-type"
                + " value that can only accept one type, the ability's own, so an item in it contributes its amount of"
                + " that type. Save writes a draft per edited ability file, Promote renames the drafts over the live"
                + " goo_abilities files in the world pack, and a dev client targets the source goo_abilities folder,"
                + " as base_values does.", ExplainerTexts.forTab(ManagerTab.ABILITIES));
    }
}
