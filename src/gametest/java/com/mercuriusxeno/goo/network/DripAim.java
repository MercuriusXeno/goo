package com.mercuriusxeno.goo.network;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Aims a streamer so the axis drip of a stream throwing drips passes through
 * a point, for gametests that hold Goop or Slick on one cell: a drip striking
 * a side lays nothing, so a test aims it down onto the top it coats. The
 * stream aims its drips through the crosshair's point, so the streamer looks
 * at the point.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * diagnose-and-correct-the-spray-landing-off-the-aim
 */
final class DripAim {

    private DripAim() {
    }

    /**
     * Turns a streamer's crosshair onto a point.
     *
     * @param streamer the streaming player
     * @param target   the point, in world space
     */
    static void at(ServerPlayer streamer, Vec3 target) {
        streamer.lookAt(EntityAnchorArgument.Anchor.EYES, target);
    }
}
