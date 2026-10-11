package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.entity.SwapBody;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;

/**
 * Quantum's swap: with no body standing, a cast leaves one where the player
 * stands; with one standing, a cast puts the player back in it, at its spot
 * and with the health it held, and leaves a fresh body where they stood.
 * swap-body-stands-until-reverted
 */
public final class BodySwap {

    /** How long each afterimage lingers, in ticks. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;

    private BodySwap() {
    }

    /**
     * Casts the swap for a player.
     *
     * @param level  the player's level
     * @param player the caster
     */
    public static void cast(ServerLevel level, ServerPlayer player) {
        Optional<SwapBody> standing = standingBody(level, player);
        SwapBody left = leaveBody(level, player);
        if (standing.isPresent()) {
            swapInto(level, player, standing.get());
        }
        player.setData(GooAttachments.SWAP_BODY, Optional.of(new SwapBodyRef(left.getUUID(),
                GlobalPos.of(level.dimension(), left.blockPosition()))));
    }

    /**
     * The body a player has standing, where its level holds it loaded.
     *
     * @param level  any level of the server
     * @param player the player
     * @return the body, empty for none or one out of reach
     */
    static Optional<SwapBody> standingBody(ServerLevel level, ServerPlayer player) {
        return player.getData(GooAttachments.SWAP_BODY).flatMap(ref -> {
            ServerLevel there = level.getServer().getLevel(ref.at().dimension());
            Entity body = there == null ? null : there.getEntity(ref.body());
            return body instanceof SwapBody swap && swap.isAlive() ? Optional.of(swap) : Optional.empty();
        });
    }

    private static SwapBody leaveBody(ServerLevel level, ServerPlayer player) {
        SwapBody body = SwapBody.build(GooEntities.SWAP_BODY.get(), level);
        body.standFor(player);
        level.addFreshEntity(body);
        return body;
    }

    private static void swapInto(ServerLevel level, ServerPlayer player, SwapBody body) {
        ServerLevel there = (ServerLevel) body.level();
        Vec3 from = player.position();
        Vec3 to = body.position();
        float health = Math.min(body.heldHealth(), player.getMaxHealth());
        body.discard();
        afterimage(player, from);
        player.teleport(new TeleportTransition(there, to, Vec3.ZERO, body.getYRot(), body.getXRot(), Set.of(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        if (health > 0f) {
            player.setHealth(health);
        }
        afterimage(player, to);
    }

    private static void afterimage(ServerPlayer player, Vec3 at) {
        EntityVisuals.sendToWatchers(player, new AfterimagePayload(player.getId(), at, GooTypes.QUANTUM,
                AFTERIMAGE_LIFE_TICKS));
    }
}
