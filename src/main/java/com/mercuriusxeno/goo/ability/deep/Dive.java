package com.mercuriusxeno.goo.ability.deep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The dive a player holds until the game time it ends at: while it stands
 * the player swims through solid blocks and sees the way the deep sees.
 * Saved with the player and synced to its client, which moves the player
 * and draws the vision.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 *
 * @param expiresAt the game time the dive ends at; zero when none stands
 */
public record Dive(long expiresAt) {

    /** No dive. */
    public static final Dive NONE = new Dive(0L);
    /** The end time of a dive held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the dive with the player. */
    public static final MapCodec<Dive> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Dive::expiresAt)
    ).apply(inst, Dive::new));

    /** Syncs the dive to the owning client, which moves the player and draws the vision. */
    public static final StreamCodec<ByteBuf, Dive> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Dive::expiresAt, Dive::new);

    /**
     * Whether the dive stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the dive ends
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: the dive stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @return the dive after the cast
     */
    public Dive hold() {
        return new Dive(NEVER_EXPIRES);
    }

    /**
     * A drunk brew: the dive stands for the brew's duration, or as long as
     * it already stood where that is longer.
     *
     * @param ticks    the brew's duration
     * @param gameTime the game time of the drink
     * @return the dive after the drink
     */
    public Dive brew(int ticks, long gameTime) {
        return new Dive(Math.max(expiresAt, gameTime + ticks));
    }
}
