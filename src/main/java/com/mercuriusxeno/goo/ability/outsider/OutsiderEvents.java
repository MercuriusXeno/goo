package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps Outsider's rooms whole: once a second the pocket stands every
 * removed voidstone and doorway again, each player tick catches a player
 * fallen below their room's floor, a player logging back in inside the
 * pocket is told their room's edges, and a block placed outside the placer's
 * own grown cells is refused.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class OutsiderEvents {

    private OutsiderEvents() {
    }

    /**
     * Restores the rooms' fixtures once a second.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == OutsiderRooms.POCKET
                && level.getGameTime() % OutsiderRooms.RESTORE_INTERVAL == 0) {
            OutsiderRooms.restoreFixtures(level);
        }
    }

    /**
     * Catches a player fallen below their room's floor.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OutsiderRooms.keepInRoom(player);
        }
    }

    /**
     * Tells a player logging back in inside the pocket their room's edges,
     * so their client carries them across as it did before they left.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OutsiderRooms.sendEdges(player);
        }
    }

    /**
     * Refuses a block a player places in the pocket outside their own grown cells.
     *
     * @param event the block place event
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !OutsiderRooms.mayBuildAt(player, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
