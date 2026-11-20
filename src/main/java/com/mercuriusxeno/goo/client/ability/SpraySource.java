package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.model.FirstPersonSwing;
import com.mercuriusxeno.goo.item.GooCasters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Where a lifted blob outside the centered abilities and Bore stands, and so
 * where a Goop or Slick spray's drips appear: a still place in the caster's
 * view straight above where the glove's palm rests, never bobbing or swinging
 * with the hand. The local first-person player's stands above the palm the
 * hand's pass last drew, its bob and swing taken out, so it stays over the
 * hand at any field of view; every other caster's stands where the default
 * field of view puts it, above the palm at vanilla's place for the hand.
 * goop-and-slick-spray-emanates-from-the-glove
 * palm-blob-lifts-as-the-spray-source
 */
public final class SpraySource {

    /**
     * Where the first-person glove's palm rests in the view for a glove in
     * the right hand, no ability posing the hand: ItemInHandRenderer's arm
     * place (0.56, -0.52, -0.72), then goo_glove.json's first-person display
     * turn and scale, carrying the resting palm blob's center
     * (GloveSpecialRenderer.palmCenter) to here. A left-hand glove mirrors it across.
     */
    static final Vector3fc RESTING_PALM_IN_VIEW = new Vector3f(0.56f, -0.37104167f, -0.7159375f);
    /**
     * How far up the view the lifted blob stands above the resting palm: the
     * height today's glove rest, 0.15 below the look, stood above that palm
     * at the default field of view, for the operator to judge at the playtest.
     */
    public static final double RISE_ABOVE_THE_PALM = 0.22104167;
    /** A quarter turn, in degrees. */
    private static final float QUARTER_TURN = 90f;

    /** Where the local first-person glove's palm last rested in the view, unbobbed and unswung. */
    private static volatile @Nullable Vector3fc restingPalmInView;

    private SpraySource() {
    }

    /**
     * The source in a view at the default field of view: the rise straight
     * above the palm at vanilla's place for the hand, carried by the view's
     * look, up and right.
     *
     * @param eye        the viewer's eye
     * @param xRot       the view's pitch in degrees, negative looking up
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @return the source
     */
    static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight) {
        return inView(eye, xRot, yRot, gloveRight, RISE_ABOVE_THE_PALM);
    }

    /**
     * The point a rise straight above the palm stands at in a view at the
     * default field of view, at vanilla's place for the hand.
     *
     * @param eye        the viewer's eye
     * @param xRot       the view's pitch in degrees, negative looking up
     * @param yRot       the view's yaw in degrees
     * @param gloveRight whether the glove is in the right hand
     * @param rise       how far up the view above the palm, in blocks
     * @return the point
     */
    static Vec3 inView(Vec3 eye, float xRot, float yRot, boolean gloveRight, double rise) {
        Vector3fc palm = palmAtVanillasPlace(gloveRight);
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = Vec3.directionFromRotation(xRot - QUARTER_TURN, yRot);
        Vec3 right = Vec3.directionFromRotation(0f, yRot + QUARTER_TURN);
        return eye.add(right.scale(palm.x())).add(up.scale(palm.y() + rise))
                .subtract(ahead.scale(palm.z()));
    }

    private static Vector3fc palmAtVanillasPlace(boolean gloveRight) {
        return gloveRight ? RESTING_PALM_IN_VIEW
                : new Vector3f(-RESTING_PALM_IN_VIEW.x(), RESTING_PALM_IN_VIEW.y(), RESTING_PALM_IN_VIEW.z());
    }

    /**
     * Records where the local first-person glove's palm rests as the hand's
     * pass draws it: the swing and the frame's bob taken out, so the blob
     * above it stands still while the hand swings and bobs.
     *
     * @param gloveToHandPass the glove's model onto the hand pass's camera-relative world axes
     * @param palmInModel     the resting palm blob's center in the glove's model
     */
    public static void recordRestingPalm(Matrix4fc gloveToHandPass, Vector3fc palmInModel) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f unswung = FirstPersonSwing.unswung(gloveToHandPass).transformPosition(palmInModel, new Vector3f());
        restingPalmInView = HandPassView.intoTheStillView(new Vec3(unswung),
                mc.gameRenderer.mainCamera().rotation(), HandPassView.frameBob(mc));
    }

    /** Forgets the recorded palm, as a test's end does. */
    static void forgetRestingPalm() {
        restingPalmInView = null;
    }

    /**
     * The local first-person player's source in the hand pass: the rise up
     * the view above the resting palm, or above the palm at vanilla's place
     * before the glove has drawn.
     *
     * @param cameraRotation the camera's rotation, from its view onto the world
     * @param gloveRight     whether the glove is in the right hand
     * @return the source in the hand pass's camera-relative world axes
     */
    static Vec3 aboveTheRestingPalmInHand(Quaternionfc cameraRotation, boolean gloveRight) {
        return aboveTheRestingPalmInHand(cameraRotation, gloveRight, RISE_ABOVE_THE_PALM);
    }

    private static Vec3 aboveTheRestingPalmInHand(Quaternionfc cameraRotation, boolean gloveRight, double rise) {
        Vector3fc recorded = restingPalmInView;
        Vector3fc palm = recorded == null ? palmAtVanillasPlace(gloveRight) : recorded;
        return HandPassView.outOfTheStillView(new Vector3f(palm).add(0f, (float) rise, 0f),
                cameraRotation);
    }

    /**
     * The caster's spray source this frame: above the resting palm the hand's
     * pass draws, carried into the level, for the local player in first
     * person, in the caster's own view at the default field of view
     * otherwise, and the glove the server sent for a caster this client does not hold.
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the server sent with the stream's tick
     * @param partialTick the frame's partial tick
     * @return the source
     */
    public static Vec3 of(int casterId, Vec3 sentGlove, float partialTick) {
        return of(casterId, sentGlove, partialTick, RISE_ABOVE_THE_PALM);
    }

    /**
     * A point a rise straight above the caster's resting palm this frame,
     * carried as {@link #of(int, Vec3, float)} carries the spray source.
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the server sent with the stream's tick
     * @param partialTick the frame's partial tick
     * @param rise        how far up the view above the palm, in blocks
     * @return the point
     */
    static Vec3 of(int casterId, Vec3 sentGlove, float partialTick, double rise) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return sentGlove;
        }
        boolean gloveRight = GooCasters.casterOnTheRight(caster);
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera camera = mc.gameRenderer.mainCamera();
            return camera.position().add(HandPassView.fromHandPass(aboveTheRestingPalmInHand(camera.rotation(),
                    gloveRight, rise), camera.rotation(), camera.getFov(), HandPassView.frameBob(mc)));
        }
        return inView(caster.getEyePosition(partialTick), caster.getViewXRot(partialTick),
                caster.getViewYRot(partialTick), gloveRight, rise);
    }
}
