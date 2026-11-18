package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;

/**
 * How a phased entity draws: its body through the phase render type, grey,
 * translucent and faintly outlined, in place of its own, to every viewer.
 * The flag rides the render state, stamped from the phase the server synced.
 * The inventory's paper doll of a phased player draws a readable ghost
 * instead: its own skin, faded, with no grey wash and no outline, which the
 * inventory's flat light spread over the whole body.
 * phase-shares-a-plane-between-the-phased
 * phased-things-draw-as-a-ghostly-image
 */
public final class PhaseLook {

    /** The render data marking an entity out of phase this frame. */
    public static final ContextKey<Boolean> PHASED =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "phased"));
    /** The render data marking a phased body drawn as the inventory's paper doll. */
    public static final ContextKey<Boolean> PAPER_DOLL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "phased_paper_doll"));

    /**
     * The body's alpha while phased, out of 255: about nine tenths there, a
     * ghost still plainly the thing it is (operator: "mobs are too
     * transparent" at 110, and again at 180).
     * phased-things-draw-as-a-ghostly-image
     */
    static final int PHASED_ALPHA = 230;
    /**
     * The phased player's own look's alpha, out of 255: its first-person arm,
     * what it holds and its paper doll, a little over half there, fainter
     * than the bodies the world sees (operator: "make both the player and the
     * held item more transparent").
     * phased-things-draw-as-a-ghostly-image
     */
    static final int OWN_PHASED_ALPHA = 140;

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
     * Marks a render state drawn as the inventory's paper doll, where it is phased.
     * phased-things-draw-as-a-ghostly-image
     *
     * @param state the paper doll's render state
     */
    public static void markPaperDoll(EntityRenderState state) {
        if (isPhased(state)) {
            state.setRenderData(PAPER_DOLL, Boolean.TRUE);
        }
    }

    /**
     * Whether a render state is a phased paper doll.
     *
     * @param state the render state
     * @return true for a phased body drawn in the inventory
     */
    public static boolean isPaperDoll(EntityRenderState state) {
        return state.getRenderDataOrDefault(PAPER_DOLL, Boolean.FALSE);
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
     * The render type a phased body draws through: the phase type, or for the
     * inventory's paper doll vanilla's translucent entity type over its own
     * skin, a readable ghost under the phase alpha.
     *
     * @param state the body's render state
     * @param skin  the entity's texture
     * @return the render type
     */
    public static RenderType bodyType(EntityRenderState state, Identifier skin) {
        return isPaperDoll(state) ? GooSubmitter.translucentOn(skin) : bodyType(skin);
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

    /**
     * The tint the phased player's own look draws under: its color at the
     * player's own, fainter alpha, or lower where it was already fainter.
     *
     * @param tint the own ARGB tint
     * @return the tint with the player's own phase alpha
     */
    public static int ownTint(int tint) {
        return ARGB.color(Math.min(ARGB.alpha(tint), OWN_PHASED_ALPHA), tint);
    }

    /**
     * The tint a phased body draws under: the player's own fainter tint for
     * its paper doll, the body tint for everything the world sees.
     *
     * @param state the body's render state
     * @param tint  the body's own ARGB tint
     * @return the tint with the phase alpha
     */
    public static int bodyTint(EntityRenderState state, int tint) {
        return isPaperDoll(state) ? ownTint(tint) : bodyTint(tint);
    }
}
