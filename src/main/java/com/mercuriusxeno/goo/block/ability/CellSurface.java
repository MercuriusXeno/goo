package com.mercuriusxeno.goo.block.ability;

/**
 * What a volume cell does to the things on it, the one way two goo types'
 * cells differ besides their look and their viscosity: slime breaks a fall
 * and bounces what lands on it, ramping while jump is held; slick lets a fall
 * harm as any ground does and ramps a thing trying to move on it, slippery as
 * ice; molten stands as ordinary ground, and so does shatter under the
 * shards hanging over it, which cut what moves through them; leech slows a
 * thing caught in it. A goo type binding a world goo to the cell names one.
 * slime-and-slick-are-one-volume-cell-tech
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
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
    POOL(Frictions.ORDINARY_GROUND),
    /** Shatter's: ordinary ground under the glass shards hanging over it, which cut what moves through them. */
    SHARDS(Frictions.ORDINARY_GROUND),
    /** Leech's: ordinary ground underfoot, it slows a thing caught in it. */
    SLOW(Frictions.ORDINARY_GROUND);

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

    /**
     * @return true where the shards over the cell cut a thing moving through them
     */
    public boolean cuts() {
        return this == SHARDS;
    }

    /**
     * Whether the cell takes the step of a thing standing on it at any
     * height, a thin cell's top lying above the depth vanilla reads a step at.
     * slick-coats-the-ground-with-kinetic-speed
     * each-shatter-cut-has-a-chance-to-deplete-a-layer
     *
     * @return true where the cell ramps or cuts what stands on it
     */
    public boolean takesTheStepAtAnyHeight() {
        return ramps() || cuts();
    }

    /**
     * @return true where the cell slows a thing caught in it
     */
    public boolean slows() {
        return this == SLOW;
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
