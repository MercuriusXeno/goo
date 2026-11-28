package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.sound.ChannelGain;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets a sound channel's gain through goo's, which raises the source's
 * gain ceiling to a gain lifted past one, so the lift reaches the mix.
 * drain-plays-its-chosen-sounds-as-loud-as-blaze-fire
 */
@Mixin(Channel.class)
public abstract class ChannelLiftedGainMixin {

    @Shadow
    @Final
    private int source;

    /**
     * Sets the gain and its ceiling in vanilla's place.
     *
     * @param volume the gain
     * @param ci     the callback, cancelled since the gain is set
     */
    @Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
    private void goo$setsTheLiftedGain(float volume, CallbackInfo ci) {
        ChannelGain.apply(source, volume);
        ci.cancel();
    }
}
