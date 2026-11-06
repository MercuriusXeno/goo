package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What Meteo shows the world as its meteor comes: the rock smouldering high
 * over its target through the wind-up, a trail of fire and smoke as it
 * drops, and a flash where it lands.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
public final class MeteorMarks {

    /** Points along each tick's fall the trail puffs from, so it runs unbroken. */
    static final int TRAIL_STEPS = 4;
    private static final int SMOULDER_PARTICLES = 3;
    private static final double SMOULDER_SPREAD = 0.4;
    private static final int TRAIL_FLAMES = 4;
    private static final int TRAIL_SMOKE = 2;
    private static final int TRAIL_LAVA = 1;
    private static final double TRAIL_SPREAD = 0.45;
    private static final double TRAIL_SPEED = 0.03;
    private static final int IMPACT_EMBERS = 24;
    private static final double IMPACT_SPREAD = 1.2;
    private static final float FLASH_RED = 1f;
    private static final float FLASH_GREEN = 0.62f;
    private static final float FLASH_BLUE = 0.25f;
    private static final float CALL_VOLUME = 2f;
    private static final float CALL_PITCH = 0.6f;
    private static final float DROP_VOLUME = 3f;
    private static final float DROP_PITCH = 0.5f;

    private MeteorMarks() {
    }

    /**
     * One tick of the wind-up: the rock smouldering where it hangs over its
     * target; its first tick sounds the call.
     *
     * @param level  the server level
     * @param start  where the rock hangs
     * @param target the point it strikes
     * @param age    the wind-up's tick, 1 on its first
     */
    public static void windUp(ServerLevel level, Vec3 start, Vec3 target, int age) {
        if (age == 1) {
            level.playSound(null, target.x, target.y, target.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                    CALL_VOLUME, CALL_PITCH);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y, start.z, SMOULDER_PARTICLES,
                SMOULDER_SPREAD, SMOULDER_SPREAD, SMOULDER_SPREAD, TRAIL_SPEED);
        level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, SMOULDER_PARTICLES,
                SMOULDER_SPREAD, SMOULDER_SPREAD, SMOULDER_SPREAD, TRAIL_SPEED);
    }

    /**
     * The points a tick's trail puffs from: spread evenly along the stretch
     * the rock fell that tick, so the trail runs unbroken down the sky.
     *
     * @param from where the rock stood last tick
     * @param to   where it stands now
     * @return the points, from just below the last tick's spot down to now
     */
    static Vec3[] trailPoints(Vec3 from, Vec3 to) {
        Vec3[] points = new Vec3[TRAIL_STEPS];
        for (int index = 0; index < TRAIL_STEPS; index++) {
            points[index] = from.lerp(to, (index + 1.0) / TRAIL_STEPS);
        }
        return points;
    }

    /**
     * The first tick of the drop: the rock roars as it lets go.
     *
     * @param level the server level
     * @param at    where the rock hangs
     */
    public static void drop(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, DROP_VOLUME,
                DROP_PITCH);
    }

    /**
     * One tick of the drop's trail: fire and smoke streaming off the rock
     * all along the stretch it fell this tick.
     *
     * @param level the server level
     * @param from  where the rock stood last tick
     * @param to    where it stands now
     */
    public static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        for (Vec3 at : trailPoints(from, to)) {
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, TRAIL_FLAMES, TRAIL_SPREAD, TRAIL_SPREAD,
                    TRAIL_SPREAD, TRAIL_SPEED);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, TRAIL_SMOKE, TRAIL_SPREAD,
                    TRAIL_SPREAD, TRAIL_SPREAD, TRAIL_SPEED);
        }
        level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, TRAIL_LAVA, TRAIL_SPREAD, TRAIL_SPREAD,
                TRAIL_SPREAD, 0);
    }

    /**
     * The flash where the rock lands, with embers thrown out of the crater.
     *
     * @param level the server level
     * @param at    the point it struck
     */
    public static void impact(ServerLevel level, Vec3 at) {
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, FLASH_RED, FLASH_GREEN, FLASH_BLUE),
                at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, IMPACT_EMBERS, IMPACT_SPREAD, IMPACT_SPREAD,
                IMPACT_SPREAD, 0);
    }
}
