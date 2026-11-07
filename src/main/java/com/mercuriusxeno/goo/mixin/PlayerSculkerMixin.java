package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a diver through solid blocks: right after vanilla sets the player's
 * no-clip from spectator mode each tick, a player in Sculker's dive takes it
 * too, on the client that moves the player and the server that checks the
 * move alike, and holds the swimming pose for the dive's length, so the
 * diver swims through the stone as through water.
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * sculker-swims-solid-blocks-and-surfaces-at-will
 */
@Mixin(Player.class)
public abstract class PlayerSculkerMixin {

    /**
     * Turns no-clip on for a diver passing through solid blocks.
     *
     * @param info the callback
     */
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER, ordinal = 0))
    private void goo$diverPassesThrough(CallbackInfo info) {
        Player player = (Player) (Object) this;
        boolean diving = SculkerSwim.passesThrough(player);
        if (!player.noPhysics && diving) {
            player.noPhysics = true;
        }
        SculkerSwim.holdTheSwimPose(player, diving);
    }
}
