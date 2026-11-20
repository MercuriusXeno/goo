package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.TunnelsSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/**
 * The Quantum tunnels standing open on a server: each a pair of mouths, one on
 * the struck face and one on the wall's far face, linked as gates a body
 * goes through until the tunnel closes.
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class OpenTunnels {

    private static final List<Tunnel> OPEN = new ArrayList<>();
    private static final double HALF = 0.5;
    /** The pitch of the wind-down a hit tunnel closes with, as a hit portal breaks with. */
    private static final float DISMISS_PITCH = 1.5F;

    private OpenTunnels() {
    }

    /**
     * A tunnel standing open.
     *
     * @param dimension the level it stands in
     * @param entry     the mouth on the struck face
     * @param exit      the mouth on the wall's far face
     * @param closesAt  the game time it closes
     */
    public record Tunnel(ResourceKey<Level> dimension, Gate entry, Gate exit, long closesAt) {
    }

    /**
     * @return the tunnels standing open
     */
    public static List<Tunnel> open() {
        return List.copyOf(OPEN);
    }

    /**
     * Stands a tunnel open and tells the clients.
     *
     * @param server the server
     * @param tunnel the tunnel
     */
    static void open(MinecraftServer server, Tunnel tunnel) {
        OPEN.add(tunnel);
        sync(server);
    }

    /**
     * Closes the tunnels whose time is up, telling the clients when one closes.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (OPEN.removeIf(tunnel -> closed(server, tunnel))) {
            sync(server);
        }
    }

    /**
     * Tells every player the tunnels standing open in their level, so their
     * client draws the view through each mouth and lets them pass through.
     *
     * @param server the server
     */
    static void sync(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(OpenTunnels::syncTo);
    }

    /**
     * Tells one player the tunnels standing open in their level.
     *
     * @param player the player
     */
    private static void syncTo(ServerPlayer player) {
        TunnelsSyncPayload payload = new TunnelsSyncPayload(OPEN.stream()
                .filter(tunnel -> tunnel.dimension().equals(player.level().dimension()))
                .map(tunnel -> new TunnelsSyncPayload.Pair(tunnel.entry(), tunnel.exit())).toList());
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Tells a player joining the tunnels standing in their level, so the
     * client shows those full size and grows open only the tunnels opened
     * after.
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    /**
     * Tells a player changing level the tunnels standing in their new one.
     *
     * @param event the dimension change event
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTo(player);
        }
    }

    /**
     * Forgets every open tunnel as the server stops.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
    }

    /**
     * Dismisses the tunnel whose mouth covers a hit block's face, as a hit
     * breaks a portal.
     *
     * @param level the level hit in
     * @param block the hit block
     * @param face  the hit face
     * @return true when a tunnel was dismissed
     */
    public static boolean dismissAt(ServerLevel level, BlockPos block, Direction face) {
        boolean dismissed = OPEN.removeIf(tunnel -> mouthOn(tunnel, level.dimension(), block, face));
        if (dismissed) {
            level.playSound(null, block.getX() + HALF, block.getY() + HALF, block.getZ() + HALF,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, DISMISS_PITCH);
            sync(level.getServer());
        }
        return dismissed;
    }

    /**
     * Whether an open tunnel's mouth covers a block's face, which a new mouth
     * may not lie over, as it may not lie over a portal.
     * hole-closes-the-space-to-an-opening-ahead
     *
     * @param dimension the level the block stands in
     * @param block     the block
     * @param face      the block's face
     * @return true where an open mouth covers it
     */
    public static boolean mouthAt(ResourceKey<Level> dimension, BlockPos block, Direction face) {
        return OPEN.stream().anyMatch(tunnel -> mouthOn(tunnel, dimension, block, face));
    }

    private static boolean mouthOn(Tunnel tunnel, ResourceKey<Level> dimension, BlockPos block, Direction face) {
        return tunnel.dimension().equals(dimension) && (mouthOn(tunnel.entry(), block, face)
                || mouthOn(tunnel.exit(), block, face));
    }

    private static boolean mouthOn(Gate mouth, BlockPos block, Direction face) {
        return mouth.face() == face && mouth.covers(block);
    }

    /**
     * Whether a tunnel closes: its time is up and nothing stands in either of
     * its mouths, so it never closes on a body passing through it.
     *
     * @param server the server
     * @param tunnel the tunnel
     * @return true when the tunnel closed
     */
    private static boolean closed(MinecraftServer server, Tunnel tunnel) {
        ServerLevel level = server.getLevel(tunnel.dimension());
        return level == null || level.getGameTime() >= tunnel.closesAt() && !occupied(level, tunnel.entry())
                && !occupied(level, tunnel.exit());
    }

    private static boolean occupied(ServerLevel level, Gate mouth) {
        return !level.getEntities((Entity) null, mouth.blocks(), Entity::isAlive).isEmpty();
    }
}
