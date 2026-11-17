package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side handler for a Knives throw: during a standing hold with a knife
 * held, the knife formed last leaves its hover slot along the thrower's look
 * as a projectile, aimed at no mob, and leaves the hold, its price spent.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-and-dart-function-as-projectiles
 * decision knives-do-not-seek-you-just-throw-them
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
                throwKnife(player);
            }
        });
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
            if (inTheHold <= 0) {
                return;
            }
            Vec3 look = player.getViewVector(1f);
            state.thrownKnives().launch(player, held.gooType(), held.ability().id().toString(),
                    ThrownKnife.launchPointOf(player.getEyePosition(), look, inTheHold),
                    ThrownKnife.launchVelocityAlong(look));
            holds.threw(player, 1);
        });
    }
}
