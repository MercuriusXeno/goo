package com.mercuriusxeno.goo.ability.zone;

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
 * Keeps Zone's rooms whole: once a second the pocket stands each
 * room's removed center voidstone and doorway again, each player tick
 * catches a player fallen below their room's floor, a player logging back
 * in inside the pocket is told their room's edges, and a block placed where
 * it does not lie wholly inside the placer's own room's extent is refused.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ZoneEvents {

    private ZoneEvents() {
    }

    /**
     * Restores the rooms' fixtures once a second.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == ZoneRooms.POCKET
                && level.getGameTime() % ZoneRooms.RESTORE_INTERVAL == 0) {
            ZoneRooms.restoreFixtures(level);
        }
    }

    /**
     * Catches a player fallen below their room's floor, and once a second
     * holds the far side of the room edges they stand near loaded.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZoneRooms.keepInRoom(player);
            if (player.tickCount % FAR_SIDE_INTERVAL == 0) {
                ZoneRooms.holdFarSideLoaded(player);
            }
        }
    }

    /** How often, in ticks, a player's far side is held loaded again, well inside a portal ticket's lapse. */
    private static final int FAR_SIDE_INTERVAL = 20;

    /**
     * Tells a player logging back in inside the pocket their room's edges,
     * so their client carries them across as it did before they left.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ZoneRooms.sendEdges(player);
        }
    }

    /**
     * Refuses a block a player places in the pocket not wholly inside their own room's extent.
     *
     * @param event the block place event
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !ZoneRooms.mayBuildAt(player, event.getPos())) {
            event.setCanceled(true);
        }
    }
}
