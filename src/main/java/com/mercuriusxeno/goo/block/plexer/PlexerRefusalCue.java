package com.mercuriusxeno.goo.block.plexer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The cue a plexer plays when it refuses a target the player has not learned: smoke
 * puffs in the cutaway and a fizzle sounds, in place of a message
 * (decision plexer-messages-go-and-refusal-fizzles).
 */
public final class PlexerRefusalCue {

    /** Smoke particles in one puff. */
    public static final int SMOKE_COUNT = 4;
    /** Smoke spread across the cutaway's opening, in blocks. */
    private static final double SMOKE_SPREAD_XZ = 0.08;
    /** Smoke spread up the cutaway, in blocks. */
    private static final double SMOKE_SPREAD_Y = 0.05;
    /** Smoke drift speed. */
    private static final double SMOKE_SPEED = 0.01;
    /** Fizzle volume, quiet beside the plexer's craft. */
    private static final float FIZZLE_VOLUME = 0.5f;
    /** Fizzle pitch, raised so the extinguish reads short and small. */
    private static final float FIZZLE_PITCH = 1.6f;

    private PlexerRefusalCue() {
    }

    /**
     * Puffs smoke at the cutaway's center and plays a fizzle from the plexer, to every player near it.
     *
     * @param level         the server level
     * @param pos           the plexer's position
     * @param cutawayCenter the cutaway's center in world coordinates
     */
    static void playAt(ServerLevel level, BlockPos pos, Vec3 cutawayCenter) {
        level.sendParticles(ParticleTypes.SMOKE, cutawayCenter.x, cutawayCenter.y, cutawayCenter.z, SMOKE_COUNT,
                SMOKE_SPREAD_XZ, SMOKE_SPREAD_Y, SMOKE_SPREAD_XZ, SMOKE_SPEED);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, FIZZLE_VOLUME, FIZZLE_PITCH);
    }
}
