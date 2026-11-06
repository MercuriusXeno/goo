package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.DiveSwim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client's half of the dive's swim: the client moves its own player, so
 * after each of its ticks inside solid blocks under the dive it steers the
 * player weightless along its input and look, jump lifting and sneak
 * sinking, and keeps no fall building.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DiveClient {

    private DiveClient() {
    }

    /**
     * Swims the local player through the stone it is inside while its dive stands.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !DiveSwim.dives(player)
                || !DiveSwim.insideSolid(player)) {
            return;
        }
        Input keys = player.input.keyPresses;
        double rise = (keys.jump() ? 1 : 0) - (keys.shift() ? 1 : 0);
        player.setDeltaMovement(DiveSwim.swum(player.getDeltaMovement(), player.zza, player.xxa, rise,
                player.getLookAngle()));
        player.resetFallDistance();
    }
}
