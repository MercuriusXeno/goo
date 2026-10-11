package com.mercuriusxeno.goo.ability.weird;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The wobble a mob holds until it fades: while it stands, the mob's attacks
 * deal no damage and knock their target back instead. Saved with the mob.
 * weird-bounces-and-softens-harm
 *
 * @param expiresAt the game time the wobble fades at
 */
public record Wobbled(long expiresAt) {

    /** No wobble: the mob strikes as it always does. */
    public static final Wobbled NONE = new Wobbled(0L);

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the wobble with the mob. */
    public static final MapCodec<Wobbled> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Wobbled::expiresAt)
    ).apply(inst, Wobbled::new));

    /**
     * Whether the wobble still stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the wobble fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }
}
