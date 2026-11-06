package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.HeldMobs;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * A witch a goo hold holds drinks no potion. Witch.aiStep picks and drinks
 * its potions ahead of super.aiStep, behind an isAlive check and no AI check,
 * so turning her AI off leaves her drinking; the check reads false for a held
 * witch, and super.aiStep still runs, so she still falls and her gauge still
 * thaws.
 * diagnose-and-correct-held-mobs-still-acting
 */
@Mixin(Witch.class)
public abstract class WitchHeldDrinksNoPotionMixin {

    /**
     * Skips the witch's own potion turn while a goo hold holds her.
     *
     * @param alive vanilla's isAlive answer
     * @return true where her potion turn runs
     */
    @ModifyExpressionValue(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Witch;isAlive()Z"))
    private boolean goo$heldWitchSkipsHerPotionTurn(boolean alive) {
        return HeldMobs.ownActionRuns(alive, HeldMobs.isHeld((Witch) (Object) this));
    }
}
