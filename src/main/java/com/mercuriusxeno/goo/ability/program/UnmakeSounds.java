package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What an unmake sounds like where it works: wet bubbling from what is
 * melting, a few times a second, and a wet plop when it is unmade.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeSounds {

    /** Ticks between bubbles from one melting thing. */
    static final int BUBBLE_EVERY = 5;
    private static final float BUBBLE_VOLUME = 0.6f;
    private static final float PLOP_VOLUME = 0.9f;
    private static final float PITCH_LOW = 0.7f;
    private static final float PITCH_SPREAD = 0.6f;

    private UnmakeSounds() {
    }

    /**
     * Bubbles at a melting thing on its own beat, its phase set by where it
     * stands so neighbours do not bubble in step.
     *
     * @param level the server level
     * @param at    where it melts
     * @param phase a number fixed for the thing, offsetting its beat
     */
    public static void bubble(ServerLevel level, Vec3 at, int phase) {
        if (Math.floorMod(level.getGameTime() + phase, BUBBLE_EVERY) == 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS,
                    BUBBLE_VOLUME, PITCH_LOW + level.getRandom().nextFloat() * PITCH_SPREAD);
        }
    }

    /**
     * Plops where a thing was unmade.
     *
     * @param level the server level
     * @param at    where it was
     */
    public static void plop(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, PLOP_VOLUME,
                PITCH_LOW + level.getRandom().nextFloat() * PITCH_SPREAD);
    }
}
