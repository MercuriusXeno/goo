package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A player in lunar breathes as one whose eyes are under water: vanilla's air tick reads the eyes wet there, so the
 * air drains a breath a tick through decreaseAirSupply, drowning lands when it runs out, and water breathing, a
 * turtle helmet's water breathing and Pneuma's reserve hold it as they do underwater. Only real water dismounts a
 * rider, so a mount carries its rider across lunar.
 * decision astral-air-drains-as-underwater
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLunarAirlessMixin {

    /**
     * Reads a player's eyes in lunar as under water, for the air tick alone.
     *
     * @param eyesInWater whether the eyes stand in water
     * @return true too where the player stands in lunar
     */
    @ModifyExpressionValue(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean goo$lunarAirReadsAsUnderwater(boolean eyesInWater) {
        return eyesInWater || Lunar.isAirless((LivingEntity) (Object) this);
    }

    /**
     * Dismounts a rider only where its eyes stand in real water.
     *
     * @param vehicle  the vehicle ridden
     * @param original vanilla's answer whether the vehicle dismounts underwater
     * @return vanilla's answer where the eyes stand in water, else false
     */
    @WrapOperation(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;dismountsUnderwater()Z"))
    private boolean goo$onlyRealWaterDismounts(Entity vehicle, Operation<Boolean> original) {
        return original.call(vehicle) && ((LivingEntity) (Object) this).isEyeInFluid(NeoForgeMod.WATER_TYPE.value());
    }
}
