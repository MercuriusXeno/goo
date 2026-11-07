package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client's half of the dive's swim: the client moves its own player, so
 * after each of its ticks passing through solid blocks under the dive it
 * steers the player weightless along its input and look, jump lifting and
 * sneak sinking, stopped short of bedrock and the world's edge, and keeps no
 * fall building.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerClient {

    private SculkerClient() {
    }

    /**
     * The keys a client player holds: the local player's own, none for any other.
     *
     * @param player a player on this client
     * @return the keys it holds
     */
    public static Input heldKeys(Player player) {
        return player instanceof LocalPlayer local ? local.input.keyPresses : Input.EMPTY;
    }

    /**
     * Swims the local player through the stone it passes through while its Sculker stands.
     *
     * @param event the player tick
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !SculkerSwim.passesThrough(player)) {
            return;
        }
        Input keys = player.input.keyPresses;
        double rise = (keys.jump() ? 1 : 0) - (keys.shift() ? 1 : 0);
        player.setDeltaMovement(SculkerSwim.swimStroke(player, player.zza, player.xxa, rise));
        player.resetFallDistance();
    }
}
