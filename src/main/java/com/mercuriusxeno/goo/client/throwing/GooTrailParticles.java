package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Trail particle spawning for goo flights: drip sparks and fog puffs
 * shed along the wake. Throttled to every other tick.
 */
final class GooTrailParticles {

    /**
     * Tick interval for trail particle spawning (every N ticks).
     */
    private static final int PARTICLE_TICK_INTERVAL = 2;

    /**
     * Fully opaque black alpha for particle colors.
     */
    private static final int OPAQUE_BLACK = 0xFF000000;

    /**
     * Downward pull a drip launches with on top of the blob's velocity.
     */
    static final double DRIP_DOWN_VEL = -0.02;

    /**
     * Minimum squared distance from camera before fog particles spawn.
     */
    private static final double FOG_MIN_DIST_SQ = 9.0;

    /**
     * Base fog particle count before random addition.
     */
    private static final int FOG_BASE_COUNT = 2;

    /**
     * Random fog particle count range (exclusive upper bound).
     */
    private static final int FOG_RANDOM_RANGE = 3;

    /**
     * Fog position offset scale behind the goo.
     */
    private static final double FOG_POS_SCALE = 0.15;

    /**
     * Position spread multiplier for fog particles.
     */
    private static final double FOG_SPREAD = 2;

    /**
     * Velocity jitter for fog particles.
     */
    private static final double FOG_VEL_JITTER = 0.02;

    /**
     * Random offset range half-extent.
     */
    private static final double OFFSET_HALF = 0.5;

    /**
     * Random offset scale.
     */
    private static final double OFFSET_SCALE = 0.1;

    private GooTrailParticles() {
    }

    /**
     * Spawns trail particles behind the goo: a viscous slime drip downward
     * and several radial fog puffs along the wake, each launched with the
     * blob's velocity. Throttled to every other tick.
     *
     * @param pos    the goo world position
     * @param flight the flight instance for tick tracking
     * @param motion the flight's unit heading and its velocity this frame
     * @param type   the goo type
     */
    static void spawnTrailParticles(Vec3 pos, GooFlightManager.GooFlight flight, TrailMotion motion,
                                    ResourceKey<GooTypeDefinition> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (flight.ticksElapsed % PARTICLE_TICK_INTERVAL != 0) {
            return;
        }

        int color = GooColors.get(mc.level.registryAccess(), type);
        spawnDripParticle(mc, pos, motion.blobVelocity(), color);
        spawnFogParticles(mc, pos, motion, color);
    }

    /**
     * How a flight moves this frame, as its trail reads it.
     *
     * @param heading      the flight's unit heading, which places the fog behind the blob
     * @param blobVelocity the blob's velocity, blocks per tick, which each drip and puff launches with
     */
    record TrailMotion(Vec3 heading, Vec3 blobVelocity) {
    }

    /**
     * A drip's launch velocity: the blob's, plus its downward pull.
     * thrown-blob-trail-takes-the-blobs-velocity
     *
     * @param blobVelocity the blob's velocity, blocks per tick
     * @return the drip's launch velocity, blocks per tick
     */
    static Vec3 dripLaunch(Vec3 blobVelocity) {
        return blobVelocity.add(0, DRIP_DOWN_VEL, 0);
    }

    /**
     * A fog puff's launch velocity: the blob's, plus the puff's jitter.
     * thrown-blob-trail-takes-the-blobs-velocity
     *
     * @param blobVelocity the blob's velocity, blocks per tick
     * @param jitter       the puff's own random push, blocks per tick
     * @return the puff's launch velocity, blocks per tick
     */
    static Vec3 fogLaunch(Vec3 blobVelocity, Vec3 jitter) {
        return blobVelocity.add(jitter);
    }

    /**
     * Spawns a single slime drip particle moving with the blob and pulled down.
     *
     * @param mc           the Minecraft client instance
     * @param pos          the goo world position
     * @param blobVelocity the blob's velocity, blocks per tick
     * @param color        the RGB color of the goo type
     */
    private static void spawnDripParticle(Minecraft mc, Vec3 pos, Vec3 blobVelocity, int color) {
        ColorParticleOption dripOption = ColorParticleOption.create(
                GooParticles.TRAIL_DRIP.get(), color | OPAQUE_BLACK);
        Vec3 launch = dripLaunch(blobVelocity);
        mc.level.addParticle(dripOption,
                pos.x + randomOffset(), pos.y + randomOffset(), pos.z + randomOffset(),
                launch.x, launch.y, launch.z);
    }

    /**
     * Spawns 2-4 fog puff particles behind the goo, suppressed when close to the camera.
     *
     * @param mc     the Minecraft client instance
     * @param pos    the goo world position
     * @param motion the flight's heading and velocity
     * @param color  the RGB color of the goo type
     */
    private static void spawnFogParticles(Minecraft mc, Vec3 pos, TrailMotion motion, int color) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        if (pos.distanceToSqr(camPos) <= FOG_MIN_DIST_SQ) {
            return;
        }

        ColorParticleOption fogOption = ColorParticleOption.create(
                GooParticles.GOO_FOG.get(), color | OPAQUE_BLACK);
        int fogCount = FOG_BASE_COUNT + ThreadLocalRandom.current().nextInt(FOG_RANDOM_RANGE);
        for (int i = 0; i < fogCount; i++) {
            emitSingleFogPuff(mc, fogOption, pos, motion);
        }
    }

    /**
     * Emits one fog puff particle at a jittered position behind the goo,
     * moving with the blob.
     *
     * @param mc        the Minecraft client instance
     * @param fogOption the color particle option for fog
     * @param pos       the goo world position
     * @param motion    the flight's heading and velocity
     */
    private static void emitSingleFogPuff(Minecraft mc, ColorParticleOption fogOption,
                                          Vec3 pos, TrailMotion motion) {
        Vec3 behind = motion.heading().scale(FOG_POS_SCALE);
        Vec3 launch = fogLaunch(motion.blobVelocity(), new Vec3(randomOffset() * FOG_VEL_JITTER,
                randomOffset() * FOG_VEL_JITTER, randomOffset() * FOG_VEL_JITTER));
        mc.level.addParticle(fogOption,
                pos.x - behind.x + randomOffset() * FOG_SPREAD,
                pos.y - behind.y + randomOffset() * FOG_SPREAD,
                pos.z - behind.z + randomOffset() * FOG_SPREAD,
                launch.x, launch.y, launch.z);
    }

    /**
     * Small random offset for particle position jitter.
     *
     * @return a small random offset value
     */
    private static double randomOffset() {
        return (ThreadLocalRandom.current().nextDouble() - OFFSET_HALF) * OFFSET_SCALE;
    }
}
