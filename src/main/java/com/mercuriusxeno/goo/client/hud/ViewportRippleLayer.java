package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.ability.ViewportRipples;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the local player's viewport ripples in first person: each frame
 * rectangle as four edge bars at its inset, in the goo type's color at its
 * fade, over the camera's overlays and under the HUD.
 * Decision viewport-frame-ripples-on-blink.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ViewportRippleLayer {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "viewport_ripple");

    /** A frame bar's thickness, as a share of the screen's shorter side. */
    private static final float BAR_SHARE = 0.01f;

    private ViewportRippleLayer() {
    }

    /**
     * Registers the layer above the camera's overlays.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER_ID, ViewportRippleLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // In third person the blinker sees the 3D ripple around their body, so the frame copy is first person's alone.
        if (mc.level == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        float gameTime = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int shorter = Math.min(width, height);
        int bar = Math.max(1, Math.round(shorter * BAR_SHARE));
        for (Afterimages.Afterimage<Void> ripple : ViewportRipples.CLIENT.live(mc.level.getGameTime())) {
            for (ViewportRipples.FramePulse pulse : ViewportRipples.framePulses(ripple, gameTime)) {
                int inset = Math.round(pulse.inset() * shorter);
                drawFrame(graphics, inset, bar, width, height, ARGB.color(pulse.alpha(), ripple.rgb()));
            }
        }
    }

    /**
     * Draws one frame rectangle as four bars.
     *
     * @param graphics the GUI graphics
     * @param inset    pixels in from the screen edge
     * @param bar      the bars' thickness in pixels
     * @param width    the screen's width
     * @param height   the screen's height
     * @param color    the ARGB color
     */
    private static void drawFrame(GuiGraphicsExtractor graphics, int inset, int bar, int width, int height,
            int color) {
        int left = inset;
        int top = inset;
        int right = width - inset;
        int bottom = height - inset;
        graphics.fill(left, top, right, top + bar, color);
        graphics.fill(left, bottom - bar, right, bottom, color);
        graphics.fill(left, top + bar, left + bar, bottom - bar, color);
        graphics.fill(right - bar, top + bar, right, bottom - bar, color);
    }
}
