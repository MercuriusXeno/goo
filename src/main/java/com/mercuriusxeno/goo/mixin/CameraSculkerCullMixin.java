package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.sculk.SculkerSwim;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Lets a diver see through the stone it swims in the way a spectator does:
 * with the camera inside a solid block, the terrain is culled as for a
 * spectator, so the caves and open space around draw through the stone
 * rather than the stone hiding them.
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(Camera.class)
public abstract class CameraSculkerCullMixin {

    /**
     * Culls terrain as for a spectator while the local player dives.
     *
     * @param spectator vanilla's answer, whether the local player spectates
     * @return true for a spectator or a diver
     */
    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean goo$diverCullsAsASpectator(boolean spectator) {
        Minecraft mc = Minecraft.getInstance();
        return spectator || mc.player != null && SculkerSwim.dives(mc.player);
    }
}
