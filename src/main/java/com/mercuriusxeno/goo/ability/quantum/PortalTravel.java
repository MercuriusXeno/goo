package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps portals standing where they may stand: a portal whose block is gone,
 * or before which a block fills its face, breaks, taking its link and the
 * link's tether with it. Going through a linked portal is GatePassage's.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalTravel {

    private PortalTravel() {
    }

    /**
     * Breaks each portal, linked or not, that its place no longer holds, as a
     * hit breaks it, its link and the link's tether with it.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        QuantumPortals portals = QuantumPortals.get(server.overworld());
        List<Portal> fallen = Stream.concat(
                portals.owners().stream().flatMap(owner -> portals.portalsOf(owner).stream()),
                portals.links().stream().flatMap(link -> Stream.of(link.entry(), link.exit())))
                .distinct().filter(portal -> fallen(server, portal)).toList();
        for (Portal portal : fallen) {
            PortalChoice.breakAt(server.getLevel(portal.block().dimension()), portal.block().pos(), portal.face());
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
     * Whether a portal's place no longer holds it, read only where its chunk
     * is loaded: a block it stands on is gone, or a block a body cannot pass
     * fills a cell it faces, where no portal could be placed.
     *
     * @param server the server
     * @param portal the portal
     * @return true for a portal that falls
     */
    static boolean fallen(MinecraftServer server, Portal portal) {
        ServerLevel level = server.getLevel(portal.block().dimension());
        if (level == null) {
            return false;
        }
        if (!level.isLoaded(portal.block().pos())) {
            return false;
        }
        return Stream.of(portal.block().pos(), portal.second()).anyMatch(pos -> level.getBlockState(pos).isAir()
                || displaced(level, pos.relative(portal.face())));
    }

    private static boolean displaced(ServerLevel level, BlockPos front) {
        return !level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }
}
