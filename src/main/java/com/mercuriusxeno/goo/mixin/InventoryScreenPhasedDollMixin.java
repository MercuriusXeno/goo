package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.PhaseLook;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the inventory's paper doll of a phased body, so it draws a readable
 * ghost, its own skin faded, in place of the world's grey outlined phase look.
 * phased-things-draw-as-a-ghostly-image
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenPhasedDollMixin {

    /**
     * Marks the paper doll's render state once vanilla extracted it.
     *
     * @param entity the entity the doll draws
     * @param cir    the render state vanilla extracted
     */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private static void goo$markPhasedPaperDoll(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
        PhaseLook.markPaperDoll(cir.getReturnValue());
    }
}
