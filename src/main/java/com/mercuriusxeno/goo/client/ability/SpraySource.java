package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.KnifeRainSling;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where a Goop or Slick spray's drips appear: a point held still in the
 * caster's view, near and above the glove, a set way ahead of the eye, out
 * to the glove's side and below the eye. It moves and turns with the caster
 * and never bobs or swings with the hand, so the spray keeps one steady
 * source. The local player's source stands in the camera's view; another
 * player's in that player's eye and look as this client draws them.
 * goop-and-slick-spray-emanates-from-the-glove
 */
public final class SpraySource {

    /** How far ahead of the eye the source stands, along the look. */
    static final double AHEAD = 0.7;
    /** How far out to the glove's side of the look the source stands. */
    static final double TO_THE_GLOVE_SIDE = 0.3;
    /** How far below the look the source stands, a little above the glove's palm. */
    static final double BELOW = 0.15;
    /** A quarter turn, in degrees. */
    private static final float QUARTER_TURN = 90f;

    private SpraySource() {
    }

    /**
     * The source in a view: ahead of the eye along the look, out to the
     * glove's side and down, each square to the others.
     *
     * @param eye        the viewer's eye
     * @param xRot       the view's pitch in degrees, negative looking up
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @return the source
     */
    static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight) {
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
        Vec3 right = Vec3.directionFromRotation(0f, yRot + QUARTER_TURN);
        return eye.add(ahead.scale(AHEAD)).add(right.scale(gloveRight ? TO_THE_GLOVE_SIDE : -TO_THE_GLOVE_SIDE))
                .subtract(up.scale(BELOW));
    }

    /**
     * The caster's spray source this frame: in the camera's view for the
     * local player in first person, in the caster's own view otherwise, and
     * the glove the server sent for a caster this client does not hold.
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the server sent with the stream's tick
     * @param partialTick the frame's partial tick
     * @return the source
     */
    public static Vec3 of(int casterId, Vec3 sentGlove, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return sentGlove;
        }
        boolean gloveRight = KnifeRainSling.gloveOnTheRight(caster);
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return inView(camera.position(), camera.xRot(), camera.yRot(), gloveRight);
        }
        return inView(caster.getEyePosition(partialTick), caster.getViewXRot(partialTick),
                caster.getViewYRot(partialTick), gloveRight);
    }
}
