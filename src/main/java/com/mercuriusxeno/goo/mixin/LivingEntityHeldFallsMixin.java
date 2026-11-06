package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.HeldMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * A mob a goo hold holds still falls. LivingEntity.aiStep runs travel, the
 * step that applies gravity, only behind isEffectiveAi, which reads false for
 * a mob whose AI is off, and every hold turns it off; the check reads true for
 * a held mob, and travel runs on no input, so the mob falls and walks nowhere.
 * diagnose-and-correct-held-mobs-still-acting
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHeldFallsMixin {

    /**
     * Runs travel for a mob a hold's mark holds.
     *
     * @param effectiveAi vanilla's isEffectiveAi answer at the travel check
     * @return true where travel runs
     */
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEffectiveAi()Z", ordinal = 1))
    private boolean goo$heldMobTravels(boolean effectiveAi) {
        return HeldMobs.travelRuns(effectiveAi, goo$heldByAHold());
    }

    /**
     * Hands a held mob's travel no input.
     *
     * @param input vanilla's travel input
     * @return the input travel runs on
     */
    @ModifyArg(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private Vec3 goo$heldMobTravelsOnNoInput(Vec3 input) {
        return HeldMobs.travelInput(input, goo$heldByAHold());
    }

    @Unique
    private boolean goo$heldByAHold() {
        return (Object) this instanceof Mob mob && HeldMobs.isHeldByAHold(mob);
    }
}
