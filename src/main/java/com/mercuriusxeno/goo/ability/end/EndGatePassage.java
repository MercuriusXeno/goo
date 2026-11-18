package com.mercuriusxeno.goo.ability.end;

import com.mercuriusxeno.goo.ability.zone.ConjuredDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Carries a player through the gate End conjured to its destination: into
 * the End onto its entrance platform, laid as a vanilla end portal lays it,
 * where a matching gate stands on them and releases them, as Zone's door
 * releases its arriver in their room. A player carried into the End keeps
 * where they portalled in from, which the gate they summon there leads back to.
 * decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back
 * decision matching-doorway-opens-in-the-room-and-releases-you
 * decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
 */
public final class EndGatePassage {

    private EndGatePassage() {
    }

    /**
     * Carries a player to a destination; a server not holding its level carries them nowhere.
     *
     * @param player      the player the gate envelops
     * @param destination where the gate leads
     * @return true once the player stands at the destination
     */
    public static boolean carry(ServerPlayer player, EndDestination destination) {
        ServerLevel there = player.level().getServer().getLevel(destination.dimension());
        if (there == null) {
            return false;
        }
        if (destination.dimension() == Level.END) {
            EndPlatformFeature.createEndPlatform(there, BlockPos.containing(destination.feet()), true);
            // decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
            EndWayBack.keepOn(player, new EndWayBack(Optional.of(EndDestination.wayBackFrom(
                    player.level().dimension(), player.position(), player.getYRot()))));
        }
        // decision door-carries-its-own-sound-through-the-change
        ConjuredDoors.carry(player, new TeleportTransition(there, destination.feet(), Vec3.ZERO, destination.yaw(), 0f,
                TeleportTransition.DO_NOTHING));
        ConjuredDoors.release(player);
        return true;
    }
}
