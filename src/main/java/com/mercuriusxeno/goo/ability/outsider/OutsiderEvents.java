package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps Outsider's rooms whole: once a second the pocket stands every
 * removed voidstone and doorway again, each player tick keeps a player in the
 * pocket inside their own room, and a block placed outside the placer's own
 * grown cells is refused.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
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
     * Keeps a player in the pocket inside their own room.
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
