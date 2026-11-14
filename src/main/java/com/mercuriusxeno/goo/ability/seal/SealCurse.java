package com.mercuriusxeno.goo.ability.seal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The warp curse Seal leaves on a mob, saved with it: how near a
 * player it may come before it warps, and how far each warp throws it. A
 * second Seal on the cursed mob exiles it from existence.
 * Decision zone-curses-with-ender-shimmer.
 *
 * @param radius how near a player the mob may come, in blocks, before it warps
 * @param range  the full width of a warp's random roll on each horizontal axis
 */
public record SealCurse(float radius, float range) {

    /** No curse: the mob stays where it is. */
    public static final SealCurse NONE = new SealCurse(0f, 0f);

    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_RANGE = "range";

    /** Saves the curse with the mob. */
    public static final MapCodec<SealCurse> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_RADIUS).forGetter(SealCurse::radius),
            Codec.FLOAT.fieldOf(FIELD_RANGE).forGetter(SealCurse::range)
    ).apply(inst, SealCurse::new));

    /**
     * Whether the curse stands on the mob.
     *
     * @return true for any curse but {@link #NONE}
     */
    public boolean stands() {
        return radius > 0f;
    }
}
