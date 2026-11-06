package com.mercuriusxeno.goo.client.pocket;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pocket.PocketMenu;
import com.mercuriusxeno.goo.network.PocketContentsPayload;
import com.mercuriusxeno.goo.registry.GooMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Binds Pocket's menu to its screen and shows the entries the server sends
 * (decision pocket-is-a-void-reserving-container-with-scroll-and-search).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PocketScreens {

    private PocketScreens() {
    }

    /**
     * Registers Pocket's screen for its menu.
     *
     * @param event the menu screen registration event
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(GooMenus.POCKET.get(), PocketScreen::new);
    }

    /**
     * Shows the sent entries on the pocket the player has open, if it is the one they were sent for.
     *
     * @param payload the pocket's entries
     * @param context the network context
     */
    public static void handleContents(PocketContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.containerMenu instanceof PocketMenu pocket
                    && pocket.containerId == payload.containerId()) {
                pocket.showEntries(payload.entries());
            }
        });
    }
}
