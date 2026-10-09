package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player under Undead counts as undead for healing and harming: vanilla's
 * heal-or-harm effects read {@link LivingEntity#isInvertedHealAndHarm}, so
 * harming heals the player and healing harms it, as for a zombie
 * (decision undead-nether-hearts-burn-in-sunlight).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUndeadMixin {

    /**
     * Answers true for a player Undead stands on, and leaves every other
     * entity to vanilla's entity type tag.
     *
     * @param cir mixin callback info; set to true for an undead player
     */
    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void goo$undeadPlayerInvertsHealAndHarm(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && player.getData(GooAttachments.UNDEAD).stands()) {
            cir.setReturnValue(true);
        }
    }
}
