package com.mercuriusxeno.goo.ability.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The lifetap a player holds: food no longer regenerates their health, and
 * every point of damage they deal heals them by the fraction, until the
 * game time it fades at.
 * lifetap-trades-regen-for-leech
 *
 * @param fraction  the share of the damage dealt the player heals
 * @param expiresAt the game time the lifetap fades at; zero when none stands
 */
public record Lifetap(float fraction, long expiresAt) {

    /** No lifetap. */
    public static final Lifetap NONE = new Lifetap(0f, 0L);
    /** The fade time of a lifetap held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_FRACTION = "fraction";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the lifetap with the player. */
    public static final MapCodec<Lifetap> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_FRACTION).forGetter(Lifetap::fraction),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Lifetap::expiresAt)
    ).apply(inst, Lifetap::new));

    /**
     * Whether the lifetap stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the lifetap fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * The health a hit heals at a game time: the fraction of the damage dealt
     * while the lifetap stands, nothing after.
     *
     * @param damage   the damage the hit dealt
     * @param gameTime the game time of the hit
     * @return the health healed
     */
    public float leechFor(float damage, long gameTime) {
        return standsAt(gameTime) ? Math.max(0f, damage) * fraction : 0f;
    }

    /**
     * A cast from the glove: the lifetap stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @param castFraction the cast's leech fraction
     * @return the lifetap after the cast
     */
    public static Lifetap hold(float castFraction) {
        return new Lifetap(castFraction, NEVER_EXPIRES);
    }

    /**
     * A drunk brew: the lifetap stands for the brew's duration, or as long as
     * it already stood where that is longer.
     *
     * @param brewFraction the brew's leech fraction
     * @param ticks        the brew's duration
     * @param gameTime     the game time of the drink
     * @return the lifetap after the drink
     */
    public Lifetap brew(float brewFraction, int ticks, long gameTime) {
        return new Lifetap(brewFraction, Math.max(expiresAt, gameTime + ticks));
    }
}
