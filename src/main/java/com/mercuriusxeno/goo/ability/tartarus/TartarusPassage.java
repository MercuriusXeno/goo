package com.mercuriusxeno.goo.ability.tartarus;

import com.mercuriusxeno.goo.ability.zone.ConjuredDoors;
import com.mercuriusxeno.goo.ability.zone.GateLook;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Carries a player through Tartarus' gate onto the spot its search found,
 * by a direct transfer: no nether portal block is placed or read, and
 * vanilla's portal search and linkage are never called. A matching gate
 * stands on them there and releases them, as Zone's door does.
 * decision tartarus-makes-no-nether-portal-and-touches-no-portal-linkage
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 * The spot is read again as the gate takes them, so ground changed during
 * the hold sets no one down on lava.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 */
public final class TartarusPassage {

    private TartarusPassage() {
    }

    /**
     * Carries a player to the spot their hold resolved, where it still stands safe.
     *
     * @param player  the player the gate envelops
     * @param arrival where the hold carries them
     * @return true once the player stands there
     */
    public static boolean carry(ServerPlayer player, TartarusArrival arrival) {
        TartarusHolds.forget(player);
        ServerLevel there = player.level().getServer().getLevel(arrival.destination().dimension());
        if (there == null || !TartarusPlacement.standsAt(new TartarusLevelCells(there), arrival.spot(),
                TartarusLevelCells.spanOf(there))) {
            player.sendSystemMessage(Component.translatable(TartarusHolds.NO_SAFE_FLOOR), true);
            return false;
        }
        ConjuredDoors.carry(player, transitionTo(there, arrival.spot(), arrival.yaw()));
        ConjuredDoors.release(player, GateLook.NETHER_PORTAL);
        return true;
    }

    /**
     * The transfer onto a spot: the middle of the block the feet fill, every coordinate exact.
     *
     * @param there the level the gate leads to
     * @param spot  the feet of the safe spot
     * @param yaw   the way the player faces arriving, in degrees
     * @return the transfer
     */
    public static TeleportTransition transitionTo(ServerLevel there, BlockPos spot, float yaw) {
        return new TeleportTransition(there, Vec3.atBottomCenterOf(spot), Vec3.ZERO, yaw, 0f,
                TeleportTransition.DO_NOTHING);
    }
}
