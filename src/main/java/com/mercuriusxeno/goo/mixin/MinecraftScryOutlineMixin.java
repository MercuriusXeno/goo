package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.LeaderSense;
import com.mercuriusxeno.goo.client.ability.ScryPaintedMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mob a Scry front painted wears the glowing outline, drawn through walls,
 * on the caster's client alone, until its linger runs out (operator ruling
 * 2026-11-06: Scry paints and highlights mobs), and so does a mob Leader
 * senses; the game has no event for which entities glow.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision leader-outlines-every-mob-through-walls
 */
@Mixin(Minecraft.class)
public abstract class MinecraftScryOutlineMixin {

    /**
     * @param entity the entity the frame asks about
     * @param cir    the callback holding vanilla's answer
     */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void goo$outlineScryPainted(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ScryPaintedMobs.CLIENT.isPaintedNow(entity.getId()) || LeaderSense.CLIENT.appearsGlowing(entity.getId())) {
            cir.setReturnValue(true);
        }
    }
}
