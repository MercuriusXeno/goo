package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lets a diver see through the stone it swims in the way a spectator does:
 * with the camera inside a solid block, the terrain is culled as for a
 * spectator, so the caves and open space around draw through the stone
 * rather than the stone hiding them.
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSculkerCullMixin {

    /**
     * Culls terrain as for a spectator while the local player dives.
     *
     * @param spectator vanilla's answer, whether the local player spectates
     * @return true for a spectator or a diver
     */
    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"),
            index = 2)
    private boolean goo$diverCullsAsASpectator(boolean spectator) {
        Minecraft mc = Minecraft.getInstance();
        return spectator || mc.player != null && SculkerSwim.dives(mc.player);
    }
}
