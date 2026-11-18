package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Outlines every led mob in the pulsing red outline. The ailment
 * outlines and the glove's aim outline are stamped after this one, so they
 * override the red while they stand.
 * rallied-mobs-wear-a-red-clenched-fist
 */
public final class LeadOutline {

    /** Ticks per pulse, the pulsing outline's. */
    static final float PULSE_PERIOD = PulsingOutline.PULSE_PERIOD;
    /** Lead's red. */
    private static final int LEAD_RED = 0xFF0000;

    private LeadOutline() {
    }

    /**
     * Render state modifier callback: outlines a led mob in the pulse's
     * red this frame. Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void stampOutline(Entity entity, EntityRenderState state) {
        if (entity.hasData(GooAttachments.LED)) {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            state.outlineColor = color(entity.level().getGameTime() + partialTick);
        }
    }

    /**
     * The outline's color at a game time: the pulsing outline in opaque red.
     *
     * @param gameTime the game time including the partial tick
     * @return the ARGB color
     */
    static int color(float gameTime) {
        return PulsingOutline.color(LEAD_RED, gameTime);
    }
}
