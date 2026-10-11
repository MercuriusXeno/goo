package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reads goo's own dimensions, Astral's goo:lunar and goo:solar, as stable.
 * Vanilla reads every dimension that is not vanilla-like as experimental,
 * so the two the mod's datapack adds to every world opened each world on
 * the Experimental Settings screen (operator ruling 2026-10-11: suppress
 * it). A dimension any other source adds still reads as vanilla reads it.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsGooStableMixin {

    /**
     * Answers stable for a dimension in goo's namespace.
     *
     * @param key  the dimension's key
     * @param stem the dimension
     * @param cir  the callback holding vanilla's answer
     */
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void goo$readGooDimensionsStable(ResourceKey<LevelStem> key, LevelStem stem,
                                                     CallbackInfoReturnable<Lifecycle> cir) {
        if (Goo.MODID.equals(key.identifier().getNamespace())) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
