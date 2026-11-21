package com.mercuriusxeno.goo.lunar;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3fc;
import java.util.function.LongSupplier;

/**
 * Lunar's day and night at an x, on both halves of the moon alike: the light follows the sun's height there, read
 * from lunar_day's light tracks at the day tick LunarSun answers, so the sky darkens, twilight ramps and the light map
 * dims as the overworld's do under the same sun. The server's brightness reads, mob spawning and daylight burning
 * among them, and the client's sun and light map all read it.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
public final class LunarNight {

    /** The sky light level of full day, from which vanilla's darkening subtracts. */
    private static final float FULL_SKY_LIGHT = 15F;
    /** The sky darkening from which vanilla's Level reads it dark outside. */
    private static final int DARK_OUTSIDE_DARKEN = 4;
    /** The overworld clock's ticks a day, the period lunar_day's light tracks run on. */
    private static final int DAY_TICKS = 24_000;
    private static final int WHITE = 0xFFFFFFFF;
    /** lunar_day's night sky light color, opaque. */
    private static final int NIGHT_SKY_LIGHT_COLOR = 0xFF7A7AFF;

    /** lunar_day's sky_light_level track: the share of full sky light, by day tick. */
    private static final DayTrack SKY_LIGHT_SHARE = new DayTrack(new int[] {133, 11_867, 13_670, 22_330},
            new float[] {1F, 1F, 0.26666668F, 0.26666668F});
    /** lunar_day's sky_light_factor track, which the client's light map reads. */
    private static final DayTrack SKY_LIGHT_FACTOR = new DayTrack(new int[] {730, 11_270, 13_140, 22_860},
            new float[] {1F, 1F, 0.24F, 0.24F});
    /** lunar_day's sky_light_color track's keyframes, white by day and blue by night; its ticks are the factor's. */
    private static final int[] SKY_LIGHT_COLORS = {WHITE, WHITE, NIGHT_SKY_LIGHT_COLOR, NIGHT_SKY_LIGHT_COLOR};

    private LunarNight() {
    }

    /**
     * The sky darkening at a lunar x now, as vanilla darkens from its sky light level: none under a high sun,
     * midnight's 11 with the sun underfoot, and lunar_day's twilight ramp between.
     *
     * @param x                  the x, in blocks
     * @param overworldClockTime the overworld clock's total ticks
     * @return the darkening there, 0 to 11
     */
    public static int skyDarkenAt(double x, long overworldClockTime) {
        float skyLight = FULL_SKY_LIGHT * SKY_LIGHT_SHARE.sample(LunarSun.dayTickAt(x, overworldClockTime));
        return (int) (FULL_SKY_LIGHT - skyLight);
    }

    /**
     * Whether the sky reads as night at a lunar x now, as vanilla reads it dark outside.
     *
     * @param x                  the x, in blocks
     * @param overworldClockTime the overworld clock's total ticks
     * @return true at night
     */
    public static boolean isNightAt(double x, long overworldClockTime) {
        return skyDarkenAt(x, overworldClockTime) >= DARK_OUTSIDE_DARKEN;
    }

    /**
     * The layer answering the sun angle at a position from the sun over its x, in place of lunar_day's answer on the
     * overworld's clock.
     *
     * @param overworldClock the overworld clock's total ticks, read as the layer is
     * @return the layer
     */
    public static EnvironmentAttributeLayer.Positional<Float> sunAngleLayer(LongSupplier overworldClock) {
        return (timeline, position, biomes) -> (float) LunarSun.sunAngleDegrees(position.x,
                overworldClock.getAsLong());
    }

    /**
     * The layer answering the sky light factor the client's light map reads at a position, from the sun over its x,
     * in place of lunar_day's answer on the overworld's clock.
     *
     * @param overworldClock the overworld clock's total ticks, read as the layer is
     * @return the layer
     */
    public static EnvironmentAttributeLayer.Positional<Float> skyLightFactorLayer(LongSupplier overworldClock) {
        return (timeline, position, biomes) -> SKY_LIGHT_FACTOR.sample(LunarSun.dayTickAt(position.x,
                overworldClock.getAsLong()));
    }

    /**
     * The layer answering the sky light color at a position, from the sun over its x, in place of lunar_day's answer
     * on the overworld's clock.
     *
     * @param overworldClock the overworld clock's total ticks, read as the layer is
     * @return the layer
     */
    public static EnvironmentAttributeLayer.Positional<Vector3fc> skyLightColorLayer(LongSupplier overworldClock) {
        return (timeline, position, biomes) -> ARGB.vector3fFromRGB24(SKY_LIGHT_FACTOR.sampleColor(
                LunarSun.dayTickAt(position.x, overworldClock.getAsLong()), SKY_LIGHT_COLORS));
    }

    /**
     * Lays the sun's angle and its light over lunar's day timeline, after it: every positional read of them answers
     * the sun over the read's x.
     *
     * @param attributes     lunar's attribute system as it is built
     * @param overworldClock the overworld clock's total ticks, read as each layer is
     */
    public static void addSunLayers(EnvironmentAttributeSystem.Builder attributes, LongSupplier overworldClock) {
        attributes.addPositionalLayer(EnvironmentAttributes.SUN_ANGLE, sunAngleLayer(overworldClock));
        attributes.addPositionalLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, skyLightFactorLayer(overworldClock));
        attributes.addPositionalLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, skyLightColorLayer(overworldClock));
    }

    /**
     * One of lunar_day's light tracks on the overworld's 24,000 tick day, sampled as vanilla's keyframe sampler
     * samples it: linearly between keyframes, wrapping from the last keyframe round to the first.
     *
     * @param ticks  the keyframes' day ticks, rising
     * @param values the keyframes' values
     */
    private record DayTrack(int[] ticks, float[] values) {

        float sample(double dayTick) {
            Segment segment = segmentAt(dayTick);
            return Mth.lerp(segment.alpha(), values[segment.from()], values[segment.to()]);
        }

        int sampleColor(double dayTick, int[] colors) {
            Segment segment = segmentAt(dayTick);
            return ARGB.srgbLerp(segment.alpha(), colors[segment.from()], colors[segment.to()]);
        }

        private Segment segmentAt(double dayTick) {
            int last = ticks.length - 1;
            for (int next = 0; next <= last; next++) {
                if (dayTick < ticks[next]) {
                    int from = next == 0 ? last : next - 1;
                    double fromTick = next == 0 ? ticks[last] - DAY_TICKS : ticks[from];
                    return new Segment(from, next, alphaBetween(fromTick, ticks[next], dayTick));
                }
            }
            return new Segment(last, 0, alphaBetween(ticks[last], ticks[0] + DAY_TICKS, dayTick));
        }

        private static float alphaBetween(double fromTick, double toTick, double dayTick) {
            return (float) ((dayTick - fromTick) / (toTick - fromTick));
        }
    }

    /**
     * Where a day tick stands between two keyframes.
     *
     * @param from  the keyframe before it
     * @param to    the keyframe after it
     * @param alpha how far from the first toward the second, 0 to 1
     */
    private record Segment(int from, int to, float alpha) {
    }
}
