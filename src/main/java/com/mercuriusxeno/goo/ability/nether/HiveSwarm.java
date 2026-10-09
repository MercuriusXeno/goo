package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
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
    static final double GNAT_FRICTION = 0.85;
    private static final double HALF = 0.5;

    private HiveSwarm() {
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
