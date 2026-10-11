package com.mercuriusxeno.goo.ability.program;

import java.util.OptionalDouble;

/**
 * The names a host binds for expressions, spelled once so the hosts that
 * answer them and the {@link HostKind} that promises them agree.
 */
public final class HostVariables {

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
     * The share of a hit the struck entity takes: one for a direct hit, and a
     * refracted Sunbeam's split for each mob a prism's beams reach
     * (decision sunbeam-splits-at-the-prism-with-a-glisten).
     */
    public static final String SHARE = "share";

    /**
     * The size a cast was dragged to, in blocks, which a world ability sized
     * at will reads for its radius; zero for a cast that names none
     * (decision black-hole-leaves-a-compression-sphere).
     */
    public static final String SIZE = "size";

    /**
     * The share of a full charge a charged ability's hold reached, 0 to 1,
     * read on the player releasing it (decision nova-ring-grows-with-the-hold).
     */
    public static final String CHARGE = "charge";
    /**
     * The age in ticks of the hold a ray's impact lands on, 1 on its first
     * tick (decision sunbeam-lands-with-impact-and-aim).
     */
    public static final String HELD = "held";
    /**
     * One when a ray's impact lands on a tick that hits, zero between hits
     * (decision sunbeam-lands-with-impact-and-aim).
     */
    public static final String HIT = "hit";

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

    /**
     * The variables a sized cast binds: its size, and nothing else, which a
     * client visual evaluates a marker's radius with.
     *
     * @param size the cast's size in blocks
     * @return the variables
     */
    public static Variables sized(double size) {
        return name -> SIZE.equals(name) ? OptionalDouble.of(size) : OptionalDouble.empty();
    }
}
