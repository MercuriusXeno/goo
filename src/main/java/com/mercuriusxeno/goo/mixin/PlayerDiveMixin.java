package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.DiveSwim;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a diver through solid blocks: right after vanilla sets the player's
 * no-clip from spectator mode each tick, a diver inside solid blocks or
 * pressing into them takes it too, on the client that moves the
 * player and the server that checks the move alike, and no-clip keeps the
 * stone from suffocating it.
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(Player.class)
public abstract class PlayerDiveMixin {

    /**
     * Turns no-clip on for a diver passing through solid blocks.
     *
     * @param info the callback
     */
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER, ordinal = 0))
    private void goo$diverPassesThrough(CallbackInfo info) {
        Player player = (Player) (Object) this;
        if (!player.noPhysics && DiveSwim.passesThrough(player)) {
            player.noPhysics = true;
        }
    }
}
