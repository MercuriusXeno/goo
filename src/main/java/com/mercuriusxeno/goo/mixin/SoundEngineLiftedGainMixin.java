package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.sound.LiftedSoundGain;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Lifts the gain vanilla's sound engine clamps to one for a sound recorded
 * too quietly to be heard under that clamp, wherever the engine reads a
 * playing sound's gain: as it starts the sound, in NeoForge's
 * playWithoutEvent, which holds the body of vanilla's play, and each tick
 * after, which a one-shot sound never reaches. The lift multiplies vanilla's
 * clamped gain, so the sliders still scale it.
 * drain-lifts-its-chosen-sounds-past-the-volume-cap
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineLiftedGainMixin {

    /**
     * Multiplies vanilla's clamped gain by the sound's lift.
     *
     * @param self     the sound engine
     * @param volume   the instance's volume
     * @param source   the category it plays under
     * @param original vanilla's clamped gain
     * @param instance the sound whose gain is read
     * @return the gain the channel plays at
     */
    @WrapOperation(method = {
        "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        "playWithoutEvent(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"
    }, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),
            require = 2)
    private float goo$liftsTheQuietSounds(SoundEngine self, float volume, SoundSource source,
                                          Operation<Float> original, @Local(argsOnly = true) SoundInstance instance) {
        return LiftedSoundGain.lifted(instance.getIdentifier(), original.call(self, volume, source));
    }
}
