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
     * program drawing the hit instead, as Frost's Orb does.
     */
    public static final String NO_SPLAT = "no_splat";

    /**
     * An ability that acts on its caster; a stream tagged so runs its
     * program on the caster each tick it is held, beside every living
     * thing in its cone.
     * vitality-waves-regenerate-and-court
     */
    public static final String SELF = "self";

    /**
     * A stream that works on a mob or on blocks, never both at once: with a
     * mob in its cone it strikes the nearest one alone and leaves the blocks,
     * and with none it runs its block pass
     * (decision decay-gnats-degrade-each-block-once).
     */
    public static final String MOB_FIRST = "mob_first";

    /**
     * A world ability sized at will: pressing pins its epicenter, dragging
     * sets its radius, and releasing opens it at once, its program reading
     * the radius as {@code size}; it is never thrown
     * (decision black-hole-leaves-a-compression-sphere).
     */
    public static final String DRAG_SIZED = "drag_sized";

    /**
     * A world ability cast by its footprint: holding right click pins a
     * corner, dragging sizes the footprint, releasing fixes it, the pitch sets
     * its rise and a second right click submits; it is never thrown
     * (decision spire-rips-walls-and-platforms).
     */
    public static final String FOOTPRINT = "footprint";

    private AbilityTags() {
    }
}
