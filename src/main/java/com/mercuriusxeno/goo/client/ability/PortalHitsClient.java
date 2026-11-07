package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The client's half of a portal counting as a block for a hit: a hit on one
 * of the player's own portals starts no break on the wall behind it, so no
 * cracks draw there while the server breaks the portal.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalHitsClient {

    private PortalHitsClient() {
    }

    /**
     * Stops a started hit on the player's own portal from breaking the wall.
     *
     * @param event the left click block event
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (startsOnTheClient(event) && hitsOwnPortal(event)) {
            event.setCanceled(true);
        }
    }

    private static boolean startsOnTheClient(PlayerInteractEvent.LeftClickBlock event) {
        return event.getLevel().isClientSide() && event.getFace() != null
                && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START;
    }

    private static boolean hitsOwnPortal(PlayerInteractEvent.LeftClickBlock event) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && ClientPortals.portals().stream().anyMatch(portal -> portal.face() == event.getFace()
                && portal.block().dimension().equals(mc.level.dimension()) && portal.covers(event.getPos()));
    }
}
