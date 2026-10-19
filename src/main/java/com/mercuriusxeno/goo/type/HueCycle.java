package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.Color;

/**
 * A goo type's color drifting around the hue wheel over time, at each channel's own saturation
 * and value. A full-circle cycle turns the whole wheel; a narrower swing sways back and forth
 * across an arc centered on the base hue. A type with no cycle answers its channels unchanged.
 * infinity-is-rainbow-goo
 * yore-gets-opalescent-iridescence-back
 *
 * @param ticks        the length of one cycle, 0 for a type whose color stands still
 * @param swingDegrees the width of the arc the hue covers, a full circle to turn the whole wheel
 */
public record HueCycle(int ticks, float swingDegrees) {

    /**
     * JSON key of {@link #ticks}.
     */
    public static final String HUE_CYCLE_TICKS = "hue_cycle_ticks";
    /**
     * JSON key of {@link #swingDegrees}.
     */
    public static final String HUE_SWING_DEGREES = "hue_swing_degrees";
    /**
     * The swing of a cycle whose JSON names none: the whole wheel.
     */
    public static final float FULL_CIRCLE = 360f;
    /**
     * The cycle of a type whose JSON names none: its color stands still.
     */
    public static final HueCycle NONE = new HueCycle(0, FULL_CIRCLE);

    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int HUE = 0;
    private static final int SATURATION = 1;
    private static final int VALUE = 2;
    private static final float HALF = 0.5f;

    /**
     * The cycle's fields as they sit flat in the type JSON body, each optional.
     */
    public static final MapCodec<HueCycle> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(HUE_CYCLE_TICKS, 0).forGetter(HueCycle::ticks),
            Codec.floatRange(0f, FULL_CIRCLE).optionalFieldOf(HUE_SWING_DEGREES, FULL_CIRCLE)
                    .forGetter(HueCycle::swingDegrees)
    ).apply(instance, HueCycle::new));

    /**
     * A channel's color at a tick of the cycle.
     *
     * @param rgb  the channel's base RGB
     * @param tick the tick to color, any non-negative count
     * @return the RGB rotated by the tick's share of the cycle
     */
    public int rotate(int rgb, long tick) {
        if (ticks == 0) {
            return rgb;
        }
        float share = (float) Math.floorMod(tick, ticks) / ticks;
        float[] hsb = Color.RGBtoHSB((rgb >> RED_SHIFT) & CHANNEL_MASK, (rgb >> GREEN_SHIFT) & CHANNEL_MASK,
                rgb & CHANNEL_MASK, null);
        return Color.HSBtoRGB(hsb[HUE] + hueOffset(share), hsb[SATURATION], hsb[VALUE]) & RGB_MASK;
    }

    /**
     * How far round the wheel the hue sits at a share of the cycle, in turns: a full-circle cycle
     * turns at an even pace, a narrower swing sways across its arc and back.
     *
     * @param share the tick's share of the cycle, 0 inclusive to 1 exclusive
     * @return the hue offset in turns of the wheel
     */
    private float hueOffset(float share) {
        if (swingDegrees >= FULL_CIRCLE) {
            return share;
        }
        return (float) (swingDegrees * HALF / FULL_CIRCLE * Math.sin(Math.TAU * share));
    }
}
