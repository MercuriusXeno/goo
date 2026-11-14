package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The eye and look a visual drawn at a caster's eye is placed from. The local
 * player's entity eye steps to crouch height the tick the pose changes, while
 * the main camera eases its eye height over several frames, so in first person
 * the local player's visual takes the camera's eye and moves with the view.
 * Another caster, and the local player in third person, keep the entity eye,
 * since that body does drop at once.
 * decision diagnose-and-correct-eye-drawn-visuals-jumping-on-sneak
 * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
 * decision diagnose-and-correct-body-height-visuals-jumping-on-sneak
 */
public final class CameraEye {

    /**
     * The share of a standing player's height its eye stands at, 1.62 of 1.8,
     * so the camera's eye above the feet scales up to a body height that
     * reads the standing height exactly and eases on down with the camera.
     */
    static final double EYE_SHARE_OF_STANDING_HEIGHT = 0.9;

    /**
     * An eye and the look out of it.
     *
     * @param eye  the world point the eye stands at
     * @param look the unit look
     */
    public record Sight(Vec3 eye, Vec3 look) {
    }

    /**
     * Clips a sight line against the world's blocks.
     */
    @FunctionalInterface
    public interface ViewClip {

        /**
         * Clips the line.
         *
         * @param from the line's start
         * @param to   the line's end
         * @return the clip result, a miss at the end in open air
         */
        BlockHitResult clip(Vec3 from, Vec3 to);
    }

    private CameraEye() {
    }

    /**
     * The blocks an entity sees: outlines clipped, fluids passed through, the entity ignored.
     *
     * @param viewer the entity the line is traced for
     * @return the clip
     */
    public static ViewClip blocksSeenBy(Entity viewer) {
        return (from, to) -> viewer.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, viewer));
    }

    /**
     * The sight a caster's eye visual is placed from this frame.
     *
     * @param mc          the client
     * @param caster      the caster the visual is drawn at
     * @param partialTick the frame's partial tick
     * @return the camera's sight for the local player in first person, else the entity's
     */
    public static Sight of(Minecraft mc, Entity caster, float partialTick) {
        return chosen(followsTheCamera(mc, caster), cameraSight(mc.gameRenderer.getMainCamera()),
                entitySight(caster, partialTick));
    }

    /**
     * Whether a caster's visuals follow the main camera: the local player seen in first person.
     *
     * @param mc     the client
     * @param caster the caster
     * @return whether the camera stands at the caster's eye
     */
    public static boolean followsTheCamera(Minecraft mc, Entity caster) {
        return caster == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /**
     * The caster's own eye and look, which step with its pose.
     *
     * @param caster      the caster
     * @param partialTick the frame's partial tick
     * @return the entity's sight
     */
    public static Sight entitySight(Entity caster, float partialTick) {
        return new Sight(caster.getEyePosition(partialTick), caster.getViewVector(partialTick));
    }

    private static Sight cameraSight(Camera camera) {
        return new Sight(camera.position(), new Vec3(camera.forwardVector()));
    }

    /**
     * The height a visual placed from a body's height reads this frame: the
     * camera's eased eye above the feet, scaled up to a body height, for the
     * local player in first person, else the body's own height.
     * decision diagnose-and-correct-body-height-visuals-jumping-on-sneak
     *
     * @param mc          the client
     * @param body        the body the visual is placed on
     * @param partialTick the frame's partial tick
     * @return the height, in blocks
     */
    public static double bodyHeight(Minecraft mc, Entity body, float partialTick) {
        double cameraAboveFeet = mc.gameRenderer.getMainCamera().position().y - body.getPosition(partialTick).y;
        return bodyHeight(followsTheCamera(mc, body), cameraAboveFeet, body.getBbHeight());
    }

    /**
     * The height a visual placed from body height reads, from the camera's
     * eye above the feet or the body's own height.
     *
     * @param followsTheCamera whether the body is the local player seen in first person
     * @param cameraAboveFeet  how far the camera's eye stands above the feet, in blocks
     * @param bodyHeight       the body's own height, which steps with its pose
     * @return the height, in blocks
     */
    static double bodyHeight(boolean followsTheCamera, double cameraAboveFeet, double bodyHeight) {
        return followsTheCamera ? cameraAboveFeet / EYE_SHARE_OF_STANDING_HEIGHT : bodyHeight;
    }

    /**
     * The eye height above the feet a visual placed relative to a caster's
     * feet starts at this frame: the camera's eased eye height for the local
     * player in first person, else the caster's own. The camera's eye height
     * is read off the camera rather than its position, so a caller on a game
     * tick, where the feet have moved on since the last frame, reads it whole.
     * decision diagnose-and-correct-eye-drawn-visuals-jumping-on-sneak
     *
     * @param mc          the client
     * @param caster      the caster the visual is placed on
     * @param partialTick the frame's partial tick
     * @return the eye height, in blocks above the feet
     */
    public static double eyeHeight(Minecraft mc, Entity caster, float partialTick) {
        Camera camera = mc.gameRenderer.getMainCamera();
        return eyeHeight(followsTheCamera(mc, caster), Mth.lerp(partialTick, camera.eyeHeightOld, camera.eyeHeight),
                caster.getEyeHeight());
    }

    /**
     * The eye height a visual placed relative to the feet starts at, from the
     * camera's eased eye height or the entity's own.
     *
     * @param followsTheCamera whether the caster is the local player seen in first person
     * @param cameraEyeHeight  the camera's eased eye height above the feet, in blocks
     * @param entityEyeHeight  the entity's eye height, which steps with its pose
     * @return the eye height, in blocks
     */
    static double eyeHeight(boolean followsTheCamera, double cameraEyeHeight, double entityEyeHeight) {
        return followsTheCamera ? cameraEyeHeight : entityEyeHeight;
    }

    /**
     * Which of two sights a visual is placed from.
     *
     * @param localFirstPerson whether the caster is the local player seen in first person
     * @param camera           the main camera's sight
     * @param entity           the caster's own sight
     * @return the sight the visual is placed from
     */
    static Sight chosen(boolean localFirstPerson, Sight camera, Sight entity) {
        return localFirstPerson ? camera : entity;
    }
}
