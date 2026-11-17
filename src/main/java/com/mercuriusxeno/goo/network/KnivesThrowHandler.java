package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for a Knives throw: during a standing hold with a knife
 * held, the knife formed last leaves its hover slot along the thrower's look
 * as a projectile, aimed at no mob, and leaves the hold, its price spent, so
 * a held left click empties the hold in the reverse of its fill. Dart's
 * javelin leaves the same way on its own flight, its cost paid as it leaves.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-throw-one-per-two-ticks-while-left-click-is-held
 * decision knives-and-dart-function-as-projectiles
 * decision knives-do-not-seek-you-just-throw-them
 * decision dart-does-not-seek-or-highlight
 */
public final class KnivesThrowHandler {

    private KnivesThrowHandler() {
    }

    /**
     * Handles the throw payload on the server thread.
     *
     * @param payload the throw payload
     * @param context the network context
     */
    public static void handle(KnivesThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                leftClick(player, payload.down());
            }
        });
    }

    /**
     * Left click goes down or lifts during the player's hold: going down it
     * throws a knife at once, and the hold throws one every 2 ticks after
     * until it lifts.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param player the player
     * @param down   true as left click goes down, false as it lifts
     */
    public static void leftClick(ServerPlayer player, boolean down) {
        GooServerState.of(player.level().getServer()).knifeHolds().leftClick(player, down);
        if (down) {
            throwKnife(player);
        }
    }

    /**
     * Throws one knife of the player's standing hold along its look.
     *
     * @param player the throwing player
     */
    public static void throwKnife(ServerPlayer player) {
        GooServerState state = GooServerState.of(player.level().getServer());
        KnifeHolds holds = state.knifeHolds();
        holds.heldBy(player).ifPresent(held -> {
            int inTheHold = holds.formed(player);
            Delivery delivery = held.ability().delivery();
            // dart-stacks-three-javelins-one-per-ten-ticks: a javelin's cost is spent as it is thrown
            if (inTheHold <= 0 || delivery.stacksJavelins() && !AbilityCharges.charge(player, held.gooType(),
                    held.ability().cost().exactIn(GooValues.of(player.level())))) {
                return;
            }
            Vec3 look = player.getViewVector(1f);
            state.thrownKnives().launch(player, held.gooType(), held.ability().id().toString(),
                    ThrownKnife.launchPointOf(player.getEyePosition(), player.getViewXRot(1f), player.getViewYRot(1f),
                            GooCasters.casterOnTheRight(player), inTheHold, delivery.stacksJavelins()),
                    ThrownKnife.launchVelocityAlong(look, delivery.throwSpeed()), delivery.stacksJavelins(),
                    delivery.formSounds().thrown());
            holds.threw(player, 1);
        });
    }
}
