package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.GnatSeekPayload;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.SwarmMotes;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * How a seeking gnat flies so it reads as homing rather than auto-aim: it
 * leaves the glove fanned across the stream's cone around the line the
 * player fires, and every tick its prey
 * pulls it, a small push toward the prey added to the velocity it already
 * has, with a little drag; its momentum carries it on along the aim at first,
 * so its path slides off the aim and curves into the mob, and the drag damps
 * the swing so it settles on the mob (decision decay-gnats-degrade-each-block-once).
 */
public final class GnatSteering {

    /** A seeking gnat's speed leaving the glove, blocks per tick, which the server's bite waits on. */
    public static final double CRUISE = SwarmMotes.GNAT_SPEED;
    private static final double PULL = SwarmMotes.PULL;
    private static final double DRAG = SwarmMotes.DRAG;
    /** Within this many blocks of its prey the pull eases off, so the gnat settles on it. */
    private static final double ARRIVING_WITHIN = 1.0;
    /** Below this squared gap a gnat sits on its prey and is pulled no way. */
    private static final double ON_THE_PREY_SQUARED = 1e-6;

    private GnatSteering() {
    }

    /**
     * A seeking gnat's launch from the glove, at its cruise alone.
     * glove-stream-motes-carry-the-players-travel-velocity
     *
     * @param aim          the heading from the glove onto the crosshair, any length
     * @param sprayDegrees the cone the swarm fans across, apex to rim, in degrees
     * @param tiltShare    how far off the firing line, 0 on it and 1 at the spray's rim
     * @param turnShare    which way round the firing line, a share of a full turn
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 aim, double sprayDegrees, double tiltShare, double turnShare) {
        return launch(aim, ConeShape.round(sprayDegrees), tiltShare, turnShare);
    }

    /**
     * A seeking gnat's launch from the glove inside a cone of a shape, at its cruise alone.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param aim       the heading from the glove onto the crosshair, any length
     * @param shape     the cone the swarm fans across, its widths across and up and down
     * @param tiltShare how far off the firing line, 0 on it and 1 at the spray's rim
     * @param turnShare which way round the firing line, a share of a full turn
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 aim, ConeShape shape, double tiltShare, double turnShare) {
        return GooStreamHandler.moteLaunch(StreamCone.launchDirection(aim, shape, tiltShare, turnShare), CRUISE);
    }

    /**
     * Where a gnat seeks a target from: the target itself, or the point on the way to it at the reach where the
     * target stands farther, so no gnat seeks past the cast's reach.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param glove  where the gnat leaves from
     * @param target where it would seek
     * @param reach  the cast's reach, in blocks
     * @return the point it seeks
     */
    public static Vec3 seekWithin(Vec3 glove, Vec3 target, double reach) {
        Vec3 toTarget = target.subtract(glove);
        double distance = toTarget.length();
        return distance <= reach ? target : glove.add(toTarget.scale(reach / distance));
    }

    /**
     * One gnat of a swarm's tick: the velocity it leaves the glove at and the point it seeks with no prey.
     *
     * @param velocity its launch velocity, blocks per tick
     * @param rest     the point it converges on with no prey, within the reach
     */
    public record Launch(Vec3 velocity, Vec3 rest) {
    }

    /**
     * The gnats a swarm's tick launches: each fanned inside the payload's shape, each converging on its own point
     * of the box the crosshair lands on, held within the payload's reach.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param payload the swarm's tick
     * @param glove   where the gnats leave from
     * @param gnats   how many gnats the tick launches
     * @param random  the random source
     * @return each gnat's launch
     */
    public static List<Launch> launchesOf(GnatSeekPayload payload, Vec3 glove, int gnats, RandomSource random) {
        List<Launch> launches = new ArrayList<>(gnats);
        for (int gnat = 0; gnat < gnats; gnat++) {
            Vec3 velocity = launch(payload.aim(), payload.shape(), random.nextDouble(), random.nextDouble());
            Vec3 rest = seekWithin(glove, pointIn(payload.targetMin(), payload.targetMax(), random), payload.reach());
            launches.add(new Launch(velocity, rest));
        }
        return launches;
    }

    private static Vec3 pointIn(Vec3 min, Vec3 max, RandomSource random) {
        return new Vec3(min.x + random.nextDouble() * (max.x - min.x), min.y + random.nextDouble() * (max.y - min.y),
                min.z + random.nextDouble() * (max.z - min.z));
    }

    /**
     * The gnat's velocity after one tick of seeking.
     *
     * @param velocity the gnat's velocity, blocks per tick
     * @param at       where the gnat is
     * @param prey     where its prey is this tick
     * @return the new velocity
     */
    public static Vec3 steer(Vec3 velocity, Vec3 at, Vec3 prey) {
        Vec3 toPrey = prey.subtract(at);
        double gap = toPrey.length();
        if (gap * gap < ON_THE_PREY_SQUARED) {
            return velocity.scale(DRAG);
        }
        Vec3 pull = toPrey.scale(PULL * Math.min(1, gap / ARRIVING_WITHIN) / gap);
        return velocity.add(pull).scale(DRAG);
    }
}
