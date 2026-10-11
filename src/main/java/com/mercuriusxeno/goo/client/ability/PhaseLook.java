package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;

/**
 * How a phased entity draws: its body through the phase render type, grey
 * and translucent, in place of its own. The flag rides the render state,
 * stamped from the phase the server synced.
 * phase-shares-a-plane-between-the-phased
 */
public final class PhaseLook {

    /** The render data marking an entity out of phase this frame. */
    public static final ContextKey<Boolean> PHASED =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "phased"));

    /** The body's alpha while phased, out of 255: under half there. */
    static final int PHASED_ALPHA = 110;

    private PhaseLook() {
    }

    /**
     * Stamps whether the entity stands out of phase onto its render state.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampPhase(Entity entity, EntityRenderState state) {
        state.setRenderData(PHASED, entity.hasData(GooAttachments.OUT_OF_PHASE)
                && entity.getData(GooAttachments.OUT_OF_PHASE).standsAt(entity.level().getGameTime()));
    }

    /**
     * Whether a render state was stamped out of phase.
     *
     * @param state the render state
     * @return true for a phased entity
     */
    public static boolean isPhased(EntityRenderState state) {
        return state.getRenderDataOrDefault(PHASED, Boolean.FALSE);
    }

    /**
     * The render type a phased body draws through, over the skin its own type read.
     *
     * @param skin the entity's texture
     * @return the phase render type
     */
    public static RenderType bodyType(Identifier skin) {
        return GooRenderTypes.gooPhase(skin);
    }

    /**
     * The tint a phased body draws under: its own color at the phase alpha,
     * or lower where it was already fainter.
     *
     * @param tint the body's own ARGB tint
     * @return the tint with the phase alpha
     */
    public static int bodyTint(int tint) {
        return ARGB.color(Math.min(ARGB.alpha(tint), PHASED_ALPHA), tint);
    }
}
