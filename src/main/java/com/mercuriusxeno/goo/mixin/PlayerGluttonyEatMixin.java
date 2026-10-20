package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.gluttony.GluttonyEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Lets a glutton start eating at a full bar, which vanilla refuses any food
 * not always edible, so the meal can bank past full as overhunger.
 * gluttony-overheals-and-overhungers
 */
@Mixin(Player.class)
public abstract class PlayerGluttonyEatMixin {

    /**
     * Answers whether the player may eat: yes while Gluttony stands,
     * vanilla's answer otherwise.
     *
     * @param original vanilla's answer
     * @return whether the player may start eating
     */
    @ModifyReturnValue(method = "canEat", at = @At("RETURN"))
    private boolean goo$gluttonEatsPastFull(boolean original) {
        return original || GluttonyEvents.eatsPastFull((Player) (Object) this);
    }
}
