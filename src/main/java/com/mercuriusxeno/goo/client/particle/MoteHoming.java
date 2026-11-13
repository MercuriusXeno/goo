package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;

/**
 * How a void mote streaming into Vacuum's glove steers: each tick it covers
 * an even share of what lies between it and where the glove is now, so it
 * lands in the hand as its flight ends even while the player moves.
 * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
 */
final class MoteHoming {

    private MoteHoming() {
    }

    /**
     * One tick's move toward the glove.
     *
     * @param at        where the mote is
     * @param glove     where the glove is this tick
     * @param ticksLeft the ticks of flight the mote has left, this one counted
     * @return the move, in blocks
     */
    static Vec3 stepToward(Vec3 at, Vec3 glove, int ticksLeft) {
        return glove.subtract(at).scale(1.0 / Math.max(1, ticksLeft));
    }
}
