package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Server-side handler for a stacking hold beginning or ending, Knives' or
 * Dart's: a hold begins for a held glove naming a known ability whose charge
 * stacks, which the player may use and holds every reagent of, its reagents
 * taken once for the hold; a knife's price is the ability's cost, paid as it
 * forms, and a javelin forms free, its cost spent at its throw. Letting go
 * ends it, throwing nothing, as logging out does.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision knives-pay-per-knife-as-each-forms
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 */
@EventBusSubscriber(modid = Goo.MODID)
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
        if (gooType != null && GooThrowHandler.validateGlove(player)) {
            begin(player, holds, gooType, GooThrowHandler.usableAbility(player, payload.abilityId(), gooType));
        }
    }

    /**
     * Begins a hold of an ability that forms knives, its reagents held and taken once for the hold.
     *
     * @param player  the holding player
     * @param holds   the server's Knives holds
     * @param gooType the ability's goo type
     * @param ability the usable ability the payload names, or null for none
     */
    private static void begin(ServerPlayer player, KnifeHolds holds, ResourceKey<GooTypeDefinition> gooType,
                              @Nullable AbilityDefinition ability) {
        if (ability == null || !ability.delivery().charge().stacks()
                || !GooThrowHandler.holdsReagents(player, ability)) {
            return;
        }
        GooThrowHandler.consumeReagents(player, ability.id().toString(), gooType);
        // dart-stacks-three-javelins-one-per-ten-ticks: a javelin costs nothing to form, its cost spent at its throw
        GooFraction price = ability.delivery().stacksJavelins() ? GooFraction.ZERO
                : ability.cost().exactIn(GooValues.of(player.level()));
        holds.begin(player, new KnifeHolds.Held(gooType, ability), price);
    }

    /**
     * Ends the hold of a player logging out, before the player saves, so the
     * knives it held are refunded.
     *
     * @param event the logout
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().getServer() != null) {
            GooServerState.of(player.level().getServer()).knifeHolds().end(player);
        }
    }
}
