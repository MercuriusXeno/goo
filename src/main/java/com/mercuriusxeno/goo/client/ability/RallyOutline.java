package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Outlines every rallied mob in red, its brightness pulsing, through
 * vanilla's glow outline. The glove's aim outline is stamped after this one,
 * so it overrides the red while the player aims at the mob.
 * rallied-mobs-wear-a-red-clenched-fist
 */
public final class RallyOutline {

    /** Ticks per pulse, one and a half seconds. */
    static final float PULSE_PERIOD = 30f;
    /** The red channel at the pulse's dimmest. */
    private static final int DIM_RED = 0x70;
    /** The red channel at the pulse's brightest. */
    private static final int BRIGHT_RED = 0xFF;
    private static final float HALF = 0.5f;

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
     * The outline's color at a game time: opaque red, brightest at the
     * start of each pulse and dimmest halfway through it.
     *
     * @param gameTime the game time including the partial tick
     * @return the ARGB color
     */
    static int color(float gameTime) {
        float brightness = HALF + HALF * Mth.cos(Mth.TWO_PI * gameTime / PULSE_PERIOD);
        return ARGB.color(BRIGHT_RED, Math.round(Mth.lerp(brightness, DIM_RED, BRIGHT_RED)), 0, 0);
    }
}
