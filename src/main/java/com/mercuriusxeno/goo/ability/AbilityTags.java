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

    /**
     * An ability whose blob draws no goo splat on the mob it strikes, its own
     * program drawing the hit instead, as Crush's rubble does
     * (decision crush-blob-breaks-along-its-strike).
     */
    public static final String NO_SPLAT = "no_splat";

    /**
     * An ability that acts on its caster; a stream tagged so runs its
     * program on the caster each tick it is held, beside every living
     * thing in its cone.
     * vitality-waves-regenerate-and-court
     */
    public static final String SELF = "self";

    private AbilityTags() {
    }
}
