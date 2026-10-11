package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Whether Meteo has a clear path to the sky over a cell, and the fizzle a
 * cast with none ends in: a puff of smoke and the extinguish sound, as the
 * plexer's refusal sounds (decision plexer-messages-go-and-refusal-fizzles).
 * decision meteo-needs-a-clear-sky
 */
public final class MeteorSky {

    private static final int SMOKE_PARTICLES = 12;
    private static final double SMOKE_SPREAD = 0.25;
    private static final double SMOKE_SPEED = 0.02;
    private static final float FIZZLE_VOLUME = 0.5f;
    private static final float FIZZLE_PITCH = 1.6f;

    private MeteorSky() {
    }

    /**
     * Whether nothing that blocks motion stands over a cell, up to the
     * world's build height.
     *
     * @param level the level
     * @param cell  the cell the meteor strikes
     * @return true for an open sky over the cell
     */
    public static boolean clearAbove(Level level, BlockPos cell) {
        return clearUnder(level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.getX(), cell.getZ()), cell.getY());
    }

    /**
     * Whether a cell stands at or above the first open cell of its column.
     *
     * @param openFrom the lowest y over which every cell of the column lets motion through
     * @param cellY    the cell's y
     * @return true when no blocking cell stands over the cell
     */
    static boolean clearUnder(int openFrom, int cellY) {
        return cellY >= openFrom;
    }

    /**
     * Fizzles a cast at a cell: smoke puffs there and the extinguish sounds.
     *
     * @param level the server level
     * @param cell  the cell the cast was aimed at
     */
    public static void fizzle(ServerLevel level, BlockPos cell) {
        Vec3 at = Vec3.atCenterOf(cell);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, SMOKE_PARTICLES, SMOKE_SPREAD, SMOKE_SPREAD,
                SMOKE_SPREAD, SMOKE_SPEED);
        level.playSound(null, cell, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, FIZZLE_VOLUME, FIZZLE_PITCH);
    }
}
