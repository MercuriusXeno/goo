package com.mercuriusxeno.goo.ability.reap;

/**
 * The kinds of plant Reap harvests, and when each is ripe: a crop at its
 * full age, a sweet berry bush bearing berries from age two, a cave vine
 * bearing glow berries, and cocoa at its full age. Nether wart is no kind of
 * Reap's: it is a fungus, shroom's to grow and reap.
 * reap-breeze-harvests-and-replants
 */
public enum RipeKind {
    /** A crop, ripe at its full age, replanted at age zero with its seed kept. */
    CROP,
    /** A sweet berry bush, bearing berries from age two, picked back to age one. */
    SWEET_BERRIES,
    /** A cave vine bearing glow berries, picked bare. */
    GLOW_BERRIES,
    /** A cocoa pod, ripe at its full age, replanted at age zero with a bean kept. */
    COCOA;

    /** The age a sweet berry bush first bears berries at. */
    static final int BERRIES_FROM_AGE = 2;

    /**
     * Whether a plant of this kind is ripe.
     *
     * @param age     its age, or zero for a kind without one
     * @param maxAge  the age it is full grown at
     * @param berries whether it bears berries, for a cave vine
     * @return true when Reap harvests it
     */
    public boolean ripe(int age, int maxAge, boolean berries) {
        return switch (this) {
            case CROP, COCOA -> age >= maxAge;
            case SWEET_BERRIES -> age >= BERRIES_FROM_AGE;
            case GLOW_BERRIES -> berries;
        };
    }
}
