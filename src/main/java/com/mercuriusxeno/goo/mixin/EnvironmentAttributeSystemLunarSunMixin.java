package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarNight;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lays lunar's sun over its day timeline, on the server's level and the client's alike, since each builds its
 * attributes through vanilla's default layers: the sun angle and its light at a position answer the sun over that
 * position's x, on both halves of the moon.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemLunarSunMixin {

    /**
     * Adds the sun's layers once lunar's dynamic layers, its timelines and weather, stand: 26.3 builds a level's
     * default layers as its static layers and then these.
     *
     * @param builder the attribute system being built
     * @param level   the level it is built for
     * @param ci      the callback
     */
    @Inject(method = "addDynamicLayers", at = @At("TAIL"))
    private static void goo$addLunarSun(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        if (level.dimension() == Lunar.LEVEL) {
            LunarNight.addSunLayers(builder, level::getOverworldClockTime);
        }
    }
}
