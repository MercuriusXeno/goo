package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.DragSize;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Server side of a world ability sized at will: on the release of its drag
 * it opens at the pinned epicenter at the radius dragged, cut back to the
 * largest the player's goo pays for, charging the cost that radius's volume
 * prices, with no flight (decision black-hole-leaves-a-compression-sphere).
 */
public final class GooDragCastHandler {

    private GooDragCastHandler() {
    }

    /**
     * Handles the release on the server thread.
     *
     * @param payload the release
     * @param context the network context
     */
    public static void handle(GooDragCastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                cast(player, payload);
            }
        });
    }

    /**
     * Opens a sized ability for a player: refused whole for a glove-less
     * player, an unknown type, an ability the player may not use or that is
     * not sized at will, a missing reagent, a pin past the throw range or a
     * radius the goo cannot pay for.
     *
     * @param player  the casting player
     * @param payload the release
     */
    public static void cast(ServerPlayer player, GooDragCastPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !withinReach(player, payload)) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        if (castable(player, ability)) {
            open(player, payload, gooType, ability);
        }
    }

    /**
     * Whether the release is one the player can make: a glove held, a finite
     * radius, and the pin within the throw range of the player's eye.
     *
     * @param player  the casting player
     * @param payload the release
     * @return true for a release in reach
     */
    private static boolean withinReach(ServerPlayer player, GooDragCastPayload payload) {
        return GooThrowHandler.validateGlove(player) && Double.isFinite(payload.radius())
                && player.getEyePosition().distanceTo(payload.pinPoint()) <= GooThrowHandler.MAX_RANGE;
    }

    /**
     * Whether an ability may be cast by its drag: one the player may use,
     * sized at will, with every reagent it consumes held.
     *
     * @param player  the casting player
     * @param ability the named ability, or null where the player may not use it
     * @return true when the cast goes ahead
     */
    private static boolean castable(ServerPlayer player, @Nullable AbilityDefinition ability) {
        return ability != null && ability.hasTag(AbilityTags.DRAG_SIZED)
                && GooThrowHandler.holdsReagents(player, ability);
    }

    /**
     * Opens the ability at the radius dragged, cut back to what the goo pays
     * for, charging that radius's price.
     *
     * @param player  the casting player
     * @param payload the release
     * @param gooType the ability's goo type
     * @param ability the ability
     */
    private static void open(ServerPlayer player, GooDragCastPayload payload, ResourceKey<GooTypeDefinition> gooType,
                             AbilityDefinition ability) {
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
        double radius = DragSize.affordable(Math.max(DragSize.MIN_RADIUS, payload.radius()), ability.cost(), holdings);
        int cost = DragSize.costAt(ability.cost(), radius);
        if (!GooSourceScanner.hasEnough(player, gooType, cost)) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, cost);
        GooThrowHandler.consumeReagents(player, payload.abilityId(), gooType);
        AbilityImpact.land(player.level(), payload.pinBlock(), gooType, payload.face(), ability, payload.pinPoint(),
                radius);
    }
}
