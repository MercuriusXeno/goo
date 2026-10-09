package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Convokes a mob from a chunk: picks a random living mob standing anywhere
 * in the chunk, the full height of the level, and teleports it to a spot
 * with an afterimage where it stood and where it lands (decision
 * afterimage-is-one-shared-effect); where the chunk holds none, it pulses
 * the blink's portal particles at the spot instead.
 * Decisions convoke-blob-throbs-until-a-mob-arrives and convoke-drip-rolls-a-small-chance.
 */
public final class ChunkConvoke {

    /** Ticks each convoke's afterimage grows and fades over, the blink's own. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;
    /** A mob already this near the spot has arrived and is not convoked again. */
    static final double ARRIVED_WITHIN = 1.0;
    private static final int PULSE_PARTICLES = 24;
    private static final double PULSE_SPREAD = 0.4;
    private static final double PULSE_SPEED = 0.4;
    private static final float PULSE_VOLUME = 0.4f;
    private static final float PULSE_PITCH = 1.6f;

    private ChunkConvoke() {
    }

    /**
     * Pulls a random mob from the spot's chunk to the spot, or pulses there.
     *
     * @param level the level
     * @param spot  where the mob's feet land
     * @return true once a mob stands at the spot
     */
    public static boolean convoke(ServerLevel level, Vec3 spot) {
        List<Mob> mobs = mobsInChunk(level, spot);
        if (mobs.isEmpty()) {
            pulse(level, spot);
            return false;
        }
        Mob mob = mobs.get(level.getRandom().nextInt(mobs.size()));
        Vec3 stood = mob.position();
        mob.teleportTo(spot.x, spot.y, spot.z);
        mob.setDeltaMovement(Vec3.ZERO);
        for (Vec3 end : new Vec3[] {stood, spot}) {
            EntityVisuals.sendToWatchers(mob, new AfterimagePayload(mob.getId(), end, GooTypes.ENDER,
                    AFTERIMAGE_LIFE_TICKS));
            level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1f);
        }
        return true;
    }

    /**
     * The living mobs standing in the spot's chunk, the full height of the
     * level, leaving out one already at the spot.
     *
     * @param level the level
     * @param spot  the convoke spot
     * @return the candidates
     */
    static List<Mob> mobsInChunk(ServerLevel level, Vec3 spot) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(spot));
        AABB column = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMinY() + level.getHeight(), chunk.getMaxBlockZ() + 1);
        return level.getEntitiesOfClass(Mob.class, column,
                mob -> mob.isAlive() && mob.position().distanceTo(spot) > ARRIVED_WITHIN);
    }

    /**
     * Pulses the blink's portal particles and a faint chime at the spot while
     * no mob comes.
     *
     * @param level the level
     * @param spot  the convoke spot
     */
    private static void pulse(ServerLevel level, Vec3 spot) {
        level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y, spot.z, PULSE_PARTICLES, PULSE_SPREAD,
                PULSE_SPREAD, PULSE_SPREAD, PULSE_SPEED);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_AMBIENT, SoundSource.BLOCKS,
                PULSE_VOLUME, PULSE_PITCH);
    }
}
