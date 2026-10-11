package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Aeon's Haste on a player: the golden haste overlay on the player's model,
 * refreshed for every watcher while the held effect stands and cleared when
 * it ends. The speed and haste ride the aeon brew effect the held effect
 * shows, so no vanilla effect stands beside it.
 * haste-stacks-speed-under-the-golden-overlay
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class Haste {

    /** Ticks between one overlay refresh and the next, so a player who starts watching sees it within a second. */
    static final int OVERLAY_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the overlay never dips. */
    static final int OVERLAY_TICKS = OVERLAY_REFRESH_TICKS * 3;
    private static final int OVERLAY_CLEARED = 0;


    private Haste() {
    }

    /**
     * Starts Haste's overlay on a target.
     *
     * @param target the hasted entity
     */
    public static void lay(LivingEntity target) {
        sendOverlay(target, OVERLAY_TICKS);
    }

    /**
     * Clears Haste from a player as its held effect ends: the overlay drops
     * on every client, the aeon brew effect going with the held effect.
     *
     * @param player the player
     */
    static void clear(ServerPlayer player) {
        sendOverlay(player, OVERLAY_CLEARED);
    }

    /**
     * Refreshes the overlay of each player whose held effects lay Haste.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % OVERLAY_REFRESH_TICKS != 0
                || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        boolean hasted = player.getData(GooAttachments.HELD_EFFECTS).held().stream()
                .anyMatch(held -> held.lays().contains(LaidState.HASTE));
        if (hasted) {
            sendOverlay(player, OVERLAY_TICKS);
        }
    }

    private static void sendOverlay(LivingEntity target, int ticks) {
        EntityVisuals.sendToWatchers(target, new AilmentPayload(target.getId(), AilmentKind.HASTE, ticks));
    }
}
