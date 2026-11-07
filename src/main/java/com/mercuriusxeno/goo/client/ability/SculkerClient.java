package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client's half of Sculker's swim: the client moves its own player, so
 * after each of its ticks in the dive it steers the player weightless along
 * its input and look, jump lifting and sneak sinking, kept inside the world,
 * and keeps no fall building.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * decision sculker-swims-solid-blocks-and-surfaces-at-will
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerClient {

    private SculkerClient() {
    }

    /**
     * Swims the local player while it is in the dive.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !SculkerSwim.passesThrough(player)) {
            return;
        }
        Input keys = player.input.keyPresses;
        player.setDeltaMovement(SculkerSwim.swimStroke(player, player.zza, player.xxa, SculkerSwim.riseOf(keys)));
        player.resetFallDistance();
    }
}
