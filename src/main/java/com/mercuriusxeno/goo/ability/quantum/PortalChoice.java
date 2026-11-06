package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.network.PortalsSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Optional;
import java.util.UUID;

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

    private PortalChoice() {
    }

    /**
     * Places a portal for its caster.
     *
     * @param level  the level the portal stands in
     * @param owner  the caster
     * @param portal the portal
     */
    public static void place(ServerLevel level, UUID owner, Portal portal) {
        QuantumPortals.get(level).place(owner, portal);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            sync(player);
        }
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
        sync(player);
    }

    /**
     * Tells a player's client of their portals, their link and their choosing.
     *
     * @param player the player
     */
    public static void sync(ServerPlayer player) {
        QuantumPortals portals = QuantumPortals.get(player.level());
        UUID owner = player.getUUID();
        PortalsSyncPayload payload = new PortalsSyncPayload(portals.portalsOf(owner), portals.linkOf(owner),
                portals.choosing(owner));
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void sound(ServerPlayer player, Vec3 at, float pitch) {
        player.level().playSound(null, at.x(), at.y(), at.z(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.PLAYERS, SOUND_VOLUME, pitch);
    }
}
