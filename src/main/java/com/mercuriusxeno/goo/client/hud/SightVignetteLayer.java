package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.client.fade.Afterglow;
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
 * vignette fades in as the sight starts and out over the second after it
 * ends, over the camera's overlays and under the HUD.
 * sight-lengthens-shift-and-outlines-fungus
 * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
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
    private static final TendrilVignette.Look LOOK = new TendrilVignette.Look(MIST_RGB, TENDRIL_RGB);
    /** This sight's own tendrils, sprouting from a seed of their own. */
    private static final TendrilVignette VIGNETTE = new TendrilVignette(0x5167_A7E5L);

    /** The vignette's rise and fall, read between frames. */
    private static final VignetteFade FADE = new VignetteFade();

    private SightVignetteLayer() {
    }

    /**
     * The vignette's strength over a sight's life: rising over its first
     * second, full through the last tick it stands, and falling over the
     * second after it ends, by the glove or by dry goo,
     * whichever the client sees first of the sight reading gone or passing
     * its expiry. Nothing fades before the end.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     */
    static final class VignetteFade {

        private final Afterglow<Sight> glow = new Afterglow<>();
        private boolean stood;
        private float risingSince;
        private float risen;
        private float shown;

        /**
         * Reads the sight as the client sees it this frame.
         *
         * @param sight the local player's sight
         * @param now   the game tick
         * @param time  the game time with the frame's partial tick
         * @return the vignette's strength, zero to one
         */
        float read(Sight sight, long now, float time) {
            boolean stands = sight.standsAt(now);
            if (stands) {
                if (!stood || risingSince > time) {
                    // a sight starting mid-fade rises from where the fade stood rather than from nothing
                    risingSince = time - shown * FADE_IN_TICKS;
                }
                risen = Mth.clamp((time - risingSince) / FADE_IN_TICKS, 0f, 1f);
            }
            stood = stands;
            shown = risen * glow.read(stands ? sight : null, time).map(Afterglow.Glow::strength).orElse(0f);
            return shown;
        }
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

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        long now = mc.level.getGameTime();
        Sight sight = player.getData(GooAttachments.SIGHT);
        float time = now + deltaTracker.getGameTimeDeltaPartialTick(false);
        float strength = FADE.read(sight, now, time);
        VIGNETTE.draw(graphics, time, strength, LOOK);
    }
}
