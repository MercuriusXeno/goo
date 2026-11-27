package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.Direction;

/**
 * The two parts set on a metronome's plate, each right-clicked where it sits: the gap switch on the back half, toward
 * the input, and the button on the front half, toward the output.
 * decision metronome-prism-pulses-at-the-learned-rate
 */
public enum MetronomePart {
    /** Steps the cadence through the repeater's four. */
    GAP_SWITCH,
    /** Flips the invert toggle. */
    BUTTON;

    /** The plate's center along either horizontal axis, in block units. */
    private static final double CENTER = 0.5;

    /**
     * Answers the part a click on the plate lands on.
     *
     * @param back   the plate's back, the side its input reads, as its FACING holds it
     * @param localX the click's x inside the block, from 0 to 1
     * @param localZ the click's z inside the block, from 0 to 1
     * @return the gap switch for a click on the back half, the button for one on the front half
     */
    public static MetronomePart at(Direction back, double localX, double localZ) {
        double towardBack = (localX - CENTER) * back.getStepX() + (localZ - CENTER) * back.getStepZ();
        return towardBack > 0 ? GAP_SWITCH : BUTTON;
    }
}
