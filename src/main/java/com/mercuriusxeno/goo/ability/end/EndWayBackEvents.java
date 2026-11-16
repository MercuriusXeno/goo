package com.mercuriusxeno.goo.ability.end;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Drops a player's End way back once they leave the End by any way: through
 * the End gate home, the vanilla exit portal, or a respawn outside it.
 * decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class EndWayBackEvents {

    private EndWayBackEvents() {
    }

    /**
     * Keeps the way back a player holds as they change level, dropping it as they leave the End.
     *
     * @param event the dimension change
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        EndWayBack held = player.getData(GooAttachments.END_WAY_BACK);
        EndWayBack kept = held.afterChangingDimension(event.getFrom(), event.getTo());
        if (!kept.equals(held)) {
            player.setData(GooAttachments.END_WAY_BACK, kept);
        }
    }

    /**
     * Drops the way back of a player respawning outside the End, as the exit
     * portal's credits respawn them.
     *
     * @param event the respawn
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().dimension() != Level.END && player.getData(GooAttachments.END_WAY_BACK).stands()) {
            player.setData(GooAttachments.END_WAY_BACK, EndWayBack.NONE);
        }
    }
}
