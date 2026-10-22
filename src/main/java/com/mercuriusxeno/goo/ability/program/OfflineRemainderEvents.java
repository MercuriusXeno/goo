package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Hands a returning player the remainders a payout kept for them while they were
 * away, credited through their own remainder capability, with any whole mB those
 * remainders made up paid into their inventory.
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class OfflineRemainderEvents {

    private OfflineRemainderEvents() {
    }

    /**
     * Hands the owed remainders over on login.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void handOverOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Drinks.give(player.level(), player, OfflineRemainders.get(player.level())
                .handOver(player.getUUID(), GooCapabilities.remainderLedgerOf(player)));
    }
}
