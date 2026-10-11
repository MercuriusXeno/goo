package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Carries a player standing at one quantum anchor to its partner: they
 * arrive in the first open spot beside the partner, an afterimage left at
 * each end, and the partner holds them until they step away from it, so
 * arriving never carries them straight back.
 * quantum-anchors-link-two-points
 */
public final class AnchorTravel {

    /** How long each afterimage lingers, in ticks. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;
    /** The travel sound's pitch, a little above the enderman's, so an anchor reads apart from a blink. */
    private static final float TRAVEL_PITCH = 1.4f;

    /** The spots beside an anchor a traveller may arrive in, tried in order. */
    private static final List<Direction> ARRIVALS = List.of(
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    private AnchorTravel() {
    }

    /**
     * Carries every player standing at an anchor, and not just arrived
     * there, to the anchor's partner; a player who arrived there and has
     * stepped away is freed to be carried on their next visit.
     *
     * @param level  the anchor's level
     * @param anchor the anchor's cell
     */
    public static void carryFrom(ServerLevel level, BlockPos anchor) {
        GlobalPos here = GlobalPos.of(level.dimension(), anchor);
        freeThoseWhoLeft(level, here);
        QuantumAnchors anchors = QuantumAnchors.get(level);
        Optional<GlobalPos> partner = anchors.partnerOf(here);
        if (partner.isEmpty()) {
            return;
        }
        if (fallen(level, partner.get())) {
            anchors.fall(partner.get());
            return;
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, QuantumAnchors.reachOf(anchor))) {
            if (!here.equals(player.getData(GooAttachments.ANCHOR_ARRIVAL).orElse(null))) {
                carry(level, player, partner.get());
            }
        }
    }

    /**
     * Frees each player who arrived at an anchor and now stands clear of it.
     *
     * @param level  the anchor's level
     * @param anchor the anchor
     */
    static void freeThoseWhoLeft(ServerLevel level, GlobalPos anchor) {
        for (ServerPlayer player : level.players()) {
            if (anchor.equals(player.getData(GooAttachments.ANCHOR_ARRIVAL).orElse(null))
                    && !player.getBoundingBox().intersects(QuantumAnchors.reachOf(anchor.pos()))) {
                player.setData(GooAttachments.ANCHOR_ARRIVAL, Optional.empty());
            }
        }
    }

    /**
     * Carries a player to an anchor, leaving an afterimage where they stood and where they arrive.
     *
     * @param level  the level the player stands in
     * @param player the player
     * @param to     the anchor they travel to
     */
    static void carry(ServerLevel level, ServerPlayer player, GlobalPos to) {
        ServerLevel target = level.getServer().getLevel(to.dimension());
        if (target == null) {
            return;
        }
        Optional<Vec3> arrival = arrivalBeside(target, player, to.pos());
        if (arrival.isEmpty()) {
            return;
        }
        Vec3 stood = player.position();
        afterimage(level, player, stood);
        player.setData(GooAttachments.ANCHOR_ARRIVAL, Optional.of(to));
        player.teleport(new TeleportTransition(target, arrival.get(), Vec3.ZERO, player.getYRot(), player.getXRot(),
                Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        afterimage(target, player, arrival.get());
    }

    /**
     * The first spot beside an anchor where the player's body meets no collision.
     *
     * @param level  the anchor's level
     * @param player the traveller
     * @param anchor the anchor's cell
     * @return the feet position, empty when every spot is walled in
     */
    static Optional<Vec3> arrivalBeside(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        for (Direction side : ARRIVALS) {
            Vec3 feet = Vec3.atBottomCenterOf(anchor.relative(side));
            if (level.noCollision(player, player.getDimensions(player.getPose()).makeBoundingBox(feet))) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether an anchor's prism no longer stands, read only where its chunk is loaded.
     *
     * @param level  any level of the server
     * @param anchor the anchor
     * @return true for an anchor whose prism is gone
     */
    static boolean fallen(ServerLevel level, GlobalPos anchor) {
        ServerLevel there = level.getServer().getLevel(anchor.dimension());
        return there == null || there.isLoaded(anchor.pos())
                && !(there.getBlockEntity(anchor.pos()) instanceof PrismBlockEntity);
    }

    private static void afterimage(ServerLevel level, ServerPlayer player, Vec3 at) {
        EntityVisuals.sendToWatchers(player, new AfterimagePayload(player.getId(), at, GooTypes.QUANTUM,
                AFTERIMAGE_LIFE_TICKS));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, TRAVEL_PITCH);
    }
}
