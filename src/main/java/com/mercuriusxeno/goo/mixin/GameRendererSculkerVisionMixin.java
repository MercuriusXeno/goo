package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.SculkerVision;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs Sculker's vision on the level just before the game renderer clears the
 * level's depth to draw the hand, so the vision dims the world by its true
 * distance; the game renderer's own post effect slot runs after that clear
 * and would read every pixel as sky.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererSculkerVisionMixin {

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    /**
     * Runs the vision before the hand's depth clear.
     *
     * @param info the callback
     */
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
            ordinal = 0))
    private void goo$senseBeforeTheHand(CallbackInfo info) {
        SculkerVision.senseBeforeTheHand(resourcePool);
    }
}
