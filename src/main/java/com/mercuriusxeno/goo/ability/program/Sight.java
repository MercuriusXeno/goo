package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The fungal sight a player holds: Fungal Shift reaches the factor farther
 * and the player sees fungus through walls within that reach until the
 * game time it fades at (decision sight-lengthens-shift-and-outlines-fungus).
 *
 * @param factor    what Fungal Shift's range is multiplied by
 * @param expiresAt the game time the sight fades at; zero when none stands
 */
public record Sight(float factor, long expiresAt) {

    /** No sight. */
    public static final Sight NONE = new Sight(1f, 0L);
    /** The fade time of a sight held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_FACTOR = "factor";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the sight with the player. */
    public static final MapCodec<Sight> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.FLOAT.fieldOf(FIELD_FACTOR).forGetter(Sight::factor),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Sight::expiresAt)
    ).apply(inst, Sight::new));

    /** Syncs the sight to the owning client, which draws the outlines. */
    public static final StreamCodec<ByteBuf, Sight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Sight::factor,
            ByteBufCodecs.VAR_LONG, Sight::expiresAt,
            Sight::new);

    /**
     * Whether the sight stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the sight fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * The factor Fungal Shift's range takes at a game time: the sight's while
     * it stands, one after.
     *
     * @param gameTime the game time
     * @return the range factor
     */
    public float factorAt(long gameTime) {
        return standsAt(gameTime) ? factor : 1f;
    }

    /**
     * A cast from the glove: the sight stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @param castFactor the cast's range factor
     * @return the sight after the cast
     */
    public Sight hold(float castFactor) {
        return new Sight(castFactor, NEVER_EXPIRES);
    }

    /**
     * A drunk brew: the sight stands for the brew's duration, or as long as
     * it already stood where that is longer.
     *
     * @param brewFactor the brew's range factor
     * @param ticks      the brew's duration
     * @param gameTime   the game time of the drink
     * @return the sight after the drink
     */
    public Sight brew(float brewFactor, int ticks, long gameTime) {
        return new Sight(brewFactor, Math.max(expiresAt, gameTime + ticks));
    }
}
