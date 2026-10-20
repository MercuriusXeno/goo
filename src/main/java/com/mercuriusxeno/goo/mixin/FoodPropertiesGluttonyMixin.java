package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.gluttony.GluttonyEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Banks what a glutton's meal pushes past full hunger, read before the meal
 * clamps the bar at full and the excess is lost.
 * gluttony-overheals-and-overhungers
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesGluttonyMixin {

    /**
     * Banks the meal's overflow before it lands.
     *
     * @param level      the level
     * @param entity     the eater
     * @param stack      the food eaten
     * @param consumable the food's consumable
     * @param callback   the injection callback
     */
    @Inject(method = "onConsume", at = @At("HEAD"))
    private void goo$gluttonBanksTheMeal(Level level, LivingEntity entity, ItemStack stack, Consumable consumable,
            CallbackInfo callback) {
        if (entity instanceof ServerPlayer player) {
            GluttonyEvents.bankMeal(player, ((FoodProperties) (Object) this).nutrition());
        }
    }
}
