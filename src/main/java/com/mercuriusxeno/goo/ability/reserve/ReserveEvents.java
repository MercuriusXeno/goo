package com.mercuriusxeno.goo.ability.reserve;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Spends a player's reserve against the world: a hit spends reserve hearts
 * before real health, after any heart overlay has taken its share, and hunger
 * the bar loses spends reserve shanks back into it.
 * reserve-hearts-sit-behind-the-bar
 * reserve-channels-on-jelly
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ReserveEvents {

    private ReserveEvents() {
    }

    /**
     * Runs what of a hit passed the heart overlay through the reserve hearts.
     *
     * @param event the damage event, after armor and before absorption
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.RESERVE)) {
            return;
        }
        Reserve reserve = player.getData(GooAttachments.RESERVE);
        Reserve.Spent spent = reserve.spend(event.getNewDamage());
        if (spent.reserve() != reserve) {
            player.setData(GooAttachments.RESERVE, spent.reserve());
        }
        event.setNewDamage(spent.remainder());
    }

    /**
     * Refills hunger the bar lost with reserve shanks, and reads where the bar stands.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.RESERVE)) {
            return;
        }
        Reserve reserve = player.getData(GooAttachments.RESERVE);
        if (!reserve.stands()) {
            return;
        }
        int food = player.getFoodData().getFoodLevel();
        int refill = reserve.refillFor(food);
        if (refill > 0) {
            food += refill;
            player.getFoodData().setFoodLevel(food);
        }
        Reserve after = reserve.read(refill, food);
        if (!after.equals(reserve)) {
            player.setData(GooAttachments.RESERVE, after);
        }
    }
}
