package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerSilence;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips a silent player's fall sound, the thud of the block it lands on.
 * sculker-mutes-your-movement-sounds
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySculkerSilenceMixin {

    /**
     * Skips the fall sound of a player moving in silence.
     *
     * @param info the callback
     */
    @Inject(method = "playBlockFallSound", at = @At("HEAD"), cancellable = true)
    private void goo$silentFall(CallbackInfo info) {
        if (SculkerSilence.movesSilently((LivingEntity) (Object) this)) {
            info.cancel();
        }
    }
}
