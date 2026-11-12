package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
 */
public final class CameraEye {

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
