package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.SwarmMotes;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.world.phys.Vec3;

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
     * A seeking gnat's launch from the glove, carrying the caster's travel.
     * glove-stream-motes-carry-the-players-travel-velocity
     *
     * @param aim          the heading from the glove onto the crosshair, any length
     * @param sprayDegrees the cone the swarm fans across, apex to rim, in degrees
     * @param tiltShare    how far off the firing line, 0 on it and 1 at the spray's rim
     * @param turnShare    which way round the firing line, a share of a full turn
     * @param travel       the caster's travel velocity, blocks per tick
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 aim, double sprayDegrees, double tiltShare, double turnShare, Vec3 travel) {
        return GooStreamHandler.moteLaunch(StreamCone.launchDirection(aim, sprayDegrees, tiltShare, turnShare),
                CRUISE, travel);
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
