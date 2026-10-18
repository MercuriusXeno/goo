package com.mercuriusxeno.goo.type;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.awt.Color;

/**
 * A goo type's color drifting around the hue wheel over time, at each channel's own saturation
 * and value. A type with no cycle answers its channels unchanged at every tick.
 * infinity-is-rainbow-goo
 *
 * @param ticks the length of one full turn of the wheel, 0 for a type whose color stands still
 */
public record HueCycle(int ticks) {

    /**
     * JSON key of {@link #ticks}.
     */
    public static final String HUE_CYCLE_TICKS = "hue_cycle_ticks";
    /**
     * The cycle of a type whose JSON names none: its color stands still.
     */
    public static final HueCycle NONE = new HueCycle(0);

    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int HUE = 0;
    private static final int SATURATION = 1;
    private static final int VALUE = 2;

    /**
     * The cycle's fields as they sit flat in the type JSON body, each optional.
     */
    public static final MapCodec<HueCycle> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf(HUE_CYCLE_TICKS, 0).forGetter(HueCycle::ticks)
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
        return Color.HSBtoRGB(hsb[HUE] + share, hsb[SATURATION], hsb[VALUE]) & RGB_MASK;
    }
}
