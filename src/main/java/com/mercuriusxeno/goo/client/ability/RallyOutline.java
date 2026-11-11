package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Outlines every rallied mob in the pulsing red outline. The ailment
 * outlines and the glove's aim outline are stamped after this one, so they
 * override the red while they stand.
 * rallied-mobs-wear-a-red-clenched-fist
 */
public final class RallyOutline {

    /** Ticks per pulse, the pulsing outline's. */
    static final float PULSE_PERIOD = PulsingOutline.PULSE_PERIOD;
    /** Rally's red. */
    private static final int RALLY_RED = 0xFF0000;

    private RallyOutline() {
    }

    /**
     * Render state modifier callback: outlines a rallied mob in the pulse's
     * red this frame. Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void stampOutline(Entity entity, EntityRenderState state) {
        if (entity.hasData(GooAttachments.RALLIED)) {
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
        return PulsingOutline.color(RALLY_RED, gameTime);
    }
}
