package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps linked portals standing on their walls: a portal whose block is gone
 * falls, taking its link and the link's tether with it. Going through a
 * linked portal is GatePassage's.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalTravel {

    private PortalTravel() {
    }

    /**
     * Drops each link whose portal's block is gone.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        QuantumPortals portals = QuantumPortals.get(server.overworld());
        for (Map.Entry<UUID, QuantumPortals.Link> owned : portals.links().entrySet()) {
            QuantumPortals.Link link = owned.getValue();
            if (fallen(server, link.entry()) || fallen(server, link.exit())) {
                PortalTether.tie(server, owned.getKey(), link, false);
                portals.fall(fallen(server, link.entry()) ? link.entry() : link.exit());
                PortalChoice.syncEveryone(server);
            }
        }
    }

    /**
     * Tells a player gone to another dimension the links standing there, so
     * it sees through them; the sync holds only its own dimension's links.
     * linked-partner-chunks-stream-to-any-viewer
     *
     * @param event the dimension change
     */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PortalChoice.sync(player);
        }
    }

    /**
     * Forgets the portal sync last told a player who logged out, so the
     * record holds only players on the server.
     *
     * @param event the logout
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PortalChoice.forget(event.getEntity().getUUID());
    }

    /**
     * Tells a respawned player the links standing where it respawned, which
     * may be another dimension than it died in.
     * linked-partner-chunks-stream-to-any-viewer
     *
     * @param event the respawn
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PortalChoice.sync(player);
        }
    }

    /**
     * Whether a portal's block is gone, read only where its chunk is loaded.
     *
     * @param server the server
     * @param portal the portal
     * @return true for a portal whose block no longer stands
     */
    static boolean fallen(MinecraftServer server, Portal portal) {
        ServerLevel level = server.getLevel(portal.block().dimension());
        return level == null || level.isLoaded(portal.block().pos())
                && (level.getBlockState(portal.block().pos()).isAir() || level.getBlockState(portal.second()).isAir());
    }
}
