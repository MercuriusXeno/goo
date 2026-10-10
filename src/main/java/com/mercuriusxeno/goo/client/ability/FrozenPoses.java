package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a frozen mob's pose still on this client: the frame its gauge
 * reads full, its limb swing, its idle animation time and its facing are
 * kept, and every frame after draws them until it thaws below full.
 * frozen-gauge-per-mob-encases-when-full
 */
public final class FrozenPoses {

    /** The client's held poses. */
    public static final FrozenPoses CLIENT = new FrozenPoses();

    /** The pose each frozen entity holds, by entity id. */
    private final Map<Integer, Pose> held = new HashMap<>();

    /**
     * The parts of a living render state that move a model while it stands.
     *
     * @param walkAnimationPos   the limb swing's phase
     * @param walkAnimationSpeed the limb swing's reach
     * @param ageInTicks         the idle animation's time
     * @param bodyRot            the body's facing
     * @param yRot               the head's yaw
     * @param xRot               the head's pitch
     */
    record Pose(float walkAnimationPos, float walkAnimationSpeed, float ageInTicks, float bodyRot, float yRot,
                float xRot) {

        static Pose of(LivingEntityRenderState state) {
            return new Pose(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.bodyRot,
                    state.yRot, state.xRot);
        }

        void applyTo(LivingEntityRenderState state) {
            state.walkAnimationPos = walkAnimationPos;
            state.walkAnimationSpeed = walkAnimationSpeed;
            state.ageInTicks = ageInTicks;
            state.bodyRot = bodyRot;
            state.yRot = yRot;
            state.xRot = xRot;
        }
    }

    /**
     * Holds an entity's pose on its render state while it stands frozen,
     * read off its synced gauge.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampFrozenPose(Entity entity, EntityRenderState state) {
        if (state instanceof LivingEntityRenderState living) {
            boolean frozen = entity.hasData(GooAttachments.FROZEN) && entity.getData(GooAttachments.FROZEN).full();
            CLIENT.hold(entity.getId(), frozen, living);
        }
    }

    /**
     * Keeps the pose the state shows the first frame an entity reads frozen
     * and draws that pose on every frozen frame after; a thawed entity's
     * pose is let go and its state left as it is.
     *
     * @param entityId the entity's id
     * @param frozen   whether it stands frozen this frame
     * @param state    its render state this frame
     */
    void hold(int entityId, boolean frozen, LivingEntityRenderState state) {
        if (!frozen) {
            held.remove(entityId);
            return;
        }
        held.computeIfAbsent(entityId, id -> Pose.of(state)).applyTo(state);
    }

    /**
     * Lets go of every held pose, as a disconnect does.
     */
    public void clear() {
        held.clear();
    }
}
