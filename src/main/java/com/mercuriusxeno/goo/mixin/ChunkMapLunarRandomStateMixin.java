package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarRandomState;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Makes lunar's random state marked as lunar's, so every noise it hands out repeats at the seam.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapLunarRandomStateMixin {

    /**
     * Makes the level's random state, marked as lunar's for lunar.
     *
     * @param noises   the noise registry
     * @param seed     the level's seed
     * @param settings the level's noise settings
     * @param original vanilla's making of the random state
     * @param level    the level the chunk map serves
     * @return the random state
     */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;"
                    + "JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)"
                    + "Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState goo$makesLunarsRandomStateRepeat(HolderGetter<NormalNoise> noises, long seed,
                                                         NoiseGeneratorSettings settings,
                                                         Operation<RandomState> original,
                                                         @Local(argsOnly = true) ServerLevel level) {
        return LunarRandomState.make(level.dimension() == Lunar.LEVEL,
                () -> original.call(noises, seed, settings));
    }
}
