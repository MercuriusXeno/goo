package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Lux a player holds: night vision without particles, and the glow
 * glisten on the mob under the crosshair, until the game time it fades at
 * (decision lux-night-vision-without-particles).
 *
 * @param expiresAt the game time Lux fades at; zero when none stands
 */
public record Lux(long expiresAt) {

    /** No Lux. */
    public static final Lux NONE = new Lux(0L);
    /** The fade time of Lux held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves Lux with the player. */
    public static final MapCodec<Lux> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Lux::expiresAt)
    ).apply(inst, Lux::new));

    /**
     * Whether Lux stands at a game time.
     *
     * @param gameTime the game time
     * @return true before Lux fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: Lux stands until its held effect ends, which
     * clears it (decision self-effects-trickle-until-ended).
     *
     * @return Lux after the cast
     */
    public Lux hold() {
        return new Lux(NEVER_EXPIRES);
    }

    /**
     * A drunk brew: Lux stands for the brew's duration, or as long as it
     * already stood where that is longer.
     *
     * @param ticks    the brew's duration
     * @param gameTime the game time of the drink
     * @return Lux after the drink
     */
    public Lux brew(int ticks, long gameTime) {
        return new Lux(Math.max(expiresAt, gameTime + ticks));
    }
}
