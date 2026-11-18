package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.network.PortalsSyncPayload;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's pair of portals as they place and break them: a Portal shot
 * places the portal of its color, moving the one of that color they placed
 * before, and the two link whenever both stand, the chunk tether following
 * the pair. The players' clients are told of every change.
 * player-holds-one-pair-of-portals
 * portals-link-by-looking-through-portal-vision
 */
public final class PortalPairs {

    private static final float SOUND_VOLUME = 0.8f;
    private static final float BREAK_PITCH = 1.5f;
    /** The portal sync last told each player's client. */
    private static final Map<UUID, PortalsSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    private PortalPairs() {
    }

    /**
     * Places a portal for its caster in its color's place: the caster's
     * portal of that color moves there, and with the other color standing the
     * two link, the chunk tether moving from the pair before to the pair now.
     * player-holds-one-pair-of-portals
     *
     * @param level  the level the portal stands in
     * @param owner  the caster
     * @param portal the portal
     */
    public static void place(ServerLevel level, UUID owner, Portal portal) {
        QuantumPortals portals = QuantumPortals.get(level);
        Optional<QuantumPortals.Link> before = portals.pairOf(owner);
        if (portals.place(owner, portal)) {
            // portal-placement-plays-a-zap-that-opens-into-a-hum
            Vec3 at = portal.faceCenter();
            level.playSound(null, at.x(), at.y(), at.z(), GooSounds.PORTAL_PLACE, SoundSource.BLOCKS,
                    SOUND_VOLUME, 1f);
            retether(level.getServer(), owner, before);
        }
        syncEveryone(level.getServer());
    }

    /**
     * Lets go the chunks of the pair a player held before, then holds the
     * chunks of the pair they hold now: the two may share a portal, and
     * letting the old pair go must not let the shared portal's chunk go.
     *
     * @param server the server
     * @param owner  the player
     * @param before the pair they held before, empty for none
     */
    private static void retether(MinecraftServer server, UUID owner, Optional<QuantumPortals.Link> before) {
        before.ifPresent(link -> PortalTether.tie(server, owner, link, false));
        QuantumPortals.get(server.overworld()).pairOf(owner).ifPresent(link -> PortalTether.tie(server, owner, link,
                true));
    }

    /**
     * Breaks the portal covering a hit block's face, as though it were a block
     * over the wall: the portal goes, with its link and the link's tether,
     * and the wall behind it stands.
     *
     * @param level the level hit in
     * @param block the hit block
     * @param face  the hit face
     * @return true when a portal broke
     */
    public static boolean breakAt(ServerLevel level, BlockPos block, Direction face) {
        QuantumPortals portals = QuantumPortals.get(level);
        Optional<Portal> hit = portals.portalAt(GlobalPos.of(level.dimension(), block), face);
        if (hit.isEmpty()) {
            return false;
        }
        portals.fall(hit.get()).ifPresent(fallen -> PortalTether.tie(level.getServer(), fallen.owner(),
                fallen.link(), false));
        // operator ruling: no vanilla particles as a portal breaks, its wind-down sound alone
        Vec3 at = hit.get().faceCenter();
        level.playSound(null, at.x(), at.y(), at.z(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                SOUND_VOLUME, BREAK_PITCH);
        syncEveryone(level.getServer());
        return true;
    }

    /**
     * Tells a player's client of their portals and the links in their level.
     *
     * @param player the player
     */
    public static void sync(ServerPlayer player) {
        PortalsSyncPayload payload = syncFor(player);
        LAST_SENT.put(player.getUUID(), payload);
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * The portal sync last told a player's client, whether or not its
     * connection carried it, so a gametest's player shows what it was told.
     *
     * @param player the player
     * @return the last sync, empty where none was told
     */
    public static Optional<PortalsSyncPayload> lastSentTo(ServerPlayer player) {
        return Optional.ofNullable(LAST_SENT.get(player.getUUID()));
    }

    /**
     * Forgets the portal sync last told a player.
     *
     * @param player the player's id
     */
    static void forget(UUID player) {
        LAST_SENT.remove(player);
    }

    /**
     * The portal sync a player's client is sent.
     *
     * @param player the player
     * @return the payload
     */
    public static PortalsSyncPayload syncFor(ServerPlayer player) {
        QuantumPortals portals = QuantumPortals.get(player.level());
        // linked-partner-chunks-stream-to-any-viewer
        List<QuantumPortals.Link> links = portals.links().stream()
                .filter(link -> link.entry().block().dimension().equals(player.level().dimension()))
                .toList();
        return new PortalsSyncPayload(portals.portalsOf(player.getUUID()), links);
    }

    /**
     * Tells every player's client of the portals, since a link any player
     * makes or loses shows to all of them.
     *
     * @param server the server
     */
    public static void syncEveryone(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(PortalPairs::sync);
    }
}
