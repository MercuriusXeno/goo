package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * How a break particle flies to the hand: the block bursts, each particle
 * kicked out from the block's middle and settling back into the cloud, so
 * the block reads as shattering; it waits there until its turn in the drain
 * comes, the turns spread over the drain from the square landing to the
 * block drained, then flies straight from where it stands toward the glove
 * at the stream's base pace, following the glove as it moves, and is done at
 * the glove.
 * decision unmake-drink-is-a-stream-of-break-particles
 */
public final class DrinkRide {

    /** Ticks the burst's kick takes to carry a particle out to its furthest. */
    public static final int POP_TICKS = 4;
    /** Ticks a kicked particle takes to settle back to its place in the cloud. */
    public static final int SETTLE_TICKS = 12;
    /** The furthest a particle kicks out from its place in the cloud, in blocks. */
    private static final double POP_REACH = 0.6;
    /** The least share of the furthest kick a particle takes, so every particle visibly kicks. */
    private static final double LEAST_KICK = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkRide() {
    }

    /**
     * One break particle's part in its block's drain.
     *
     * @param cloud   where it waits in the cloud, inside the block
     * @param kick    how far and which way the burst kicks it out from there, away from the block's middle
     * @param burst   the game time the block bursts
     * @param release the game time its turn comes and it leaves the cloud
     */
    public record Mote(Vec3 cloud, Vec3 kick, double burst, double release) {
    }

    /**
     * @param block  the block bursting
     * @param count  how many particles it bursts into
     * @param random the client's random
     * @return each particle's part: a place in the block, and a turn spread over the drain, every one before the
     *         block is drained so the cloud is empty as the drain ends
     */
    public static List<Mote> motesOf(DrinkTree.Block block, int count, RandomSource random) {
        BlockPos pos = block.pos();
        double drain = Math.max(1, block.end() - block.start());
        List<Mote> motes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Vec3 cloud = new Vec3(pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble());
            double release = block.start() + drain * (index + random.nextDouble()) / count;
            motes.add(new Mote(cloud, kickOf(cloud, block.center(), random), block.start(), release));
        }
        return motes;
    }

    /**
     * @param cloud  a particle's place in the cloud
     * @param center the block's middle
     * @param random the client's random
     * @return the burst's kick to it: straight out from the block's middle, half to all of the furthest reach
     */
    private static Vec3 kickOf(Vec3 cloud, Vec3 center, RandomSource random) {
        Vec3 out = cloud.subtract(center);
        Vec3 way = out.lengthSqr() > 0 ? out.normalize() : UP;
        return way.scale(POP_REACH * (LEAST_KICK + (1 - LEAST_KICK) * random.nextDouble()));
    }

    /**
     * Where a particle stands before it flies: kicked out of its place in the
     * cloud as the block bursts, then settling back to it.
     *
     * @param mote its part in the drain
     * @param now  the game time, with the partial tick
     * @return where it stands in the burst
     */
    static Vec3 burstPlaceOf(Mote mote, double now) {
        double since = now - mote.burst();
        double out = since < POP_TICKS ? DrinkStream.smoothRamp(0, POP_TICKS, since)
                : 1 - DrinkStream.smoothRamp(POP_TICKS, POP_TICKS + SETTLE_TICKS, since);
        return mote.cloud().add(mote.kick().scale(out));
    }

    /**
     * @param release the game time its turn comes
     * @param now     the game time, with the partial tick
     * @return blocks it has flown from its place in the cloud: none before its turn, then as far as the base pace
     *         carries it in the time since
     */
    public static double distanceOf(double release, double now) {
        return now < release ? 0 : DrinkStream.FLOW * (now - release);
    }

    /**
     * @param from     where its flight leaves from
     * @param glove    the drinker's glove
     * @param distance blocks it has flown
     * @return whether it has reached the glove
     */
    public static boolean done(Vec3 from, Vec3 glove, double distance) {
        return distance >= from.distanceTo(glove);
    }

    /**
     * @param mote  its part in the drain
     * @param glove the drinker's glove now
     * @param now   the game time, with the partial tick
     * @return where it stands: in the burst before its turn, then on the line from there to the glove; or null
     *         once it has reached the glove
     */
    public static @Nullable Vec3 pointOf(Mote mote, Vec3 glove, double now) {
        Vec3 from = burstPlaceOf(mote, now);
        double distance = distanceOf(mote.release(), now);
        if (done(from, glove, distance)) {
            return null;
        }
        return from.lerp(glove, distance / from.distanceTo(glove));
    }
}
