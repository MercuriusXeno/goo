package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
        handOver(OfflineRemainders.get(player.level()), player.getUUID(), GooCapabilities.remainderLedgerOf(player),
                goo -> giveToInventory(player, goo));
    }

    /**
     * Credits what a returning player is owed through their own ledger and
     * hands the whole mB it made up straight to their inventory.
     * decision unmake-and-everything-only-it-used-deleted
     *
     * @param offline   the server's record of owed remainders
     * @param player    the returning player's id
     * @param ledger    their own ledger
     * @param inventory takes the whole mB into their inventory
     */
    static void handOver(OfflineRemainders offline, UUID player, IRemainderLedger ledger,
                         Consumer<GooContents> inventory) {
        GooContents paid = offline.handOver(player, ledger);
        if (!paid.getAll().isEmpty()) {
            inventory.accept(paid);
        }
    }

    /**
     * Puts whole goo into a player's inventory, dropping at their feet what has no space.
     *
     * @param player the player
     * @param goo    the whole mB to give
     */
    private static void giveToInventory(ServerPlayer player, GooContents goo) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : goo.getAll().entrySet()) {
            ItemStack stack = GooStacks.createForOutput(entry.getKey(), entry.getValue());
            if (!stack.isEmpty() && !player.addItem(stack) && !stack.isEmpty()) {
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack,
                        0, 0, 0);
                item.setDefaultPickUpDelay();
                player.level().addFreshEntity(item);
            }
        }
    }
}
