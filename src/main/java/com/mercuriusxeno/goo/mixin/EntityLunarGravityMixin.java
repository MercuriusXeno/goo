package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.LunarGravity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Everything falls in lunar under one sixth of the gravity it falls under in the overworld: vanilla's every fall, a
 * living entity's through its gravity attribute, an item's, a projectile's, reads Entity.getGravity, so scaling its
 * answer by the entity's dimension reaches all of them, on the client as on the server, so a player's own movement
 * predicts what the server computes; a holder of Moonrock falls under lunar's sixth anywhere.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 * decision lunar-stone-carries-one-sixth-gravity-while-held
 */
@Mixin(Entity.class)
public abstract class EntityLunarGravityMixin {

    /**
     * Scales an entity's gravity by its dimension's.
     *
     * @param gravity the gravity vanilla answers
     * @return the gravity the entity falls under where it stands
     */
    @ModifyReturnValue(method = "getGravity", at = @At("RETURN"))
    private double goo$lunarHalvesGravity(double gravity) {
        return gravity * LunarGravity.scaleOf((Entity) (Object) this);
    }
}
