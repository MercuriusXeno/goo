package com.mercuriusxeno.goo.client.ability;

/**
 * Sculker's close-range field, where blocks read as glass: within a few
 * blocks of the player every block draws its own texture, mostly solid, at
 * full strength over most of the field and easing to nothing at its edge, past which the remembered echoes alone show. An ore a block
 * or two into the wall reads by its texture, and so does the block the
 * player is inside.
 * decision sculker-shows-blocks-translucent-close-up
 */
public final class SculkerGlass {

    /** How far from the player the field reaches, in blocks. */
    public static final double FIELD_RADIUS = 5;
    /** The share of the radius the field holds full strength over before it eases to nothing at its edge. */
    private static final double FULL_SHARE = 0.7;
    /** The smoothstep's cubic terms, 3s² - 2s³, easing the fall in and out. */
    private static final double SMOOTH_SQUARE = 3;
    private static final double SMOOTH_CUBE = 2;

    private SculkerGlass() {
    }

    /**
     * How strongly a block's own texture shows at a distance from the player.
     *
     * @param distance how far the block's center stands from the player's eyes, in blocks
     * @return 1 at the player, falling across the field to 0 at its radius and past it
     */
    public static float strengthAt(double distance) {
        double share = Math.clamp((distance / FIELD_RADIUS - FULL_SHARE) / (1 - FULL_SHARE), 0, 1);
        double eased = share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
        return (float) (1 - eased);
    }
}
