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
 * Lays the dark side's night over lunar's day timeline, on the server's level and the client's alike, since each
 * builds its attributes through vanilla's default layers.
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemDarkSideMixin {

    /**
     * Adds the dark side's layers once lunar's default layers stand.
     *
     * @param builder the attribute system being built
     * @param level   the level it is built for
     * @param ci      the callback
     */
    @Inject(method = "addDefaultLayers", at = @At("TAIL"))
    private static void goo$addDarkSideNight(EnvironmentAttributeSystem.Builder builder, Level level,
            CallbackInfo ci) {
        if (level.dimension() == Lunar.LEVEL) {
            LunarNight.addDarkSideLayers(builder);
        }
    }
}
