package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * An entity's standing out of phase: until the game time it fades at, the
 * entity shares a plane with every other phased entity and with nothing
 * else. Saved with the entity and synced to every client drawing it, which
 * draws it translucent and grey. A plain attachment any ability may lay.
 * phase-shares-a-plane-between-the-phased
 *
 * @param expiresAt the game time it fades at; zero when none stands
 */
public record OutOfPhase(long expiresAt) {

    /** In phase: the entity shares the world's plane. */
    public static final OutOfPhase NONE = new OutOfPhase(0L);
    /** The fade time of a phase held until its held effect ends. */
    public static final long NEVER_EXPIRES = Long.MAX_VALUE;

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the phase with the entity. */
    public static final MapCodec<OutOfPhase> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(OutOfPhase::expiresAt)
    ).apply(inst, OutOfPhase::new));

    /** Syncs the phase to the clients drawing the entity. */
    public static final StreamCodec<ByteBuf, OutOfPhase> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, OutOfPhase::expiresAt,
            OutOfPhase::new);

    /**
     * Whether the phase stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * Whether the phase stands at all, the test for saving it.
     *
     * @return true for a phase laid and not cleared
     */
    public boolean laid() {
        return expiresAt > 0L;
    }

    /**
     * A phase held from the glove, standing until its held effect ends.
     *
     * @return the held phase
     */
    public static OutOfPhase held() {
        return new OutOfPhase(NEVER_EXPIRES);
    }

    /**
     * A phase for a count of ticks, or as long as this one already stood where that is longer.
     *
     * @param ticks    how long the new phase lasts
     * @param gameTime the game time it is laid at
     * @return the phase after it is laid
     */
    public OutOfPhase lastingFor(int ticks, long gameTime) {
        return new OutOfPhase(Math.max(expiresAt, gameTime + ticks));
    }

    /**
     * Whether one entity can affect another: two entities in phase with each
     * other share a plane, and an entity out of phase shares none with one in it.
     *
     * @param actorPhased  whether the acting entity is out of phase
     * @param targetPhased whether the entity acted on is out of phase
     * @return true when the act lands
     */
    public static boolean sharePlane(boolean actorPhased, boolean targetPhased) {
        return actorPhased == targetPhased;
    }
}
