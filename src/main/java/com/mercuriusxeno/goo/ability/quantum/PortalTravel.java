package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Carries a player stepping into either portal of a linked pair out of the
 * other, an afterimage left at each end. The portal they come out of holds
 * them until they step clear of its mouth, so arriving never carries them
 * straight back. A portal whose block is gone falls, taking its link and
 * the link's tether with it.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PortalTravel {

    /** How long each afterimage lingers, in ticks. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;
    /** The travel sound's pitch, a little above the enderman's, so a portal reads apart from a blink. */
    private static final float TRAVEL_PITCH = 1.4f;
    /** The cells around an exit's mouth a traveller may come out in, tried in order. */
    private static final List<Direction> ARRIVALS = List.of(Direction.DOWN, Direction.UP);

    private PortalTravel() {
    }

    /**
     * Carries every player standing in a linked portal's mouth out of its partner.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        freeThoseWhoLeft(server);
        QuantumPortals portals = QuantumPortals.get(server.overworld());
        for (Map.Entry<UUID, QuantumPortals.Link> owned : portals.links().entrySet()) {
            QuantumPortals.Link link = owned.getValue();
            if (fallen(server, link.entry()) || fallen(server, link.exit())) {
                PortalTether.tie(server, owned.getKey(), link, false);
                portals.fall(fallen(server, link.entry()) ? link.entry() : link.exit());
                syncOwner(server, owned.getKey());
            } else {
                carryThrough(server, link.entry(), link.exit());
                carryThrough(server, link.exit(), link.entry());
            }
        }
    }

    private static void carryThrough(MinecraftServer server, Portal from, Portal to) {
        ServerLevel level = server.getLevel(from.block().dimension());
        if (level == null || !level.isLoaded(from.mouth())) {
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, from.mouthBox())) {
            if (!from.equals(player.getData(GooAttachments.PORTAL_ARRIVAL).orElse(null))) {
                carry(server, player, to);
            }
        }
    }

    /**
     * Frees each player who came out of a portal and now stands clear of its mouth.
     *
     * @param server the server
     */
    static void freeThoseWhoLeft(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Portal> arrival = player.getData(GooAttachments.PORTAL_ARRIVAL);
            if (arrival.isPresent() && (!player.level().dimension().equals(arrival.get().block().dimension())
                    || !player.getBoundingBox().intersects(arrival.get().mouthBox()))) {
                player.setData(GooAttachments.PORTAL_ARRIVAL, Optional.empty());
            }
        }
    }

    /**
     * Carries a player out of a portal, facing out of it, leaving an
     * afterimage where they stood and where they arrive.
     *
     * @param server the server
     * @param player the traveller
     * @param to     the portal they come out of
     */
    static void carry(MinecraftServer server, ServerPlayer player, Portal to) {
        ServerLevel target = server.getLevel(to.block().dimension());
        if (target == null) {
            return;
        }
        Optional<Vec3> arrival = arrivalAt(target, player, to);
        if (arrival.isEmpty()) {
            return;
        }
        afterimage(player, player.position());
        player.setData(GooAttachments.PORTAL_ARRIVAL, Optional.of(to));
        float yaw = to.face().getAxis().isHorizontal() ? to.face().toYRot() : player.getYRot();
        player.teleport(new TeleportTransition(target, arrival.get(), Vec3.ZERO, yaw, player.getXRot(), Set.of(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        afterimage(player, arrival.get());
    }

    /**
     * Where a traveller comes out of a portal: its mouth, or the cell below or
     * above it where the mouth alone cannot hold the body.
     *
     * @param level  the portal's level
     * @param player the traveller
     * @param to     the portal
     * @return the feet, empty where the body fits at none of them
     */
    static Optional<Vec3> arrivalAt(ServerLevel level, ServerPlayer player, Portal to) {
        BlockPos mouth = to.mouth();
        if (fits(level, player, mouth)) {
            return Optional.of(Vec3.atBottomCenterOf(mouth));
        }
        return ARRIVALS.stream().map(mouth::relative).filter(cell -> fits(level, player, cell))
                .map(Vec3::atBottomCenterOf).findFirst();
    }

    private static boolean fits(ServerLevel level, ServerPlayer player, BlockPos cell) {
        return level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(
                Vec3.atBottomCenterOf(cell)));
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

    private static void syncOwner(MinecraftServer server, UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            PortalChoice.sync(player);
        }
    }

    private static void afterimage(ServerPlayer player, Vec3 at) {
        EntityVisuals.sendToWatchers(player, new AfterimagePayload(player.getId(), at, GooTypes.QUANTUM,
                AFTERIMAGE_LIFE_TICKS));
        player.level().playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f,
                TRAVEL_PITCH);
    }
}
