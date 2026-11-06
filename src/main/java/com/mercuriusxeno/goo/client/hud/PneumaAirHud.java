package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.typhoon.Breath;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the air Pneuma banks as a row of tinted air bubbles above vanilla's
 * air bubbles, the way reserve hearts stand beside the health bar: each
 * bubble is a tenth of the reserve's cap, and the row fills from the right as
 * vanilla's does (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PneumaAirHud {

    private static final Identifier AIR_BUBBLE = Identifier.withDefaultNamespace("hud/air");
    /** Pneuma's pale wind tint over vanilla's bubble. */
    private static final int RESERVE_TINT = 0xFF9FE8FF;
    private static final int BUBBLES_PER_ROW = 10;
    private static final int BUBBLE_SIZE = 9;
    private static final int BUBBLE_SPACING = 8;
    private static final int ROW_HEIGHT = 10;
    private static final int BAR_HALF_WIDTH = 91;
    private static final int HALF = 2;

    private PneumaAirHud() {
    }

    /**
     * Wraps the vanilla air layer so the reserve row draws above its bubbles.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.AIR_LEVEL, PneumaAirHud::aboveAir);
    }

    private static GuiLayer aboveAir(GuiLayer vanilla) {
        return (graphics, deltaTracker) -> {
            vanilla.render(graphics, deltaTracker);
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
                int bubbles = reserveBubbles(player.getData(GooAttachments.BREATH));
                if (bubbles > 0) {
                    paintReserve(graphics, bubbles, mc.gui.rightHeight);
                    mc.gui.rightHeight += ROW_HEIGHT;
                }
            }
        };
    }

    private static void paintReserve(GuiGraphicsExtractor graphics, int bubbles, int rightHeight) {
        int xRight = graphics.guiWidth() / HALF + BAR_HALF_WIDTH;
        int y = graphics.guiHeight() - rightHeight;
        for (int bubble = 0; bubble < bubbles; bubble++) {
            int x = xRight - bubble * BUBBLE_SPACING - BUBBLE_SIZE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, AIR_BUBBLE, x, y, BUBBLE_SIZE, BUBBLE_SIZE, RESERVE_TINT);
        }
    }

    /**
     * The bubbles the reserve row draws: each a tenth of the cap, a part-filled tenth showing whole.
     *
     * @param breath the player's breath
     * @return the bubbles, 0 to 10
     */
    static int reserveBubbles(Breath breath) {
        if (breath.reserve() <= 0 || breath.reserveCap() <= 0) {
            return 0;
        }
        return Math.min(Mth.positiveCeilDiv(breath.reserve() * BUBBLES_PER_ROW, breath.reserveCap()), BUBBLES_PER_ROW);
    }
}
