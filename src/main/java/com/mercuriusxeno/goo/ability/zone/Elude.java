package com.mercuriusxeno.goo.ability.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Elude a player holds: each mob or projectile hit they would take moves
 * them instead to a random safe spot within the distance, until the game time
 * it fades at. Saved with the player.
 * Decision elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage.
 *
 * @param distance  the radius each escape lands within
 * @param expiresAt the game time it fades at; zero when none stands
 */
public record Elude(float distance, long expiresAt) {

    /** No Elude. */
    public static final Elude NONE = new Elude(0f, 0L);
    /** The fade time of Elude held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_DISTANCE = "distance";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the Elude with the player. */
    public static final MapCodec<Elude> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_DISTANCE).forGetter(Elude::distance),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Elude::expiresAt)
    ).apply(inst, Elude::new));

    /**
     * Whether Elude stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: Elude stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @param castDistance the cast's escape radius
     * @return the Elude after the cast
     */
    public Elude hold(float castDistance) {
        return new Elude(castDistance, NEVER_EXPIRES);
    }
}
