package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Flails a held mob's limbs on this client: while it wears Grab's ailment,
 * its limb swing runs fast at full reach off its own animation time, so the
 * mob kicks in the air however still it hangs.
 * grab-holds-and-throws-a-physics-body
 */
public final class GrabFlail {

    /** How fast the flail swings, in limb swing phase per tick of animation time. */
    static final float FLAIL_RATE = 1.6f;
    /** The flail's reach, the full swing of a sprint. */
    static final float FLAIL_REACH = 1f;

    private GrabFlail() {
    }

    /**
     * Stamps the flail onto an entity's render state while it wears Grab's ailment.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampFlail(Entity entity, EntityRenderState state) {
        if (state instanceof LivingEntityRenderState living && grabbed(entity)) {
            flail(living);
        }
    }

    private static boolean grabbed(Entity entity) {
        return MobAilments.CLIENT.ailmentsOf(entity.getId(), entity.level().getGameTime()).stream()
                .anyMatch(worn -> worn.kind() == AilmentKind.GRABBED);
    }

    /**
     * Swings a state's limbs as a flail off its animation time.
     *
     * @param state the render state
     */
    static void flail(LivingEntityRenderState state) {
        state.walkAnimationPos = state.ageInTicks * FLAIL_RATE;
        state.walkAnimationSpeed = FLAIL_REACH;
    }
}
