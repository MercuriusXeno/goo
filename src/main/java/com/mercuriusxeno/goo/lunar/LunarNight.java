package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * Lunar's night on the dark side: there the sky darkens as at midnight at every hour, to the server's brightness
 * reads, mob spawning and daylight burning among them, and to the client's light, while the light side keeps its day
 * and night on the overworld's clock.
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
public final class LunarNight {

    /** The sky darkening of lunar's midnight: 15 less lunar_day's night sky light level of 4. */
    static final int NIGHT_SKY_DARKEN = 11;
    /** The sky darkening from which vanilla's Level reads it dark outside. */
    private static final int DARK_OUTSIDE_DARKEN = 4;
    /** lunar_day's night sky light factor, which the client's light map reads; boxed, as its layer answers it. */
    private static final Float NIGHT_SKY_LIGHT_FACTOR = 0.24F;
    /** lunar_day's night sky light color, opaque; boxed, as its layer answers it. */
    private static final Integer NIGHT_SKY_LIGHT_COLOR = 0xFF7A7AFF;

    private LunarNight() {
    }

    /**
     * The sky darkening at a lunar x: midnight's on the dark side, and the light side's own where the clock stands.
     *
     * @param x               the x, in blocks
     * @param lightSideDarken the darkening the overworld's clock gives the light side now
     * @return the darkening there
     */
    public static int skyDarkenAt(double x, int lightSideDarken) {
        return Lunar.isDarkSide(x) ? NIGHT_SKY_DARKEN : lightSideDarken;
    }

    /**
     * Whether the sky reads as night at a lunar x, as vanilla reads it dark outside: always on the dark side, and on
     * the light side by the overworld's clock.
     *
     * @param x               the x, in blocks
     * @param lightSideDarken the darkening the overworld's clock gives the light side now
     * @return true at night
     */
    public static boolean isNightAt(double x, int lightSideDarken) {
        return skyDarkenAt(x, lightSideDarken) >= DARK_OUTSIDE_DARKEN;
    }

    /**
     * Adds the dark side's night to lunar's environment attributes, after its day timeline: the client's light map
     * reads midnight's sky light factor and color wherever the camera stands on the dark side.
     *
     * @param attributes lunar's attribute system as it is built
     */
    public static void addDarkSideLayers(EnvironmentAttributeSystem.Builder attributes) {
        attributes.addPositionalLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR,
                (lightSide, position, biomes) -> Lunar.isDarkSide(position.x) ? NIGHT_SKY_LIGHT_FACTOR : lightSide);
        attributes.addPositionalLayer(EnvironmentAttributes.SKY_LIGHT_COLOR,
                (lightSide, position, biomes) -> Lunar.isDarkSide(position.x) ? NIGHT_SKY_LIGHT_COLOR : lightSide);
    }
}
