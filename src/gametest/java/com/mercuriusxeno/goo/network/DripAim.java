package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Aims a streamer so the axis drip of a stream throwing drips passes through
 * a point, for gametests that hold Goop or Slick on one cell: a drip striking
 * a side lays nothing, so a test aims it down onto the top it coats.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
final class DripAim {

    /** The steepest a drip is aimed up and down, in degrees of pitch, within which a higher aim lands higher. */
    private static final float STEEPEST_UP = -45f;
    private static final float STEEPEST_DOWN = 80f;
    /** Halvings of the pitch range the aim is found by, far finer than a block at these reaches. */
    private static final int AIM_HALVINGS = 40;
    private static final double HALF = 0.5;

    private DripAim() {
    }

    /**
     * Turns a streamer toward a point and pitches it so the axis drip, thrown
     * from the eye at the throw speed, passes through the point, the pitch
     * found by halving between the steepest aims.
     *
     * @param streamer   the streaming player
     * @param target     the point, in world space
     * @param throwSpeed the stream's throw speed, blocks per tick
     * @param travel     the streamer's travel velocity, which the drip carries, blocks per tick
     */
    static void at(ServerPlayer streamer, Vec3 target, double throwSpeed, Vec3 travel) {
        streamer.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        Vec3 eye = streamer.getEyePosition();
        double reach = target.subtract(eye).horizontalDistance();
        float up = STEEPEST_UP;
        float down = STEEPEST_DOWN;
        for (int halving = 0; halving < AIM_HALVINGS; halving++) {
            float pitch = (up + down) / 2;
            Vec3 launch = Vec3.directionFromRotation(pitch, streamer.getYRot()).scale(throwSpeed).add(travel);
            if (heightAt(eye, launch, reach) > target.y) {
                up = pitch;
            } else {
                down = pitch;
            }
        }
        streamer.setXRot((up + down) / 2);
    }

    /**
     * How high a drip's arc stands once it has flown a level distance from its launch, through open air.
     *
     * @param from   the drip's launch point
     * @param launch its launch velocity, blocks per tick
     * @param reach  the level distance
     * @return the arc's height there, read between the two ticks either side
     */
    private static double heightAt(Vec3 from, Vec3 launch, double reach) {
        Vec3 at = from;
        Vec3 velocity = launch;
        while (true) {
            DripArc.Step step = DripArc.step(at, velocity, (start, end) -> Optional.empty(), point -> true);
            double before = at.subtract(from).horizontalDistance();
            double after = step.position().subtract(from).horizontalDistance();
            if (after >= reach) {
                double share = after > before ? (reach - before) / (after - before) : HALF;
                return at.y + (step.position().y - at.y) * share;
            }
            at = step.position();
            velocity = step.velocity();
        }
    }
}
