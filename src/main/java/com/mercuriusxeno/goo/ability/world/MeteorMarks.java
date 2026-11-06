package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What Meteo shows the world as its meteor comes: a ring marking the target
 * through the wind-up while the rock smoulders high above, a trail of fire
 * and smoke as it drops, and a flash where it lands.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
public final class MeteorMarks {

    /** The marking ring's radius around the target, in blocks. */
    static final double MARK_RADIUS = 1.6;
    /** Points around the marking ring each tick of the wind-up. */
    static final int MARK_POINTS = 16;
    private static final double MARK_LIFT = 0.1;
    private static final int SMOULDER_PARTICLES = 3;
    private static final double SMOULDER_SPREAD = 0.4;
    private static final int TRAIL_FLAMES = 10;
    private static final int TRAIL_SMOKE = 6;
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
     * One tick of the wind-up: the ring at the target, and the rock
     * smouldering where it hangs; its first tick sounds the call.
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
        for (Vec3 point : ringAround(target, age)) {
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y, start.z, SMOULDER_PARTICLES,
                SMOULDER_SPREAD, SMOULDER_SPREAD, SMOULDER_SPREAD, TRAIL_SPEED);
        level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, SMOULDER_PARTICLES,
                SMOULDER_SPREAD, SMOULDER_SPREAD, SMOULDER_SPREAD, TRAIL_SPEED);
    }

    /**
     * The marking ring's points on a tick of the wind-up, turning a little
     * each tick so the ring reads as alive.
     *
     * @param target the point the meteor strikes
     * @param age    the wind-up's tick
     * @return the ring's points, just above the target
     */
    static Vec3[] ringAround(Vec3 target, int age) {
        Vec3[] points = new Vec3[MARK_POINTS];
        double turn = age * Math.PI / MARK_POINTS;
        for (int index = 0; index < MARK_POINTS; index++) {
            double angle = turn + index * Math.TAU / MARK_POINTS;
            points[index] = target.add(Math.cos(angle) * MARK_RADIUS, MARK_LIFT, Math.sin(angle) * MARK_RADIUS);
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
     * One tick of the drop's trail: fire and smoke streaming off the rock.
     *
     * @param level the server level
     * @param at    where the rock stands
     */
    public static void trail(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, TRAIL_FLAMES, TRAIL_SPREAD, TRAIL_SPREAD,
                TRAIL_SPREAD, TRAIL_SPEED);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, TRAIL_SMOKE, TRAIL_SPREAD, TRAIL_SPREAD,
                TRAIL_SPREAD, TRAIL_SPEED);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, TRAIL_LAVA, TRAIL_SPREAD, TRAIL_SPREAD,
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
