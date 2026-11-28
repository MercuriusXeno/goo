package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.Direction;

/**
 * The two parts set on a metronome's plate, each right-clicked where it sits: the button on the back half, toward the
 * input, and the gap switch on the front half, toward the output (operator ruling 2026-11-28).
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
     * Where the button's half starts toward the back, in blocks from the center: a pixel past it, between the switch
     * torch's back face on the center and the button's front face two pixels behind it, so a click on the torch's
     * back face presses no button. The button stands at the input end, the back (operator rulings 2026-11-28).
     */
    private static final double BUTTON_HALF_STARTS = 1.0 / 16;

    /**
     * Answers the part a click on the plate lands on.
     *
     * @param back   the plate's back, the side its input reads, as its FACING holds it
     * @param localX the click's x inside the block, from 0 to 1
     * @param localZ the click's z inside the block, from 0 to 1
     * @return the button for a click on the back half, the gap switch for one on the front half
     */
    public static MetronomePart at(Direction back, double localX, double localZ) {
        double towardBack = (localX - CENTER) * back.getStepX() + (localZ - CENTER) * back.getStepZ();
        return towardBack > BUTTON_HALF_STARTS ? BUTTON : GAP_SWITCH;
    }
}
