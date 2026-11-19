package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarNight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Darkens lunar's sky as at midnight over the dark side for every brightness read that takes vanilla's sky darkening,
 * mob spawning's light test and daylight burning among them. Vanilla's darkening is one number a level holds for the
 * whole dimension, so Level answers its own brightness read here in place of LevelReader's default.
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
@Mixin(Level.class)
public abstract class LevelDarkSideNightMixin implements LevelReader {

    /**
     * The brightness at a block, its sky light darkened as the dark side or the light side's clock darkens it.
     *
     * @param pos the block
     * @return the brightness, 0 to 15
     */
    @Override
    public int getMaxLocalRawBrightness(BlockPos pos) {
        Level level = (Level) (Object) this;
        int darken = getSkyDarken();
        return getMaxLocalRawBrightness(pos,
                level.dimension() == Lunar.LEVEL ? LunarNight.skyDarkenAt(pos.getX(), darken) : darken);
    }
}
