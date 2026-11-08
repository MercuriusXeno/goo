package com.mercuriusxeno.goo.ability.deep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Sculker a player holds until the game time it ends at: while it stands
 * the player swims through solid blocks and sees the way the deep sees.
 * Saved with the player and synced to its client, which moves the player
 * and draws the vision.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * decision dive-is-renamed-sculker
 *
 * @param expiresAt the game time the brew ends at; zero when none stands
 * @param diving    whether the player is in the dive now, swimming through solid blocks
 */
public record Sculker(long expiresAt, boolean diving) {

    /** No Sculker. */
    public static final Sculker NONE = new Sculker(0L, false);
    /** The end time of a dive held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_DIVING = "diving";

    /** Saves the dive with the player. */
    public static final MapCodec<Sculker> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Sculker::expiresAt),
            Codec.BOOL.optionalFieldOf(FIELD_DIVING, false).forGetter(Sculker::diving)
    ).apply(inst, Sculker::new));

    /** Syncs the dive to the owning client, which moves the player and draws the vision. */
    public static final StreamCodec<ByteBuf, Sculker> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Sculker::expiresAt, ByteBufCodecs.BOOL, Sculker::diving, Sculker::new);

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
    public Sculker hold() {
        return new Sculker(NEVER_EXPIRES, diving);
    }

    /**
     * The same brew with the player in or out of the dive.
     *
     * @param inTheDive whether the player is in the dive
     * @return the brew with that dive state
     */
    public Sculker withDiving(boolean inTheDive) {
        return new Sculker(expiresAt, inTheDive);
    }
}
