package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * While the local player holds fungal sight, the screen's rim fills with a
 * magenta mist, and small tendrils curl in from the edges, undulating. The
 * vignette fades in as the sight starts and out over its last seconds, over
 * the camera's overlays and under the HUD.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SightVignetteLayer {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "sight_vignette");
    /** The mist's magenta. */
    private static final int MIST_RGB = 0xB54FC0;
    /** The tendrils' paler magenta. */
    private static final int TENDRIL_RGB = 0xD77AD9;
    /** Ticks the vignette fades in over. */
    static final float FADE_IN_TICKS = 20f;
    /** Ticks before the sight ends that the vignette starts fading out. */
    static final float FADE_OUT_TICKS = 60f;
    private static final TendrilVignette.Look LOOK = new TendrilVignette.Look(MIST_RGB, TENDRIL_RGB);

    /** Marks that no standing sight has been seen. */
    private static final long UNSEEN = Long.MIN_VALUE;
    /** The game time the standing sight was first seen, or {@link #UNSEEN} while none stands. */
    private static long seenSince = UNSEEN;

    private SightVignetteLayer() {
    }

    /**
     * Registers the layer above the camera's overlays.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, LAYER_ID, SightVignetteLayer::render);
    }

    /**
     * The vignette's strength: rising over its first second, full while the
     * sight stands, and falling over the last three seconds before it ends.
     *
     * @param sinceStart ticks since the sight was first seen
     * @param untilEnd   ticks until the sight ends
     * @return zero to one
     */
    static float fade(float sinceStart, float untilEnd) {
        return Mth.clamp(Math.min(sinceStart / FADE_IN_TICKS, untilEnd / FADE_OUT_TICKS), 0f, 1f);
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        long now = mc.level.getGameTime();
        Sight sight = player.getData(GooAttachments.SIGHT);
        float time = now + deltaTracker.getGameTimeDeltaPartialTick(false);
        float strength = strengthAt(sight, now, time);
        TendrilVignette.draw(graphics, time, strength, LOOK);
    }

    /**
     * The vignette's strength this frame, minding when the sight was first seen.
     *
     * @param sight the local player's sight
     * @param now   the game tick
     * @param time  the game time with the frame's partial tick
     * @return zero to one, zero while no sight stands
     */
    private static float strengthAt(Sight sight, long now, float time) {
        if (!sight.standsAt(now)) {
            seenSince = UNSEEN;
            return 0f;
        }
        if (seenSince == UNSEEN || seenSince > now) {
            seenSince = now;
        }
        return fade(time - seenSince, sight.expiresAt() - time);
    }
}
