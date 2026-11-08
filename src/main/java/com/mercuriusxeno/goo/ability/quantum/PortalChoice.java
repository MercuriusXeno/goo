package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.network.PortalsSyncPayload;
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
 * A player's portals as they place, choose and link them: a Portal cast
 * places one; with Portal in hand, right clicking one of theirs enters
 * choosing its partner, and every distant portal of theirs shows until the
 * link lands; right clicking another links the two, the first the blue entry
 * portal, unlinking the pair the player linked before and moving the chunk
 * tether to the new pair. The player's client is told of every change.
 * portals-link-by-looking-through-portal-vision
 */
public final class PortalChoice {

    private static final float SOUND_VOLUME = 0.8f;
    private static final float CHOOSE_PITCH = 1.6f;
    private static final float LINK_PITCH = 1.2f;
    private static final float BREAK_PITCH = 1.5f;
    /** The portal sync last told each player's client. */
    private static final Map<UUID, PortalsSyncPayload> LAST_SENT = new ConcurrentHashMap<>();

    private PortalChoice() {
    }

    /**
     * Places a portal for its caster, inert; where the last portal the caster
     * placed still stands and is not paired, the new one pairs with it, the
     * older the blue entry and the newer the orange exit. Where the last
     * placed broke, the new one stands inert, never pairing with an older one.
     *
     * @param level  the level the portal stands in
     * @param owner  the caster
     * @param portal the portal
     */
    public static void place(ServerLevel level, UUID owner, Portal portal) {
        QuantumPortals portals = QuantumPortals.get(level);
        Optional<Portal> last = portals.lastPlaced(owner);
        boolean fresh = !portals.portalsOf(owner).contains(portal);
        portals.place(owner, portal);
        if (fresh && last.isPresent() && portals.partnerOf(last.get()).isEmpty()) {
            linkPair(level, owner, last.get(), portal);
        }
        syncEveryone(level.getServer());
    }

    /**
     * Links two of a player's portals, the first the blue entry, moving the
     * chunk tether from the pair unlinked to the pair linked.
     *
     * @param level the level
     * @param owner the player
     * @param entry the blue entry portal
     * @param exit  the orange exit portal
     */
    private static void linkPair(ServerLevel level, UUID owner, Portal entry, Portal exit) {
        QuantumPortals portals = QuantumPortals.get(level);
        Optional<Portal> choosing = portals.choosing(owner);
        portals.choose(owner, Optional.of(entry));
        portals.link(owner, exit).ifPresent(relink -> {
            relink.before().ifPresent(unlinked -> PortalTether.tie(level.getServer(), owner, unlinked, false));
            PortalTether.tie(level.getServer(), owner, relink.now(), true);
        });
        portals.choose(owner, choosing.filter(held -> portals.portalsOf(owner).contains(held)));
    }

    /**
     * Enters choosing a partner for one of the player's portals, or ends choosing.
     *
     * @param player the player
     * @param portal the portal right clicked, empty to end choosing
     */
    public static void choose(ServerPlayer player, Optional<Portal> portal) {
        if (QuantumPortals.get(player.level()).choose(player.getUUID(), portal)) {
            portal.ifPresent(chosen -> sound(player, chosen.faceCenter(), CHOOSE_PITCH));
            sync(player);
        }
    }

    /**
     * Links the portal the player is choosing for to the one right clicked,
     * moving the chunk tether from the pair unlinked to the pair linked.
     *
     * @param player the player
     * @param target the portal right clicked as the partner
     */
    public static void link(ServerPlayer player, Portal target) {
        UUID owner = player.getUUID();
        QuantumPortals.get(player.level()).link(owner, target).ifPresent(relink -> {
            relink.before().ifPresent(before -> PortalTether.tie(player.level().getServer(), owner, before, false));
            PortalTether.tie(player.level().getServer(), owner, relink.now(), true);
            sound(player, relink.now().entry().faceCenter(), LINK_PITCH);
            sound(player, relink.now().exit().faceCenter(), LINK_PITCH);
        });
        syncEveryone(player.level().getServer());
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
        Map<UUID, QuantumPortals.Link> before = portals.links();
        portals.fall(hit.get()).ifPresent(owner -> PortalTether.tie(level.getServer(), owner, before.get(owner),
                false));
        // operator ruling: no vanilla particles as a portal breaks, its wind-down sound alone
        Vec3 at = hit.get().faceCenter();
        level.playSound(null, at.x(), at.y(), at.z(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                SOUND_VOLUME, BREAK_PITCH);
        syncEveryone(level.getServer());
        return true;
    }

    /**
     * Tells a player's client of their portals, their link and their choosing.
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
        UUID owner = player.getUUID();
        // linked-partner-chunks-stream-to-any-viewer
        List<QuantumPortals.Link> links = portals.links().values().stream()
                .filter(link -> link.entry().block().dimension().equals(player.level().dimension()))
                .toList();
        return new PortalsSyncPayload(portals.portalsOf(owner), portals.linkOf(owner), portals.choosing(owner),
                links);
    }

    /**
     * Tells every player's client of the portals, since a link any player
     * makes or loses shows to all of them.
     *
     * @param server the server
     */
    public static void syncEveryone(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(PortalChoice::sync);
    }

    private static void sound(ServerPlayer player, Vec3 at, float pitch) {
        player.level().playSound(null, at.x(), at.y(), at.z(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, SOUND_VOLUME, pitch);
    }
}
