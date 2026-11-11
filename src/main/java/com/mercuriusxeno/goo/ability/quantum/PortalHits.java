package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A portal, a tunnel's mouth and a portable hole's mouth count as a block for
 * a hit: hitting a portal's face breaks the portal, hitting a mouth dismisses
 * its hole, and the hit never reaches the block behind any of them.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 * decision punching-the-hole-closes-it-early
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalHits {

    private PortalHits() {
    }

    /**
     * Breaks the portal or dismisses the hole a started hit lands on, in
     * place of the block behind it.
     *
     * @param event the left click block event
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || event.getFace() == null) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && dismissesAt(level, event.getPos(), event.getFace())) {
            event.setCanceled(true);
        }
    }

    private static boolean dismissesAt(ServerLevel level, BlockPos block, Direction face) {
        return PortalChoice.breakAt(level, block, face) || OpenHoles.dismissAt(level, block, face)
                || PortableHoles.dismissAt(level, block, face);
    }
}
