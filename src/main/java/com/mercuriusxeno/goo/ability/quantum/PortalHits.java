package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A portal counts as a block for a hit: hitting a portal's face breaks the
 * portal, and the hit never reaches the wall behind it.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalHits {

    private PortalHits() {
    }

    /**
     * Breaks the portal a started hit lands on, in place of the block behind it.
     *
     * @param event the left click block event
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || event.getFace() == null) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && PortalChoice.breakAt(level, event.getPos(),
                event.getFace())) {
            event.setCanceled(true);
        }
    }
}
