package com.mercuriusxeno.goo.ability.crystal;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Crystal Scales on a player's model: while the player's crystal hearts
 * stand, from the glove or the brew, every watcher draws the diamond-blue
 * faceted overlay over the player, a pattern apart from Stasis and Haste's
 * glint; it clears once the crystal hearts are gone.
 * decision scales-crystal-hearts-diamond-blue-overlay
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ScalesOverlay {

    /** Ticks between one overlay refresh and the next, so a player who starts watching sees it within a second. */
    static final int OVERLAY_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh, so the overlay never dips. */
    static final int OVERLAY_TICKS = OVERLAY_REFRESH_TICKS * 3;
    private static final int OVERLAY_CLEARED = 0;

    /** The players whose watchers draw the overlay now. */
    private static final Set<UUID> SCALED = new HashSet<>();

    private ScalesOverlay() {
    }

    /**
     * Refreshes the overlay of each player wearing crystal hearts, and clears
     * it from a player whose crystal hearts are gone.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % OVERLAY_REFRESH_TICKS != 0) {
            return;
        }
        if (wearsScales(player)) {
            SCALED.add(player.getUUID());
            send(player, OVERLAY_TICKS);
        } else if (SCALED.remove(player.getUUID())) {
            send(player, OVERLAY_CLEARED);
        }
    }

    /**
     * Forgets a player who leaves.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SCALED.remove(event.getEntity().getUUID());
    }

    /**
     * Whether a player's crystal hearts stand.
     *
     * @param player the player
     * @return true while a Scales heart overlay stands
     */
    public static boolean wearsScales(ServerPlayer player) {
        if (!player.hasData(GooAttachments.HEART_OVERLAY)) {
            return false;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        return overlay.kind() == HeartKind.SCALES && overlay.stands();
    }

    private static void send(ServerPlayer player, int ticks) {
        EntityVisuals.sendToWatchers(player, new AilmentPayload(player.getId(), AilmentKind.SCALES, ticks));
    }
}
