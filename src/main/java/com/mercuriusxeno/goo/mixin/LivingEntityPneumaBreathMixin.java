package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.typhoon.PneumaBreathing;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's Pneuma answers each breath vanilla takes while the player cannot
 * breathe, before the vanilla air does: vanilla takes it through
 * {@link LivingEntity#decreaseAirSupply}, which NeoForge 26.1 fires no breathe
 * event around (decision pneuma-overfills-the-air-meter-with-a-brief-airless-immunity).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPneumaBreathMixin {

    /**
     * Leaves the air where it stands for a player whose Pneuma covered the breath.
     *
     * @param currentSupply the air before the breath
     * @param cir           mixin callback info; set to the air unchanged when Pneuma covered it
     */
    @Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
    private void goo$pneumaCoversTheBreath(int currentSupply, CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && PneumaBreathing.coversBreath(player)) {
            cir.setReturnValue(currentSupply);
        }
    }
}
