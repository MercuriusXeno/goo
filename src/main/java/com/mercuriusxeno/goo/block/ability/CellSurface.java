package com.mercuriusxeno.goo.block.ability;

/**
 * What a volume cell does to the things on it, the one way goo types'
 * cells differ besides their look and their viscosity: slime breaks a fall
 * and bounces what lands on it, ramping while jump is held; slick lets a fall
 * harm as any ground does and ramps a thing trying to move on it, slippery as
 * ice; molten stands as ordinary ground, and so does shatter under the
 * shards hanging over it, which cut what moves through them; rime is as
 * slippery as ice and pushes nothing; wisps stands as ordinary ground and
 * smites the undead touching it; leech slows a thing caught in it; spikes
 * stands as ordinary ground and impales what walks on it; vines stands as
 * ordinary ground and entangles what crosses it; phase stands as ordinary
 * ground and puts what stands in it out of phase; stitch stands as ordinary
 * ground, lets a passive pass and tangles a hostile in it; gloom stands as
 * ordinary ground and reads dark to the mobs spawning on it; Dowsing is
 * ordinary ground. A goo type binding a world goo to the cell names one.
 * slime-and-slick-are-one-volume-cell-tech
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * bounce-ramps-jump-height-while-jump-is-held
 * slick-coats-the-ground-with-kinetic-speed
 * rime-goo-is-slippery-to-walk-on
 * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * gloom-spawns-regardless-of-light-and-mobs-on-it-ignore-daylight
 *
 * @see VolumeCellBlock
 */
public enum CellSurface {

    /** Slime's: a fall onto it does no harm, and what lands on it bounces. */
    BOUNCE(Frictions.ORDINARY_GROUND),
    /** Slick's: as slippery as ice, it ramps a thing trying to move on it. */
    RAMP(Frictions.ICE),
    /** Molten's and Creep's: ordinary ground, letting a fall harm and moving nothing on it. */
    POOL(Frictions.ORDINARY_GROUND),
    /** Shatter's: ordinary ground under the glass shards hanging over it, which cut what moves through them. */
    SHARDS(Frictions.ORDINARY_GROUND),
    /** Rime's: as slippery as ice, moving nothing on it, so a thing slides as on ice with none of slick's push. */
    GLAZE(Frictions.ICE),
    /** Wisps': ordinary ground that sets the undead touching it on fire and deals them holy damage. */
    SMITE(Frictions.ORDINARY_GROUND),
    /** Leech's: ordinary ground underfoot, it slows a thing caught in it. */
    SLOW(Frictions.ORDINARY_GROUND),
    /** Spikes': ordinary ground that forms spikes to impale a thing walking on it. */
    IMPALE(Frictions.ORDINARY_GROUND),
    /** Vines': ordinary ground underfoot, it entangles a thing crossing it in vines by its layers. */
    ENTANGLE(Frictions.ORDINARY_GROUND),
    /** Phase's: ordinary ground underfoot, it puts what stands in it out of phase. */
    PHASE(Frictions.ORDINARY_GROUND),
    /**
     * Stitch's: ordinary ground underfoot, a passive passes it and a hostile in it is tangled in strands.
     * passives-move-through-stitch-and-hostiles-tangle-in-it
     */
    TANGLE(Frictions.ORDINARY_GROUND),
    /** Gloom's: ordinary ground underfoot, it reads dark to a hostile mob spawning on it, whatever the light. */
    DARKEN(Frictions.ORDINARY_GROUND),
    /**
     * Dowsing's: ordinary ground underfoot that neither bounces nor ramps;
     * the cell works on the eye looking through it, not on what stands on it.
     * astral-world-goo-is-dowsing-an-x-ray-of-ore
     */
    LENS(Frictions.ORDINARY_GROUND);

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
     * Whether the cell, rather than the block under it, takes the step and
     * the ground friction of a thing standing on it at any height, a thin
     * cell's top lying above the depth vanilla reads them at: slick's ramp,
     * shatter's cut, rime's slide and cold, and spikes' strike reach what
     * stands on it.
     * slick-coats-the-ground-with-kinetic-speed
     * each-shatter-cut-has-a-chance-to-deplete-a-layer
     * rime-goo-is-slippery-to-walk-on
     * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
     *
     * @return true where the cell ramps, cuts, glazes or impales what stands on it
     */
    public boolean takesTheStepAtAnyHeight() {
        return ramps() || cuts() || this == GLAZE || this == IMPALE;
    }

    /**
     * @return true where the cell burns the undead touching it and deals them holy damage
     */
    public boolean smites() {
        return this == SMITE;
    }

    /**
     * @return true where the cell slows a thing caught in it
     */
    public boolean slows() {
        return this == SLOW;
    }

    /**
     * @return true where the cell entangles a thing crossing it
     */
    public boolean entangles() {
        return this == ENTANGLE;
    }

    /**
     * @return true where the cell puts what stands in it out of phase
     */
    public boolean phases() {
        return this == PHASE;
    }

    /**
     * @return true where the cell tangles a hostile in it and lets a passive pass
     */
    public boolean tangles() {
        return this == TANGLE;
    }

    /**
     * @return true where the cell reads dark to a hostile mob spawning on it
     */
    public boolean darkens() {
        return this == DARKEN;
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
