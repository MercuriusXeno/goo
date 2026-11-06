package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.DiveSenses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Draws the vanilla outline around an entity a diver senses, for the moment
 * it is sensed.
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDiveOutlineMixin {

    /**
     * Answers whether an entity draws its outline: yes while a diver senses it, vanilla's answer otherwise.
     *
     * @param original vanilla's answer
     * @param entity   the entity drawn
     * @return whether the entity draws its outline
     */
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean goo$diverSensesIt(boolean original, Entity entity) {
        return original || DiveSenses.outlines(entity);
    }
}
