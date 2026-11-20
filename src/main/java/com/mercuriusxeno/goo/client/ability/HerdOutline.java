package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Outlines every mob Herd senses in Scry's pulsing outline in Yarn's color.
 * Lead's outline, the ailment outlines and the glove's aim outline are
 * stamped after this one, so they override Yarn's color while they stand.
 * leader-outlines-every-mob-through-walls
 */
public final class HerdOutline {

    private HerdOutline() {
    }

    /**
     * Render state modifier callback: outlines a sensed mob in the pulsing
     * outline in Yarn's color this frame. Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void stampOutline(Entity entity, EntityRenderState state) {
        if (HerdSense.CLIENT.appearsGlowing(entity.getId())) {
            float time = entity.level().getGameTime()
                    + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            state.outlineColor = color(ClientGooTypes.color(GooTypes.YARN), time, HerdSense.CLIENT.strength(time));
        }
    }

    /**
     * The outline's color at a game time: the pulsing outline in Yarn's
     * color, its alpha the sensed set's strength.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     *
     * @param yarnRgb   Yarn's color, 0xRRGGBB
     * @param gameTime the game time including the partial tick
     * @param strength the sensed set's strength, zero to one
     * @return the ARGB color
     */
    static int color(int yarnRgb, float gameTime, float strength) {
        return Afterglow.fadeArgb(PulsingOutline.color(yarnRgb, gameTime), strength);
    }
}
