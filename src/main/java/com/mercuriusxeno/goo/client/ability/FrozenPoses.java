package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;

/**
 * Freezes a statue's pose: the first frame a mob draws as a statue its pose
 * is kept, and every frame after draws that pose, limbs, head, body and idle
 * motion alike. Petrify's statues freeze here, and Frost's frozen mobs can
 * freeze through the same store (decision petrify-stone-encasement-and-calcify-map).
 */
public final class FrozenPoses {

    /** The client's frozen poses. */
    public static final FrozenPoses CLIENT = new FrozenPoses();

    private final Map<Integer, Pose> poses = new HashMap<>();

    /**
     * The parts of a living render state a frozen pose holds.
     *
     * @param bodyRot     the body's yaw
     * @param yRot        the head's yaw
     * @param xRot        the head's pitch
     * @param walkPos     the limb swing's position
     * @param walkSpeed   the limb swing's speed
     * @param ageInTicks  the age idle motion reads
     */
    record Pose(float bodyRot, float yRot, float xRot, float walkPos, float walkSpeed, float ageInTicks) {

        static Pose of(LivingEntityRenderState state) {
            return new Pose(state.bodyRot, state.yRot, state.xRot, state.walkAnimationPos,
                    state.walkAnimationSpeed, state.ageInTicks);
        }

        void applyTo(LivingEntityRenderState state) {
            state.bodyRot = bodyRot;
            state.yRot = yRot;
            state.xRot = xRot;
            state.walkAnimationPos = walkPos;
            state.walkAnimationSpeed = walkSpeed;
            state.ageInTicks = ageInTicks;
        }
    }

    /**
     * Holds an entity's pose while it stands frozen and lets it go when it no
     * longer does.
     *
     * @param entityId the entity's id
     * @param frozen   whether the entity stands frozen
     * @param state    its render state this frame
     */
    void hold(int entityId, boolean frozen, LivingEntityRenderState state) {
        if (!frozen) {
            poses.remove(entityId);
            return;
        }
        poses.computeIfAbsent(entityId, id -> Pose.of(state)).applyTo(state);
    }

    /**
     * Freezes a statue's render state, as the render state modifier runs it.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void freezeStatue(Entity entity, EntityRenderState state) {
        if (state instanceof LivingEntityRenderState living) {
            boolean statue = entity.hasData(GooAttachments.PETRIFICATION)
                    && entity.getData(GooAttachments.PETRIFICATION).statue();
            CLIENT.hold(entity.getId(), statue, living);
        }
    }

    /** Drops every pose, as a disconnect does. */
    public void clear() {
        poses.clear();
    }
}
