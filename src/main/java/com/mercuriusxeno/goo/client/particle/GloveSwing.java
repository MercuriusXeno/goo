package com.mercuriusxeno.goo.client.particle;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.IntFunction;

/**
 * How a glove stream mote swings with the glove when its caster turns: its
 * offset from the glove and its velocity rotate by the change in the
 * caster's look, in yaw and in pitch, fully at launch and fading evenly to
 * none over {@link #SWING_TICKS}, after which the mote flies free.
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
public final class GloveSwing {

    /** Ticks over which a mote's swing fades to none, the value the operator tunes in play. */
    public static final double SWING_TICKS = 2.0;

    private GloveSwing() {
    }

    /**
     * A mote's position and velocity after its swing.
     *
     * @param position where the mote stands
     * @param velocity the mote's velocity, blocks per tick
     */
    public record Swung(Vec3 position, Vec3 velocity) {
    }

    /**
     * Where a caster's glove stands and where the caster looks, this tick.
     *
     * @param glove the glove the mote swings about
     * @param look  the caster's look
     */
    public record CasterPose(Vec3 glove, Vec3 look) {
    }

    /**
     * Swings a mote with the glove by one tick's change in its caster's look.
     *
     * @param position   where the mote stands
     * @param velocity   the mote's velocity, blocks per tick
     * @param pivot      the glove the mote swings about
     * @param lookBefore the caster's look last tick
     * @param lookNow    the caster's look this tick
     * @param age        the mote's age in ticks
     * @return the swung position and velocity, the inputs where the look held still or the mote is past its window
     */
    public static Swung swing(Vec3 position, Vec3 velocity, Vec3 pivot, Vec3 lookBefore, Vec3 lookNow, double age) {
        double share = 1.0 - age / SWING_TICKS;
        double yawBefore = yawOf(lookBefore);
        double pitchBefore = pitchOf(lookBefore);
        double yawTurn = wrapped(yawOf(lookNow) - yawBefore);
        double pitchTurn = pitchOf(lookNow) - pitchBefore;
        if (share <= 0 || yawTurn == 0 && pitchTurn == 0) {
            return new Swung(position, velocity);
        }
        LookFrame from = LookFrame.of(yawBefore, pitchBefore);
        LookFrame to = LookFrame.of(yawBefore + yawTurn * share, pitchBefore + pitchTurn * share);
        return new Swung(pivot.add(to.carry(from, position.subtract(pivot))), to.carry(from, velocity));
    }

    private static double yawOf(Vec3 look) {
        return Math.atan2(look.x, look.z);
    }

    private static double pitchOf(Vec3 look) {
        return Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z));
    }

    private static double wrapped(double turn) {
        return Math.atan2(Math.sin(turn), Math.cos(turn));
    }

    /**
     * The axes a look points along: forward, up off the look, and sideways.
     *
     * @param forward  along the look
     * @param up       square to the look, toward the sky
     * @param sideways square to both
     */
    private record LookFrame(Vec3 forward, Vec3 up, Vec3 sideways) {

        static LookFrame of(double yaw, double pitch) {
            double sinYaw = Math.sin(yaw);
            double cosYaw = Math.cos(yaw);
            double sinPitch = Math.sin(pitch);
            double cosPitch = Math.cos(pitch);
            return new LookFrame(new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch),
                    new Vec3(-sinYaw * sinPitch, cosPitch, -cosYaw * sinPitch), new Vec3(cosYaw, 0, -sinYaw));
        }

        /**
         * Carries a vector held in another frame into this one, keeping its
         * share along each axis.
         *
         * @param from   the frame the vector was held in
         * @param vector the vector
         * @return the vector in this frame
         */
        Vec3 carry(LookFrame from, Vec3 vector) {
            return forward.scale(vector.dot(from.forward)).add(up.scale(vector.dot(from.up)))
                    .add(sideways.scale(vector.dot(from.sideways)));
        }
    }

    /**
     * One mote's swing, tick over tick: it reads its own caster's pose each
     * tick by the caster's id, so another player's turn leaves it alone.
     */
    public static final class Tracker {

        private final int casterId;
        private final IntFunction<@Nullable CasterPose> poses;
        private @Nullable Vec3 lastLook;

        /**
         * Starts a mote's swing from its caster's look at launch.
         *
         * @param casterId the player who sprayed the mote
         * @param poses    each caster's pose this tick by id, null for one this client holds none of
         */
        public Tracker(int casterId, IntFunction<@Nullable CasterPose> poses) {
            this.casterId = casterId;
            this.poses = poses;
            CasterPose launch = poses.apply(casterId);
            this.lastLook = launch == null ? null : launch.look();
        }

        /**
         * Swings the mote by its caster's turn since last tick.
         *
         * @param position where the mote stands
         * @param velocity the mote's velocity, blocks per tick
         * @param age      the mote's age in ticks
         * @return the swung position and velocity, or null once the mote flies free or its caster is gone
         */
        public @Nullable Swung step(Vec3 position, Vec3 velocity, int age) {
            if (age >= SWING_TICKS) {
                return null;
            }
            CasterPose pose = poses.apply(casterId);
            Vec3 before = lastLook;
            lastLook = pose == null ? null : pose.look();
            if (pose == null || before == null) {
                return null;
            }
            return swing(position, velocity, pose.glove(), before, pose.look(), age);
        }
    }
}
