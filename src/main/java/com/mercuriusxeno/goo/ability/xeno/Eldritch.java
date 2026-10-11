package com.mercuriusxeno.goo.ability.xeno;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The eldritch state a player holds: while it stands, the out-of-phase are
 * drawn to the player and can target the player, until the game time it
 * fades at.
 * eldritch-sight-reveals-the-out-of-phase
 *
 * @param expiresAt the game time the state fades at; zero when none stands
 */
public record Eldritch(long expiresAt) {

    /** No eldritch state. */
    public static final Eldritch NONE = new Eldritch(0L);
    /** The fade time of a state held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the state with the player. */
    public static final MapCodec<Eldritch> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Eldritch::expiresAt)
    ).apply(inst, Eldritch::new));

    /** Syncs the state to the player holding it, whose client draws the out-of-phase by it. */
    public static final StreamCodec<ByteBuf, Eldritch> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Eldritch::expiresAt,
            Eldritch::new);

    /**
     * Whether the state stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the state fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: the state stands until its held effect ends,
     * which clears it.
     *
     * @return the state after the cast
     */
    public Eldritch hold() {
        return new Eldritch(NEVER_EXPIRES);
    }

    /**
     * A drunk brew: the state stands for the brew's duration, or as long as
     * it already stood where that is longer.
     *
     * @param ticks    the brew's duration
     * @param gameTime the game time of the drink
     * @return the state after the drink
     */
    public Eldritch brew(int ticks, long gameTime) {
        return new Eldritch(Math.max(expiresAt, gameTime + ticks));
    }
}
