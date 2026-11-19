package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarNight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Darkens lunar's sky at a block as the sun's height over its x darkens it, for every brightness read that takes
 * vanilla's sky darkening, mob spawning's light test and daylight burning among them. Vanilla's darkening is one
 * number a level holds for the whole dimension, so Level answers its own brightness read here in place of
 * LevelReader's default.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 */
@Mixin(Level.class)
public abstract class LevelLunarSunlightMixin implements LevelReader {

    /**
     * The brightness at a block, its sky light darkened as lunar's sun over its x darkens it.
     *
     * @param pos the block
     * @return the brightness, 0 to 15
     */
    @Override
    public int getMaxLocalRawBrightness(BlockPos pos) {
        Level level = (Level) (Object) this;
        int darken = level.dimension() == Lunar.LEVEL
                ? LunarNight.skyDarkenAt(pos.getX(), level.getOverworldClockTime()) : getSkyDarken();
        return getMaxLocalRawBrightness(pos, darken);
    }
}
