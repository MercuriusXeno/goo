package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.SculkerVision;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * While the local player's Sculker stands, a thin network of sculk veins
 * spreads in from clumps along the screen's edges through a dark teal mist,
 * easing in and out with the vision itself.
 * sculker-sight-persists-where-echoes-have-touched
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerVignetteLayer {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sculker_vignette");
    /** The mist's deep sculk teal. */
    private static final int MIST_RGB = 0x0A3A40;
    /**
     * The veins' teal, about a third of the way from the mist toward sculk teal, so they sit inside the haze.
     * decision sculker-tendrils-blend-into-the-teal-haze
     */
    private static final int VEIN_RGB = 0x117173;
    static final TendrilVignette.Look LOOK = new TendrilVignette.Look(MIST_RGB, VEIN_RGB);
    /** This sight's own vein network, sprouting from a seed of its own. */
    private static final TendrilVignette VIGNETTE = TendrilVignette.veined(0x5C01_C3E5L);
    /**
     * The profiler section the vignette's draw reports its frame time under.
     * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
     */
    private static final String VIGNETTE_SECTION = "goo_sculker_vignette";

    private SculkerVignetteLayer() {
    }

    /**
     * Registers the layer above the camera's overlays.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER_ID, SculkerVignetteLayer::render);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        float time = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
        ProfilerFiller profiler = Profiler.get();
        profiler.push(VIGNETTE_SECTION);
        VIGNETTE.draw(graphics, time, SculkerVision.presence(), LOOK);
        profiler.pop();
    }
}
