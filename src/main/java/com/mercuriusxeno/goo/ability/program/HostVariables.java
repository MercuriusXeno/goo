package com.mercuriusxeno.goo.ability.program;

/**
 * The names a host binds for expressions, spelled once so the hosts that
 * answer them and the {@link HostKind} that promises them agree.
 */
public final class HostVariables {

    /**
     * The live stack count on a marker.
     */
    public static final String STACKS = "stacks";
    /**
     * The marker's stack ceiling.
     */
    public static final String MAX_STACKS = "max_stacks";
    /**
     * One for a flat blob, zero otherwise.
     */
    public static final String FLAT = "flat";
    /**
     * The struck entity's current health.
     */
    public static final String HEALTH = "health";
    /**
     * The struck entity's maximum health.
     */
    public static final String MAX_HEALTH = "max_health";
    /**
     * The struck entity's distance from the thrower, zero with no thrower.
     */
    public static final String DISTANCE = "distance";
    /**
     * One when the struck entity heals from harm and is harmed by healing,
     * zero otherwise.
     */
    public static final String UNDEAD = "undead";
    /**
     * One when the target is a sprinting player, zero otherwise.
     */
    public static final String SPRINTING = "sprinting";

    /**
     * The separator of a namespaced id, which marks a variable name as a
     * counter read rather than a host variable.
     */
    private static final char COUNTER_SEPARATOR = ':';

    private HostVariables() {
    }

    /**
     * Tells whether an expression's variable name reads a counter the
     * target keeps: a counter is named by its namespaced id, such as
     * {@code goo:ritual} (decision aeon-mob-ritual-drops-spawn-egg).
     *
     * @param name the variable name
     * @return true for a counter id
     */
    public static boolean isCounter(String name) {
        return name.indexOf(COUNTER_SEPARATOR) >= 0;
    }
}
