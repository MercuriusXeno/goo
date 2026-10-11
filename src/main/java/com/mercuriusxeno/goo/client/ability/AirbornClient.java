package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.ability.typhoon.AirbornMotion;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client's half of Airborn: the client moves its own player, so after
 * each of its ticks under Airborn it steers the player toward its movement
 * input in midair, levitating included, and caps its fall, and draws an
 * elytra glide faster along the look. Creative flight, water, lava and a
 * mount keep their own motion.
 * airborn-steerable-levitation-and-soft-falls
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AirbornClient {

    private AirbornClient() {
    }

    /**
     * Steers the local player and caps its fall, or speeds its glide, while
     * Airborn stands.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Airborn airborn = player.getData(GooAttachments.AIRBORN);
        if (!airborn.standsAt(player.level().getGameTime())) {
            return;
        }
        if (player.isFallFlying()) {
            // airborn-steerable-levitation-and-soft-falls: faster flight, the glide is drawn along the look
            player.setDeltaMovement(AirbornMotion.glided(player.getDeltaMovement(), player.getLookAngle(), airborn));
        } else if (movesOnItsOwn(player)) {
            player.setDeltaMovement(AirbornMotion.moved(player.getDeltaMovement(),
                    new Vec3(player.xxa, 0, player.zza), player.getYRot(), player.onGround(), airborn));
        }
    }

    private static boolean movesOnItsOwn(LocalPlayer player) {
        boolean swims = player.isInWater() || player.isInLava();
        return !player.getAbilities().flying && !swims && !player.isPassenger();
    }
}
