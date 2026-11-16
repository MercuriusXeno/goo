package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.HostDeaths;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offers a death to the Host goo near it once the death's game event has
 * reached every sculk catalyst, which claims a death by consuming its
 * experience, and before the death drops its experience.
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHostDeathMixin {

    /**
     * Hands the death to Host goo just before the loot and experience drop.
     *
     * @param source what killed the mob
     * @param ci     the callback
     */
    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void goo$hostTakesTheDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity mob = (LivingEntity) (Object) this;
        if (mob.level() instanceof ServerLevel level) {
            HostDeaths.claim(level, mob, source);
        }
    }
}
