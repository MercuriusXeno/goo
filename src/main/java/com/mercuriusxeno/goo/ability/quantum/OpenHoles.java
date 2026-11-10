package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

/**
 * The Quantum holes standing open on a server: each a pair of mouths, one on
 * the struck face and one on the wall's far face, linked as gates a body
 * goes through until the hole closes.
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class OpenHoles {

    private static final List<Hole> OPEN = new ArrayList<>();
    private static final double HALF = 0.5;
    /** The pitch of the wind-down a hit hole closes with, as a hit portal breaks with. */
    private static final float DISMISS_PITCH = 1.5F;

    private OpenHoles() {
    }

    /**
     * A hole standing open.
     *
     * @param dimension the level it stands in
     * @param entry     the mouth on the struck face
     * @param exit      the mouth on the wall's far face
     * @param closesAt  the game time it closes
     */
    public record Hole(ResourceKey<Level> dimension, Gate entry, Gate exit, long closesAt) {
    }

    /**
     * @return the holes standing open
     */
    public static List<Hole> open() {
        return List.copyOf(OPEN);
    }

    /**
     * Stands a hole open and tells the clients.
     *
     * @param server the server
     * @param hole   the hole
     */
    static void open(MinecraftServer server, Hole hole) {
        OPEN.add(hole);
        sync(server);
    }

    /**
     * Closes the holes whose time is up, telling the clients when one closes.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (OPEN.removeIf(hole -> closed(server, hole))) {
            sync(server);
        }
    }

    /**
     * Tells every player the holes standing open in their level, so their
     * client draws the view through each mouth and lets them pass through.
     *
     * @param server the server
     */
    static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HolesSyncPayload payload = new HolesSyncPayload(OPEN.stream()
                    .filter(hole -> hole.dimension().equals(player.level().dimension()))
                    .map(hole -> new HolesSyncPayload.Pair(hole.entry(), hole.exit())).toList());
            // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /**
     * Forgets every open hole as the server stops.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
    }

    /**
     * Dismisses the hole whose mouth covers a hit block's face, as a hit
     * breaks a portal.
     *
     * @param level the level hit in
     * @param block the hit block
     * @param face  the hit face
     * @return true when a hole was dismissed
     */
    public static boolean dismissAt(ServerLevel level, BlockPos block, Direction face) {
        boolean dismissed = OPEN.removeIf(hole -> hole.dimension().equals(level.dimension())
                && (mouthOn(hole.entry(), block, face) || mouthOn(hole.exit(), block, face)));
        if (dismissed) {
            level.playSound(null, block.getX() + HALF, block.getY() + HALF, block.getZ() + HALF,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, DISMISS_PITCH);
            sync(level.getServer());
        }
        return dismissed;
    }

    private static boolean mouthOn(Gate mouth, BlockPos block, Direction face) {
        return mouth.face() == face && mouth.covers(block);
    }

    /**
     * Whether a hole closes: its time is up and nothing stands in either of
     * its mouths, so it never closes on a body passing through it.
     *
     * @param server the server
     * @param hole   the hole
     * @return true when the hole closed
     */
    private static boolean closed(MinecraftServer server, Hole hole) {
        ServerLevel level = server.getLevel(hole.dimension());
        return level == null || level.getGameTime() >= hole.closesAt() && !occupied(level, hole.entry())
                && !occupied(level, hole.exit());
    }

    private static boolean occupied(ServerLevel level, Gate mouth) {
        return !level.getEntities((Entity) null, mouth.blocks(), Entity::isAlive).isEmpty();
    }
}
