package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.ability.typhoon.AirbornMotion;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client's half of Float: the client moves its own player, so after each
 * of its ticks under Float it flies the player by its keys in midair,
 * levitating included, caps its fall, and draws an elytra glide faster along
 * the look. Creative flight, water, lava and a mount keep their own motion.
 * tailwind-and-airborn-merge-into-float
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AirbornClient {

    private AirbornClient() {
    }

    /**
     * Flies the local player and caps its fall, or speeds its glide, while Float stands.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Airborn flight = player.getData(GooAttachments.AIRBORN);
        if (!flight.standsAt(player.level().getGameTime())) {
            return;
        }
        if (player.isFallFlying()) {
            // tailwind-and-airborn-merge-into-float: the glide is drawn along the look
            player.setDeltaMovement(AirbornMotion.glided(player.getDeltaMovement(), player.getLookAngle(), flight));
        } else if (movesOnItsOwn(player)) {
            player.setDeltaMovement(AirbornMotion.flown(player.getDeltaMovement(), flightInput(player.input.keyPresses),
                    player.getLookAngle(), player.getYRot(), player.onGround(), flight));
        }
    }

    /**
     * The flight a player's keys ask for: strafe left as x, jump up and sneak
     * down as y, forward as z, each -1 to 1.
     *
     * @param keys the movement keys held
     * @return the flight input
     */
    static Vec3 flightInput(Input keys) {
        return new Vec3(axis(keys.left(), keys.right()), axis(keys.jump(), keys.shift()),
                axis(keys.forward(), keys.backward()));
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }

    private static boolean movesOnItsOwn(LocalPlayer player) {
        boolean swims = player.isInWater() || player.isInLava();
        return !player.getAbilities().flying && !swims && !player.isPassenger();
    }
}
