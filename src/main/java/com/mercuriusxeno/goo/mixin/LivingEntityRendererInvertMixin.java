package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.Invert;
import com.mercuriusxeno.goo.ability.nether.InvertPresentation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * An inverted player's model draws upside down, feet on the ceiling, as
 * vanilla draws a mob named Dinnerbone, in third person and to every other
 * player; the gravity modifier it reads syncs to every client tracking the
 * player.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererInvertMixin {

    /**
     * Draws an inverted entity upside down.
     *
     * @param upsideDown vanilla's answer, by name
     * @param entity     the entity drawn
     * @return true where the entity draws upside down
     */
    @ModifyReturnValue(method = "isEntityUpsideDown", at = @At("RETURN"))
    private boolean goo$drawsTheInvertedUpsideDown(boolean upsideDown,
                                                    @Local(argsOnly = true) LivingEntity entity) {
        return upsideDown || InvertPresentation.of(Invert.isInverted(entity), false).modelUpsideDown();
    }
}
