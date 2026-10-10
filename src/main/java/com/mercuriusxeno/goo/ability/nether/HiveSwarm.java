package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * What a hive prism shows on the client each tick: with no mob in its reach
 * a few gnats hover clustered around the column, and with mobs in reach the
 * swarm emanates from the column, gnats flying out to each mob it eats. The
 * server's program does the eating; this only shows the swarm, reading mobs
 * the way the hive's program selects them
 * (decision hive-prism-pillar-eats-the-living).
 */
public final class HiveSwarm {

    /** The combo a hive prism holds, nether's hive ability. */
    public static final String COMBO = "goo:nether_hive";
    /** nether_hive.json's radius, the reach the swarm flies out to. */
    public static final double REACH = 5;
    /** How far from the column's center an idle gnat hovers. */
    static final double CLUSTER_RADIUS = 0.6;
    /** The drift an idle gnat starts with, each way. */
    static final double IDLE_DRIFT = 0.02;
    /** Gnats launched at each mob in reach each tick. */
    static final int GNATS_PER_MOB = 2;
    /**
     * The share of its speed a gnat keeps each tick, the gnat particle's
     * friction; a launch reaching a distance carries that distance times one less this.
     */
    static final double GNAT_FRICTION = 0.9;
    private static final double HALF = 0.5;
    /** Decay's buzz: a bee's loop, soft and pitched high (nether_decay.json's sound). */
    private static final Identifier BUZZ = GooSoundIds.GNAT_BUZZ;
    private static final float BUZZ_VOLUME = 0.12f;
    private static final float BUZZ_PITCH = 1.95f;
    private static final float BUZZ_PITCH_SPREAD = 0.1f;

    /** Keeps the swarm's buzz loop sounding; the client installs it, a server needs none. */
    private static LoopKeeper buzzLoops = (key, sound, source, volume, pitch, at) -> { };

    private HiveSwarm() {
    }

    /**
     * Something keeping a fading loop sounding one more tick for a key.
     */
    @FunctionalInterface
    public interface LoopKeeper {
        /**
         * Keeps the key's loop sounding one more tick, starting it when none sounds.
         *
         * @param key    what the loop belongs to
         * @param sound  the looped sound's id
         * @param source the mixer channel it plays on
         * @param volume the volume it swells to
         * @param pitch  its pitch, used when it starts
         * @param at     where it plays
         */
        void keepAlive(Object key, Identifier sound, SoundSource source, float volume, float pitch, Vec3 at);
    }

    /**
     * Installs the client's fading loops as the swarm's buzz.
     *
     * @param keeper the client's loop keeper
     */
    public static void installBuzzLoops(LoopKeeper keeper) {
        buzzLoops = keeper;
    }

    /**
     * Shows one tick of the swarm around a hive prism.
     *
     * @param level the client level
     * @param pos   the prism's position
     */
    public static void tick(Level level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        RandomSource random = level.getRandom();
        // the operator's ruling on Hive: the swarm buzzes as Decay's gnats do, one loop fading out with the prism
        buzzLoops.keepAlive(pos.immutable(), BUZZ, SoundSource.BLOCKS, BUZZ_VOLUME,
                BUZZ_PITCH + (random.nextFloat() - (float) HALF) * BUZZ_PITCH_SPREAD, center);
        List<Mob> eaten = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(REACH),
                mob -> mob.isAlive() && mob.position().distanceTo(center) <= REACH);
        if (eaten.isEmpty()) {
            Vec3 at = center.add(spread(random, CLUSTER_RADIUS));
            Vec3 drift = spread(random, IDLE_DRIFT);
            level.addParticle(GooParticles.GNAT.get(), at.x, at.y, at.z, drift.x, drift.y, drift.z);
            return;
        }
        for (Mob mob : eaten) {
            Vec3 launch = launchToward(center, mob.getBoundingBox().getCenter());
            for (int gnat = 0; gnat < GNATS_PER_MOB; gnat++) {
                level.addParticle(GooParticles.GNAT.get(), center.x, center.y, center.z, launch.x, launch.y, launch.z);
            }
        }
    }

    /**
     * The launch speed that carries a gnat from one point to another as its
     * friction slows it: the gap times the share of speed lost each tick.
     *
     * @param from where the gnat leaves
     * @param to   where it should come to rest
     * @return the launch velocity, in blocks per tick
     */
    static Vec3 launchToward(Vec3 from, Vec3 to) {
        return to.subtract(from).scale(1 - GNAT_FRICTION);
    }

    /**
     * A random offset up to a reach either way on each axis.
     *
     * @param random the random source
     * @param reach  the most the offset strays each way
     * @return the offset
     */
    private static Vec3 spread(RandomSource random, double reach) {
        return new Vec3(stray(random, reach), stray(random, reach), stray(random, reach));
    }

    private static double stray(RandomSource random, double reach) {
        return (random.nextDouble() - HALF) * (reach + reach);
    }
}
