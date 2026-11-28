package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.durable.ShieldHolds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * An entity holding Durable's Shield up blocks with it through vanilla's
 * own blocking path, as with a vanilla shield raised: vanilla's arc, its
 * arrows, its blocked sounds and what disables it all hold.
 * durable-goo-forms-a-shield-at-will-and-shield-bashes
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGooShieldMixin {

    /**
     * Answers a vanilla shield for an entity blocking with nothing of its own while its goo shield stands.
     *
     * @param original what vanilla blocks with
     * @return vanilla's answer, else the goo shield's stack, else null
     */
    @ModifyReturnValue(method = "getItemBlockingWith", at = @At("RETURN"))
    private @Nullable ItemStack goo$blocksWithTheGooShield(@Nullable ItemStack original) {
        return original != null ? original : ShieldHolds.blockingStack((LivingEntity) (Object) this);
    }
}
