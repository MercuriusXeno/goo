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

    private HostVariables() {
    }
}
