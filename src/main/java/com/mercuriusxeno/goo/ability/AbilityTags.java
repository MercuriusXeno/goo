package com.mercuriusxeno.goo.ability;

/**
 * Tags an ability definition carries that code reads, by the name the
 * ability JSON spells.
 */
public final class AbilityTags {

    /**
     * An ability the glove throws at an entity rather than a block.
     */
    public static final String ENTITY = "entity";

    /**
     * An ability a tap's drip runs where it lands; the glove never offers
     * one (decision tap-ability-tagged-program).
     */
    public static final String TAP = "tap";

    private AbilityTags() {
    }
}
