package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.HolesSyncPayload;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Quantum holes standing open on a server: each a pair of mouths, one
 * flush on the struck face and one flush on the wall's far face, carrying
 * whoever steps into either out of the other until the hole closes. A
 * traveller just come out of a mouth passes it by until they step clear of it.
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class OpenHoles {

    private static final List<Hole> OPEN = new ArrayList<>();

    private OpenHoles() {
    }

    /**
     * One of a hole's mouths.
     *
     * @param touch   the slab flush on its face a body steps into
     * @param arrival where feet come out of this mouth
     * @param middle  the mouth's middle, where it sounds
     * @param gate    the mouth as a gate the far side is seen through
     */
    public record Mouth(AABB touch, Vec3 arrival, Vec3 middle, Gate gate) {
    }

    /**
     * A hole standing open.
     *
     * @param dimension the level it stands in
     * @param entry     the mouth on the struck face
     * @param exit      the mouth on the wall's far face
     * @param axis      the axis its mouths stand across
     * @param closesAt  the game time it closes
     * @param heldAtEntry the travellers just come out of the entry, passing it by
     * @param heldAtExit  the travellers just come out of the exit, passing it by
     */
    public record Hole(ResourceKey<Level> dimension, Mouth entry, Mouth exit, Direction.Axis axis, long closesAt,
                       Set<UUID> heldAtEntry, Set<UUID> heldAtExit) {

        /**
         * A hole just opened, holding no one.
         *
         * @param dimension the level it stands in
         * @param entry     the mouth on the struck face
         * @param exit      the mouth on the wall's far face
         * @param axis      the axis its mouths stand across
         * @param closesAt  the game time it closes
         */
        public Hole(ResourceKey<Level> dimension, Mouth entry, Mouth exit, Direction.Axis axis, long closesAt) {
            this(dimension, entry, exit, axis, closesAt, new HashSet<>(), new HashSet<>());
        }
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
     * Carries whoever stepped into a mouth out of the other, and closes the
     * holes whose time is up, telling the clients when one closes.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (OPEN.removeIf(hole -> tick(server, hole))) {
            sync(server);
        }
    }

    /**
     * Tells every player the holes standing open in their level, so their
     * client draws the view through each mouth.
     *
     * @param server the server
     */
    static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            HolesSyncPayload payload = new HolesSyncPayload(OPEN.stream()
                    .filter(hole -> hole.dimension().equals(player.level().dimension()))
                    .map(hole -> new HolesSyncPayload.Pair(hole.entry().gate(), hole.exit().gate())).toList());
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
     * Ticks one hole.
     *
     * @param server the server
     * @param hole   the hole
     * @return true when the hole closed
     */
    private static boolean tick(MinecraftServer server, Hole hole) {
        ServerLevel level = server.getLevel(hole.dimension());
        if (level == null || level.getGameTime() >= hole.closesAt()) {
            return true;
        }
        carryThrough(level, hole.entry(), hole.heldAtEntry(), hole.exit(), hole.heldAtExit());
        carryThrough(level, hole.exit(), hole.heldAtExit(), hole.entry(), hole.heldAtEntry());
        return false;
    }

    private static void carryThrough(ServerLevel level, Mouth from, Set<UUID> heldAtFrom, Mouth to,
            Set<UUID> heldAtTo) {
        List<LivingEntity> touching = level.getEntitiesOfClass(LivingEntity.class, from.touch());
        heldAtFrom.removeIf(id -> touching.stream().noneMatch(entity -> entity.getUUID().equals(id)));
        for (LivingEntity traveller : touching) {
            if (!heldAtFrom.contains(traveller.getUUID())) {
                heldAtTo.add(traveller.getUUID());
                traveller.teleportTo(to.arrival().x(), to.arrival().y(), to.arrival().z());
                traveller.resetFallDistance();
            }
        }
    }
}
