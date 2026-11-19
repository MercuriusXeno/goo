package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.Invert;
import com.mercuriusxeno.goo.ability.nether.InvertPresentation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * An inverted player's model draws upside down, feet on the ceiling, in
 * third person and to every other player. The player renderer answers its own
 * upside down check, by name and cape, without the living renderer's, so the
 * flip LivingEntityRendererInvertMixin lays for a mob is laid here for a
 * player.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererInvertMixin {

    /**
     * Draws an inverted player upside down.
     *
     * @param upsideDown vanilla's answer, by name and cape
     * @param avatar     the player drawn
     * @return true where the player draws upside down
     */
    @ModifyReturnValue(method = "isEntityUpsideDown(Lnet/minecraft/world/entity/Avatar;)Z", at = @At("RETURN"))
    private boolean goo$drawsTheInvertedPlayerUpsideDown(boolean upsideDown, @Local(argsOnly = true) Avatar avatar) {
        return upsideDown || InvertPresentation.of(Invert.isInverted(avatar), false).modelUpsideDown();
    }
}
