package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps every goo:soul_bound stack through a player's death (decision
 * exorite-kept-through-death): the stacks leave the inventory before it
 * drops and return to their slots in the respawned player.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SoulBoundEvents {

    private SoulBoundEvents() {
    }

    /**
     * Moves every soul-bound stack out of a dying player's inventory into the
     * player's soul-bound attachment, before vanilla drops the inventory. It
     * runs last and skips a cancelled death, so a death another mod prevents
     * moves nothing; with keepInventory on, vanilla keeps the stacks itself.
     *
     * @param event the death event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }
        Inventory inventory = player.getInventory();
        List<SoulBoundStacks.SlotStack> kept = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(SoulBoundStacks.SOUL_BOUND)) {
                kept.add(new SoulBoundStacks.SlotStack(slot, inventory.removeItemNoUpdate(slot)));
            }
        }
        if (!kept.isEmpty()) {
            player.setData(GooAttachments.SOUL_BOUND_STACKS, new SoulBoundStacks(kept));
        }
    }

    /**
     * Puts each kept stack back into the slot it left in the respawned player,
     * or into any free slot when that one is taken.
     *
     * @param event the clone event
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        if (!event.isWasDeath() || !original.hasData(GooAttachments.SOUL_BOUND_STACKS)) {
            return;
        }
        Player respawned = event.getEntity();
        Inventory inventory = respawned.getInventory();
        for (SoulBoundStacks.SlotStack kept : original.getData(GooAttachments.SOUL_BOUND_STACKS).stacks()) {
            ItemStack stack = kept.stack();
            if (inventory.getItem(kept.slot()).isEmpty()) {
                inventory.setItem(kept.slot(), stack);
            } else if (!inventory.add(stack)) {
                respawned.drop(stack, false);
            }
        }
        original.removeData(GooAttachments.SOUL_BOUND_STACKS);
    }
}
