package com.mercuriusxeno.goo.client.workbench;

/**
 * What explainer mode says each tab is for, in the operator's words from the idea
 * values-manager-explainer-mode-anchored-tooltips-and-numbered-member-rails, verbatim.
 * Decision explainer-lines-are-the-operators-tab-descriptions.
 */
final class ExplainerTexts {
    /** The Items tab's paragraph. */
    static final String ITEMS = "Search for items, view their explicit or derived values, and recipe ratios to help"
            + " make decisions on values. This is where to look for unvalued items, and where to set explicit values"
            + " to `anchor items`. `Anchor items` cascade their values to other things: e.g. iron nugget value x9 =>"
            + " iron ingot (so you only have to define either and not both). You can use the filters to search for"
            + " things missing a value, as well as review fractional values, if you want to tune fractional values.";
    /** The Groups tab's paragraph. */
    static final String GROUPS = "Establish custom sets of items, or refer to an existing #tag (you can use forge and"
            + " vanilla tags to equate things). This can be used to give all the items in the set the same value, and"
            + " it can also be used to create parallel sets of things that relate congrously with another set, e.g."
            + " #copper and #waxed_copper. #copper defines all the copper blocks that can be `$waxed`. #waxed_copper"
            + " defines all the items after being `$waxed`. See `Conversions` for more information.";
    /** The Prefabs tab's paragraph. */
    static final String PREFABS = "Create consistent static or dynamic values or expressions. A prefab can be a"
            + " scalar (a constant value, e.g. $base = 6) or an object (leaf = $base) assigning a value to a specific"
            + " goo type consistently. Prefabs can also refer to other items, and other prefabs, allowing you to"
            + " create relationships that are predefined, e.g. $waxed = minecraft:honeycomb.weird (this gives $waxed"
            + " the weird goo from honeycomb). Prefabs can be used in value expressions and conversions and make it"
            + " easier to establish value ratios relative to other things.";
    /** The Conversions tab's paragraph. */
    static final String CONVERSIONS = "Conversions describe processes that are consistent, such as waxing copper"
            + " blocks, or copper blocks oxidizing, or stripping logs. The defaults define those processes as having"
            + " some change to their goo values that is relative to their original goo values (in some cases by"
            + " scaling: oxidizing copper turns some metal and pulse into yore at a loss). It can also be a way to"
            + " `iterate` over one #group of items and copy their values onto another #group. e.g. #copper copies"
            + " onto #waxed_copper, mutated by adding $waxed to each entry. `Note: the groups must be"
            + " parallel/congruent. Note that order matters!`";
    /** The Audit tab's paragraph. */
    static final String AUDIT = "The mod packages some value-validation rules that were created to help define the"
            + " defaults for the vanilla minecraft taxonomy. They were included in this workbench in case modpack"
            + " authors and server ops find them useful too. You can run an audit to gather information about your"
            + " current value set and tell you what is \"wrong\" with it. In some cases they are just warnings."
            + " There's a lot of flexibility in the system, so some rules are okay to bend. Goo isn't like EMC. A"
            + " feedback loop through conversions may be desirable to you, depending on what you're trying to do."
            + " Treat what the audit reports as informational, not a passing/failing grade. Use it to make decisions"
            + " about your values; you can't \"fail\" an audit.";

    private ExplainerTexts() {
    }

    /**
     * @param tab a manager tab
     * @return what explainer mode says the tab is for
     */
    static String forTab(ManagerTab tab) {
        return switch (tab) {
            case VALUES -> ITEMS;
            case GROUPS -> GROUPS;
            case VARIABLES -> PREFABS;
            case PROCESSES -> CONVERSIONS;
            case AUDIT -> AUDIT;
        };
    }
}
