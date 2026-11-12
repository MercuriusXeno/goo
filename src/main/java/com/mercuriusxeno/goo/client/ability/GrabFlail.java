package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import java.util.List;

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

    /**
     * Whether a Grab holds an entity now, as this client sees its ailment.
     *
     * @param entity the entity
     * @return true while a standing Grab holds it
     */
    public static boolean grabbed(Entity entity) {
        return flails(MobAilments.CLIENT.ailmentsOf(entity.getId(), entity.level().getGameTime()));
    }

    /**
     * Whether an entity wearing these ailments flails: while Grab's ailment
     * stands, not while its overlay fades after the release.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     *
     * @param worn the ailments the entity wears
     * @return true while a standing Grab holds it
     */
    static boolean flails(List<MobAilments.Worn> worn) {
        return worn.stream().anyMatch(ailment -> ailment.kind() == AilmentKind.GRABBED && ailment.stands());
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
