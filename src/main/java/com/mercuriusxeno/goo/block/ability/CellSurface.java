package com.mercuriusxeno.goo.block.ability;

/**
 * What a volume cell does to the things on it, the one way two goo types'
 * cells differ besides their look and their viscosity: slime breaks a fall
 * and bounces what lands on it, ramping while jump is held; slick lets a fall
 * harm as any ground does and ramps a thing trying to move on it, slippery as
 * ice; molten stands as ordinary ground. A goo type binding a world goo to
 * the cell names one.
 * slime-and-slick-are-one-volume-cell-tech
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * bounce-ramps-jump-height-while-jump-is-held
 * slick-coats-the-ground-with-kinetic-speed
 *
 * @see VolumeCellBlock
 */
public enum CellSurface {

    /** Slime's: a fall onto it does no harm, and what lands on it bounces. */
    BOUNCE(Frictions.ORDINARY_GROUND),
    /** Slick's: as slippery as ice, it ramps a thing trying to move on it. */
    RAMP(Frictions.ICE),
    /** Molten's and Host's: ordinary ground, letting a fall harm and moving nothing on it. */
    POOL(Frictions.ORDINARY_GROUND);

    /** The friction a cell bound to this surface stands at, the slipperiness what moves on it meets. */
    private final float friction;

    CellSurface(float friction) {
        this.friction = friction;
    }

    /**
     * @return the friction a cell bound to this surface stands at
     */
    public float friction() {
        return friction;
    }

    /**
     * @return true where a fall onto the cell does no harm and what lands on it bounces
     */
    public boolean bounces() {
        return this == BOUNCE;
    }

    /**
     * @return true where the cell ramps a thing trying to move on it
     */
    public boolean ramps() {
        return this == RAMP;
    }

    /** The frictions the surfaces stand at, held apart so the constants above can read them. */
    private static final class Frictions {
        /** Vanilla's friction for ordinary ground. */
        static final float ORDINARY_GROUND = 0.6F;
        /** Ice's friction, so slick is as slippery as ice. */
        static final float ICE = 0.98F;

        private Frictions() {
        }
    }
}
