package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.PhaseLook;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Draws a phased entity's body grey and translucent in place of its own:
 * the body's render type turns to the phase type over the same skin, and its
 * tint drops to the phase alpha. The layers over the body draw as they did.
 * phase-shares-a-plane-between-the-phased
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPhaseMixin {

    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    /**
     * Turns a phased body's render type to the phase type over its skin;
     * a body drawing nothing still draws nothing.
     *
     * @param state           the entity's render state
     * @param isBodyVisible   whether the body shows
     * @param forceTransparent whether vanilla draws it faint
     * @param appearGlowing   whether it glows
     * @param cir             the render type vanilla chose
     */
    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    private void goo$phasedBodyType(LivingEntityRenderState state, boolean isBodyVisible, boolean forceTransparent,
            boolean appearGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (cir.getReturnValue() != null && PhaseLook.isPhased(state)) {
            cir.setReturnValue(PhaseLook.bodyType(getTextureLocation(state)));
        }
    }

    /**
     * Drops a phased body's tint to the phase alpha.
     *
     * @param tint  the tint vanilla computed
     * @param state the entity's render state
     * @return the tint the body draws under
     */
    @ModifyExpressionValue(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;multiply(II)I"))
    private int goo$phasedBodyTint(int tint, @Local(argsOnly = true) LivingEntityRenderState state) {
        return PhaseLook.isPhased(state) ? PhaseLook.bodyTint(tint) : tint;
    }
}
