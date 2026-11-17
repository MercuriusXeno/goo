package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for a Knives hold beginning or ending: a hold begins
 * for a held glove naming a known ability whose charge forms knives in
 * stages, which the player may use; letting go ends it, throwing nothing.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-form-in-fibonacci-stages-to-twelve
 */
public final class KnivesHoldHandler {

    private KnivesHoldHandler() {
    }

    /**
     * Handles the hold payload on the server thread.
     *
     * @param payload the hold payload
     * @param context the network context
     */
    public static void handle(KnivesHoldPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                hold(player, payload);
            }
        });
    }

    /**
     * Begins or ends the player's Knives hold.
     *
     * @param player  the holding player
     * @param payload the hold payload
     */
    public static void hold(ServerPlayer player, KnivesHoldPayload payload) {
        KnifeHolds holds = GooServerState.of(player.level().getServer()).knifeHolds();
        if (!payload.holding()) {
            holds.end(player);
            return;
        }
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !GooThrowHandler.validateGlove(player)) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        if (ability != null && ability.delivery().charge().formsInStages()) {
            holds.begin(player, ability.delivery().charge());
        }
    }
}
