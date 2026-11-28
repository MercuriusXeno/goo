package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.LunarRandomState;
import com.mercuriusxeno.goo.lunar.LunarSeam;
import com.mercuriusxeno.goo.lunar.RepeatingNoise;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out every noise of lunar's random state repeating every 15,000,000 blocks on x and z: the noises its density
 * functions, its goo fields and its surface rule read, so lunar's ground meets itself at the seam. Every other
 * level's random state hands out vanilla's noises.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(RandomState.class)
public abstract class RandomStateLunarRepeatsMixin {

    @Shadow
    @Final
    private HolderGetter<NormalNoise> noises;

    @Shadow
    @Final
    private PositionalRandomFactory random;

    @Unique
    private boolean goo$repeats;

    @Unique
    private Map<ResourceKey<NormalNoise>, Noise> goo$repeatingNoises;

    /**
     * Reads whether this is lunar's random state before its surface system reads its first noise.
     *
     * @param ci the callback
     */
    @Inject(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/level/levelgen/RandomState;router:"
                    + "Lnet/minecraft/world/level/levelgen/NoiseRouter;", shift = At.Shift.AFTER))
    private void goo$readsWhetherItRepeats(CallbackInfo ci) {
        goo$repeats = LunarRandomState.makingLunars();
        goo$repeatingNoises = new ConcurrentHashMap<>();
    }

    /**
     * Hands out lunar's noises repeating at the seam.
     *
     * @param key the noise's key
     * @param cir the callback taking the noise
     */
    @Inject(method = "getOrCreateNoise", at = @At("HEAD"), cancellable = true)
    private void goo$handsOutRepeatingNoise(ResourceKey<NormalNoise> key, CallbackInfoReturnable<Noise> cir) {
        if (goo$repeats) {
            cir.setReturnValue(goo$repeatingNoises.computeIfAbsent(key,
                    name -> RepeatingNoise.repeating(Noises.instantiate(noises, random, name), LunarSeam.PERIOD)));
        }
    }
}
