package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Wires the starfield cover into the client: the screen shown for a change
 * into the pocket, the starfield drawn over the view from the envelop until
 * that screen opens, the wait for a change that never came, and the cover
 * let go on leaving the server.
 * decision doorway-starfield-fills-the-view-until-the-room-is-ready
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class EntryCoverHooks {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "entry_cover");

    private EntryCoverHooks() {
    }

    /**
     * Shows the starfield cover for a change into the pocket while the entry holds it.
     *
     * @param event the transition screen registration event
     */
    @SubscribeEvent
    public static void onRegisterTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        event.registerIncomingEffect(OutsiderRooms.POCKET,
                new EntryScreens(EntryCover.LOCAL, StarfieldCoverScreen::new, LevelLoadingScreen::new));
    }

    /**
     * Registers the starfield drawn over everything while the cover stands with no screen open.
     *
     * @param event the layer registration event
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, EntryCoverHooks::drawCover);
    }

    /**
     * Counts the cover's wait for the change.
     *
     * @param event the client post-tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EntryCover.LOCAL.tick();
    }

    /**
     * Lets the cover go when the client leaves the server.
     *
     * @param event the logging-out event
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EntryCover.LOCAL.clear();
    }

    private static void drawCover(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!EntryCover.LOCAL.covering() || mc.screen != null) {
            return;
        }
        TextureManager textures = mc.getTextureManager();
        AbstractTexture sky = textures.getTexture(AbstractEndPortalRenderer.END_SKY_LOCATION);
        AbstractTexture portal = textures.getTexture(AbstractEndPortalRenderer.END_PORTAL_LOCATION);
        TextureSetup setup = TextureSetup.doubleTexture(sky.getTextureView(), sky.getSampler(),
                portal.getTextureView(), portal.getSampler());
        graphics.fill(RenderPipelines.END_PORTAL, setup, 0, 0, graphics.guiWidth(), graphics.guiHeight());
    }
}
