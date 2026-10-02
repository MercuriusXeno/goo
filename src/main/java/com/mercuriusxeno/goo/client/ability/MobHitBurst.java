package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The splash a goo landing on a mob throws off the struck point: trail-drip
 * particles in the goo type's color, flung away from the mob's center.
 * Decision hit-bursts-goo-particles.
 */
public final class MobHitBurst {

    /** Drips one burst throws. */
    static final int DRIP_COUNT = 8;

    /** Speed of a drip along the outward direction, in blocks per tick. */
    static final double OUTWARD_SPEED = 0.15;

    /**
     * Largest random kick added to a drip's velocity; below the outward speed,
     * so every drip still leaves the mob.
     */
    static final double SCATTER_SPEED = 0.1;

    /** Fully opaque alpha for particle colors. */
    private static final int OPAQUE_BLACK = 0xFF000000;

    /** Outward direction for a hit point standing on the mob's center. */
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private MobHitBurst() {
    }

    /**
     * One drip of the burst.
     *
     * @param position where it spawns
     * @param velocity its starting velocity
     * @param argb     its opaque color
     */
    record Drip(Vec3 position, Vec3 velocity, int argb) {
    }

    /**
     * The drips a hit throws off its struck point, each flung away from the
     * mob's center.
     *
     * @param hitPoint  the point the goo struck
     * @param mobCenter the struck mob's center
     * @param rgb       the goo type's color
     * @param random    the scatter source
     * @return the drips to spawn
     */
    static List<Drip> splash(Vec3 hitPoint, Vec3 mobCenter, int rgb, RandomGenerator random) {
        Vec3 outward = hitPoint.subtract(mobCenter);
        outward = outward.lengthSqr() > 0 ? outward.normalize() : UP;
        List<Drip> drips = new ArrayList<>(DRIP_COUNT);
        for (int i = 0; i < DRIP_COUNT; i++) {
            Vec3 velocity = outward.scale(OUTWARD_SPEED).add(scatter(random));
            drips.add(new Drip(hitPoint, velocity, rgb | OPAQUE_BLACK));
        }
        return drips;
    }

    /**
     * Spawns a hit's splash in the client level.
     *
     * @param level     the client level
     * @param hitPoint  the point the goo struck
     * @param mobCenter the struck mob's center
     * @param rgb       the goo type's color
     */
    public static void spawn(ClientLevel level, Vec3 hitPoint, Vec3 mobCenter, int rgb) {
        for (Drip drip : splash(hitPoint, mobCenter, rgb, RandomGenerator.getDefault())) {
            level.addParticle(ColorParticleOption.create(GooParticles.TRAIL_DRIP.get(), drip.argb()),
                    drip.position().x, drip.position().y, drip.position().z,
                    drip.velocity().x, drip.velocity().y, drip.velocity().z);
        }
    }

    /**
     * A random kick no longer than the scatter speed.
     *
     * @param random the scatter source
     * @return the kick
     */
    private static Vec3 scatter(RandomGenerator random) {
        Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        if (direction.lengthSqr() == 0) {
            return Vec3.ZERO;
        }
        return direction.normalize().scale(SCATTER_SPEED * random.nextDouble());
    }
}
