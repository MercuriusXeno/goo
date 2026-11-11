package com.mercuriusxeno.goo.ability.hole;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The portable holes standing open on a server, one per player across every
 * level: a hole swallows the items meeting its mouth onto its owner's shelf,
 * and closes when its time is up, its owner leaves its level, or its owner
 * opens another.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortableHoles {

    private static final Map<UUID, PortableHole> OPEN = new LinkedHashMap<>();
    private static final float SWALLOW_VOLUME = 0.4F;
    private static final float SWALLOW_PITCH = 0.6F;

    private PortableHoles() {
    }

    /**
     * @return the holes standing open
     */
    public static List<PortableHole> open() {
        return List.copyOf(OPEN.values());
    }

    /**
     * @param owner the player
     * @return the player's hole, empty while none stands
     */
    public static Optional<PortableHole> of(UUID owner) {
        return Optional.ofNullable(OPEN.get(owner));
    }

    /**
     * Stands a player's hole open, dismissing the one they had open anywhere,
     * and tells the clients.
     *
     * @param server the server
     * @param hole   the hole
     */
    static void stand(MinecraftServer server, PortableHole hole) {
        OPEN.put(hole.owner(), hole);
        sync(server);
    }

    /**
     * Swallows what meets each open mouth and closes the holes whose time is
     * up or whose owner left their level, telling the clients when one closes.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (OPEN.isEmpty()) {
            return;
        }
        boolean closed = OPEN.values().removeIf(hole -> closes(server, hole));
        for (PortableHole hole : OPEN.values()) {
            if (hole.swallows()) {
                swallow(server.getLevel(hole.dimension()), hole);
            }
        }
        if (closed) {
            sync(server);
        }
    }

    /**
     * Whether a hole closes: its level is gone, its time is up, or its owner
     * is away or stands in another level.
     *
     * @param server the server
     * @param hole   the hole
     * @return true when the hole closes
     */
    private static boolean closes(MinecraftServer server, PortableHole hole) {
        ServerLevel level = server.getLevel(hole.dimension());
        ServerPlayer owner = server.getPlayerList().getPlayer(hole.owner());
        return level == null || level.getGameTime() >= hole.closesAt() || owner == null
                || !owner.level().dimension().equals(hole.dimension());
    }

    private static void swallow(ServerLevel level, PortableHole hole) {
        List<ItemEntity> fallen = level.getEntitiesOfClass(ItemEntity.class, hole.mouth(), ItemEntity::isAlive);
        if (fallen.isEmpty()) {
            return;
        }
        PocketShelves shelves = PocketShelves.get(level);
        for (ItemEntity item : fallen) {
            shelves.add(hole.owner(), item.getItem(), item.getItem().getCount());
            item.discard();
        }
        Vec3 middle = hole.mouth().getCenter();
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                SWALLOW_VOLUME, SWALLOW_PITCH);
    }

    /**
     * Tells a player joining the holes standing in their level.
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
     * Tells a player changing level the holes standing in their new one.
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
     * Forgets every open hole as the server stops.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
    }

    private static void sync(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(PortableHoles::syncTo);
    }

    /**
     * Tells a player the holes standing in their level, so their client draws
     * each mouth.
     *
     * @param player the player
     */
    private static void syncTo(ServerPlayer player) {
        PortableHolesSyncPayload payload = new PortableHolesSyncPayload(OPEN.values().stream()
                .filter(hole -> hole.dimension().equals(player.level().dimension()))
                .map(hole -> new PortableHolesSyncPayload.Mouth(hole.block(), hole.face(), hole.closesAt()))
                .toList());
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
