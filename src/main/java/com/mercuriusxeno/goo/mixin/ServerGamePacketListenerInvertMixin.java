package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.CeilingGround;
import com.mercuriusxeno.goo.ability.nether.Invert;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * The server reads an inverted player's move away from the ceiling as the
 * jump and its move toward the ceiling as the fall: vanilla's move up starts
 * the server's jump and clears the fall's distance, so for an inverted player
 * the move down does both instead, and its landing on the ceiling hurts.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerInvertMixin {

    @Shadow
    public ServerPlayer player;

    /**
     * Answers whether the move rose off the player's ground: up for an
     * upright player, down for an inverted one.
     *
     * @param movedUpwards vanilla's answer, the move up
     * @param yDist        the vertical move
     * @return whether the move rose off the player's ground
     */
    @ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), name = "movedUpwards")
    private boolean goo$movedOffTheCeiling(boolean movedUpwards, @Local(name = "yDist") double yDist) {
        return Invert.isInverted(player) ? CeilingGround.towardGround(yDist, true) > 0 : movedUpwards;
    }
}
