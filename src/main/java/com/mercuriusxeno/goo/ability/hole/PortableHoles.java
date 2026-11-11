package com.mercuriusxeno.goo.ability.hole;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.ArrayList;
import java.util.HashMap;
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
    /**
     * The items each open hole swallowed last, sent with every sync until its next swallow or its closing.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     */
    private static final Map<UUID, List<PortableHolesSyncPayload.Swallowed>> SWALLOWED = new HashMap<>();
    private static final float SWALLOW_VOLUME = 0.4F;
    private static final float SWALLOW_PITCH = 0.6F;
    private static final double HALF = 0.5;
    /**
     * The Outsider room's wub, reversed in its file, pitched down, so the hole closes on a falling note.
     * decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down
     */
    private static final float CLOSING_PITCH = 0.625F;

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
     * The items an owner's hole swallowed last, as the clients were told of them.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     *
     * @param owner the hole's owner
     * @return each swallowed item's kind, place and game time, empty before a swallow
     */
    public static List<PortableHolesSyncPayload.Swallowed> swallowedInto(UUID owner) {
        return SWALLOWED.getOrDefault(owner, List.of());
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
     * Swallows what meets each open mouth, pours each ceiling's hole once it
     * stands fully open, and closes the holes whose time is up or whose owner
     * left their level, telling the clients when one closes or pours.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (OPEN.isEmpty()) {
            return;
        }
        // decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down
        // read before the removal, which keeps no record of why each hole closed
        List<PortableHole> ranOut = OPEN.values().stream().filter(hole -> ranOut(server, hole)).toList();
        boolean changed = OPEN.values().removeIf(hole -> closes(server, hole));
        ranOut.forEach(hole -> playClosing(server.getLevel(hole.dimension()), hole.mouth().getCenter()));
        for (Map.Entry<UUID, PortableHole> open : OPEN.entrySet()) {
            PortableHole hole = open.getValue();
            ServerLevel level = server.getLevel(hole.dimension());
            // decision hole-pours-each-kind-from-where-it-swims-once-open: the clients hear of the emptied shelf
            if (hole.poursAt(level.getGameTime())) {
                HoleOpening.pour(level, hole);
                open.setValue(hole.pouredOut());
                changed = true;
            }
            // a swallow changes what the mouth shows swimming in it and when it closes, so the clients hear of it
            List<PortableHolesSyncPayload.Swallowed> swallowed = hole.swallows() ? swallow(level, hole) : List.of();
            if (!swallowed.isEmpty()) {
                SWALLOWED.put(hole.owner(), swallowed);
                open.setValue(hole.fedAt(level.getGameTime()));
                changed = true;
            }
        }
        if (changed) {
            sync(server);
        }
    }

    /**
     * Closes the hole whose mouth covers a punched block's face, any block of
     * its footprint on the face its mouth stands on; what it stored stays on
     * the shelf.
     * decision punching-the-hole-closes-it-early
     *
     * @param level the level punched in
     * @param block the punched block
     * @param face  the punched face
     * @return true when a hole closed
     */
    public static boolean dismissAt(ServerLevel level, BlockPos block, Direction face) {
        boolean dismissed = OPEN.values().removeIf(hole -> hole.dimension().equals(level.dimension())
                && hole.face() == face && hole.footprint().contains(block));
        if (dismissed) {
            Vec3 at = Vec3.atCenterOf(block).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(HALF));
            playClosing(level, at);
            sync(level.getServer());
        }
        return dismissed;
    }

    private static void playClosing(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x(), at.y(), at.z(), GooSounds.HOLE_CLOSE.get(), SoundSource.PLAYERS, 1.0F,
                CLOSING_PITCH);
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

    /**
     * Whether a hole closes because its time ran out while its owner stands
     * in its level, the closing a player hears; a hole its owner left closes
     * silently.
     * decision hole-closes-with-the-outsider-wub-reversed-and-pitched-down
     *
     * @param server the server
     * @param hole   the hole
     * @return true when the hole's time is up and its owner stands in its level
     */
    private static boolean ranOut(MinecraftServer server, PortableHole hole) {
        ServerLevel level = server.getLevel(hole.dimension());
        ServerPlayer owner = server.getPlayerList().getPlayer(hole.owner());
        return level != null && level.getGameTime() >= hole.closesAt() && owner != null
                && owner.level().dimension().equals(hole.dimension());
    }

    /**
     * Shelves every item meeting a hole's mouth and takes it from the world.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     *
     * @param level the hole's level
     * @param hole  the hole
     * @return each item swallowed, as many as one sync tells of, for the clients to draw easing in
     */
    private static List<PortableHolesSyncPayload.Swallowed> swallow(ServerLevel level, PortableHole hole) {
        List<ItemEntity> fallen = level.getEntitiesOfClass(ItemEntity.class, hole.mouth(), ItemEntity::isAlive);
        if (fallen.isEmpty()) {
            return List.of();
        }
        PocketShelves shelves = PocketShelves.get(level);
        List<PortableHolesSyncPayload.Swallowed> swallowed = new ArrayList<>();
        for (ItemEntity item : fallen) {
            if (swallowed.size() < PortableHolesSyncPayload.MOST_SWALLOWS_TOLD) {
                swallowed.add(new PortableHolesSyncPayload.Swallowed(
                        BuiltInRegistries.ITEM.getKey(item.getItem().getItem()), item.position(), level.getGameTime()));
            }
            shelves.add(hole.owner(), item.getItem(), item.getItem().getCount());
            item.discard();
        }
        Vec3 middle = hole.mouth().getCenter();
        level.playSound(null, middle.x(), middle.y(), middle.z(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                SWALLOW_VOLUME, SWALLOW_PITCH);
        return List.copyOf(swallowed);
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
        // a closed hole's swallows go with it, and a new hole in their owner's name starts with none
        SWALLOWED.entrySet().removeIf(told -> !OPEN.containsKey(told.getKey())
                || told.getValue().getFirst().time() < OPEN.get(told.getKey()).openedAt());
        server.getPlayerList().getPlayers().forEach(PortableHoles::syncTo);
    }

    /**
     * Tells a player the holes standing in their level, so their client draws
     * each mouth.
     *
     * @param player the player
     */
    private static void syncTo(ServerPlayer player) {
        PocketShelves shelves = PocketShelves.get(player.level());
        PortableHolesSyncPayload payload = new PortableHolesSyncPayload(OPEN.values().stream()
                .filter(hole -> hole.dimension().equals(player.level().dimension()))
                .map(hole -> mouthOf(shelves, hole))
                .toList());
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * The mouth the clients are told of for a hole: where it stands, its
     * span, the kinds its owner stored and what it swallowed last.
     *
     * @param shelves the server's shelves
     * @param hole    the hole
     * @return the mouth as a sync carries it
     */
    public static PortableHolesSyncPayload.Mouth mouthOf(PocketShelves shelves, PortableHole hole) {
        return new PortableHolesSyncPayload.Mouth(hole.footprint(), hole.face(), hole.openedAt(), hole.closesAt(),
                storedOf(shelves, hole.owner()), swallowedInto(hole.owner()));
    }

    /**
     * Every kind an owner stored, for the client to draw swimming in the mouth.
     * decision hole-shows-every-kind-stored
     *
     * @param shelves the server's shelves
     * @param owner   the hole's owner
     * @return the kinds and their counts, in shelf order
     */
    private static List<PortableHolesSyncPayload.Stored> storedOf(PocketShelves shelves, UUID owner) {
        return shelves.shelfOf(owner).stream()
                .map(entry -> new PortableHolesSyncPayload.Stored(
                        BuiltInRegistries.ITEM.getKey(entry.kind().getItem()), entry.count()))
                .toList();
    }
}
