package com.mercuriusxeno.goo.ability.sculk;

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
 * @param expiresAt the game time the held effect ends at; zero when none stands
 * @param submerged    whether the player is submerged now, swimming through solid blocks
 */
public record Sculker(long expiresAt, boolean submerged) {

    /** No Sculker. */
    public static final Sculker NONE = new Sculker(0L, false);
    /** The end time of a Sculker held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_SUBMERGED = "submerged";

    /** Saves the Sculker with the player. */
    public static final MapCodec<Sculker> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Sculker::expiresAt),
            Codec.BOOL.optionalFieldOf(FIELD_SUBMERGED, false).forGetter(Sculker::submerged)
    ).apply(inst, Sculker::new));

    /** Syncs the Sculker to the owning client, which moves the player and draws the vision. */
    public static final StreamCodec<ByteBuf, Sculker> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Sculker::expiresAt, ByteBufCodecs.BOOL, Sculker::submerged, Sculker::new);

    /**
     * Whether the Sculker stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the Sculker ends
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * A cast from the glove: the Sculker stands until its held effect ends,
     * which clears it (decision self-effects-trickle-until-ended).
     *
     * @return the Sculker after the cast
     */
    public Sculker hold() {
        return new Sculker(NEVER_EXPIRES, submerged);
    }

    /**
     * The same held effect with the player submerged or not.
     *
     * @param submerged whether the player is submerged
     * @return the held effect with that submerged state
     */
    public Sculker withSubmerged(boolean submerged) {
        return new Sculker(expiresAt, submerged);
    }
}
