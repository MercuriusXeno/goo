package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of a spontaneous cast sized by its charge: on release it lands
 * at the aimed target that tick at the size the hold reached, cut back to the
 * largest the player's goo pays for, charging the cost that size's volume
 * prices, with nothing thrown.
 * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
 */
public final class GooChargedCastHandler {

    private GooChargedCastHandler() {
    }

    /**
     * Casts a sized ability at the target its release aimed: refused whole for
     * a target past the throw range or a size the goo cannot pay for.
     *
     * @param player    the casting player
     * @param gooType   the ability's goo type
     * @param ability   the ability, one the player may use with its reagents held
     * @param aimed     the target the crosshair aimed as the release let go
     * @param heldTicks the ticks the use key was held
     */
    static void cast(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability,
                     GooThrowPayload aimed, int heldTicks) {
        if (player.getEyePosition().distanceTo(aimed.targetPoint()) > GooThrowHandler.MAX_RANGE) {
            return;
        }
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(gooType, 0);
        // goo-is-an-int-and-only-the-remainder-is-a-fraction: a fractional cost scales exactly, the size's price rounded up
        double unitCost = ability.cost().exactIn(GooValues.of(player.level())).toDouble();
        double size = ChargeSize.affordable(ability.delivery().sizeAt(heldTicks), unitCost, holdings);
        int cost = ChargeSize.costAt(unitCost, size);
        if (!GooSourceScanner.hasEnough(player, gooType, cost)) {
            return;
        }
        GooSourceScanner.deplete(player, gooType, cost);
        GooThrowHandler.consumeReagents(player, aimed.abilityId(), gooType);
        Direction face = GooThrowHandler.directionFromOrdinal(aimed.targetFace());
        AbilityImpact.land(player.level(), aimed.targetPos(), gooType, face == null ? Direction.UP : face, ability,
                aimed.targetPoint(), size);
    }
}
