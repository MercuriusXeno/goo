package com.mercuriusxeno.goo.ability.nether;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The step an inverted body takes along its ceiling, vanilla's step up
 * mirrored: walking into a block hanging from the ceiling within its step
 * height, the body steps down onto that block's underside and walks on, as an
 * upright body steps up onto a slab.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
public final class CeilingStep {

    /** Vanilla's reach past a resting body's ground for the colliders a step reads. */
    private static final double GROUND_REACH = 1.0E-5;

    private CeilingStep() {
    }

    /**
     * The move an inverted body makes once its step is taken: the collided
     * move where no step carries it farther, else the stepped move.
     *
     * @param body     the inverted body
     * @param asked    the move asked for
     * @param collided the move vanilla's collision left, with no step taken
     * @return the move to make
     */
    public static Vec3 step(Entity body, Vec3 asked, Vec3 collided) {
        boolean landedOnTheCeiling = asked.y != collided.y && asked.y > 0;
        if (!stepsAt(body, asked, collided, landedOnTheCeiling)) {
            return collided;
        }
        AABB box = body.getBoundingBox();
        AABB grounded = landedOnTheCeiling ? box.move(0, collided.y, 0) : box;
        Vec3 stepped = firstFartherStep(body, asked, collided, grounded, landedOnTheCeiling);
        return stepped == null ? collided : stepped.add(0, grounded.minY - box.minY, 0);
    }

    /**
     * Answers whether a move asks for a step: the body steps, a block stopped
     * it sideways, and it stands on its ceiling or just landed there.
     *
     * @param body               the inverted body
     * @param asked              the move asked for
     * @param collided           the move vanilla's collision left
     * @param landedOnTheCeiling whether the move landed the body on its ceiling
     * @return true where the move asks for a step
     */
    private static boolean stepsAt(Entity body, Vec3 asked, Vec3 collided, boolean landedOnTheCeiling) {
        boolean blockedSideways = asked.x != collided.x || asked.z != collided.z;
        return body.maxUpStep() > 0 && blockedSideways && (landedOnTheCeiling || body.onGround());
    }

    /**
     * The first step down, shortest first, that carries the body farther
     * sideways than the collided move, or null where none does.
     *
     * @param body               the inverted body
     * @param asked              the move asked for
     * @param collided           the move vanilla's collision left
     * @param grounded           the body's box resting on its ceiling
     * @param landedOnTheCeiling whether the move landed the body on its ceiling
     * @return the stepped move from the grounded box, or null
     */
    private static @Nullable Vec3 firstFartherStep(Entity body, Vec3 asked, Vec3 collided, AABB grounded,
                                                    boolean landedOnTheCeiling) {
        float maxStep = body.maxUpStep();
        AABB reach = grounded.expandTowards(asked.x, -maxStep, asked.z);
        if (!landedOnTheCeiling) {
            reach = reach.expandTowards(0, GROUND_REACH, 0);
        }
        List<VoxelShape> colliders = Entity.collectAllColliders(body, body.level(), reach);
        float[] heights = CeilingGround.stepHeights(grounded.maxY, colliders.stream()
                .flatMapToDouble(shape -> shape.getCoords(Direction.Axis.Y).doubleStream()), maxStep, (float) -collided.y);
        for (float height : heights) {
            Vec3 stepped = Entity.collideBoundingBox(body, new Vec3(asked.x, -height, asked.z), grounded, body.level(),
                    List.of());
            if (stepped.horizontalDistanceSqr() > collided.horizontalDistanceSqr()) {
                return stepped;
            }
        }
        return null;
    }
}
