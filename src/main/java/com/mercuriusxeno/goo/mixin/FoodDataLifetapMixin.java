package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.hex.LifetapEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Holds food regeneration off a lifetapped player: food's two regeneration
 * branches each ask whether the player is hurt, and while a lifetap stands
 * the answer reads no, so neither branch heals nor spends the hunger its
 * heal would cost. Every other heal, a potion's or an ability's, still lands.
 * lifetap-trades-regen-for-leech
 */
@Mixin(FoodData.class)
public abstract class FoodDataLifetapMixin {

    /**
     * Answers food's regeneration check: not hurt while a lifetap stands,
     * vanilla's answer otherwise.
     *
     * @param player   the player food ticks for
     * @param original vanilla's hurt check
     * @return whether food may regenerate the player's health
     */
    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"))
    private boolean goo$lifetapHoldsFoodRegen(ServerPlayer player, Operation<Boolean> original) {
        return !LifetapEvents.blocksFoodRegen(player) && original.call(player);
    }
}
