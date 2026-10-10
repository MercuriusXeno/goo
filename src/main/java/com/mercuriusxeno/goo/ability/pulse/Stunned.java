package com.mercuriusxeno.goo.ability.pulse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A stunned mob's wake: the game time its AI returns, and whether its AI
 * stood off before the stun, so waking leaves a mob another ability froze
 * as frozen. Saved with the mob, so a stun outlasting an unload still wakes.
 * zap-ticks-the-device-and-stuns
 *
 * @param wakesAt  the game time the stun ends at
 * @param wasNoAi  whether the mob's AI stood off before the stun
 */
public record Stunned(long wakesAt, boolean wasNoAi) {

    /** No stun. */
    public static final Stunned NONE = new Stunned(0L, false);

    private static final String FIELD_WAKES_AT = "wakes_at";
    private static final String FIELD_WAS_NO_AI = "was_no_ai";

    /** Saves the stun with the mob. */
    public static final MapCodec<Stunned> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_WAKES_AT).forGetter(Stunned::wakesAt),
            Codec.BOOL.fieldOf(FIELD_WAS_NO_AI).forGetter(Stunned::wasNoAi)
    ).apply(inst, Stunned::new));

    /**
     * The stun a fresh hit leaves: a mob already stunned keeps the AI it had
     * before its first stun, and the later wake of the two stands.
     *
     * @param standing the stun the mob carries, or null for none
     * @param wakesAt  the game time the fresh hit wakes at
     * @param noAiNow  whether the mob's AI stands off now
     * @return the stun to carry
     */
    public static Stunned renewed(Stunned standing, long wakesAt, boolean noAiNow) {
        if (standing == null) {
            return new Stunned(wakesAt, noAiNow);
        }
        return new Stunned(Math.max(standing.wakesAt, wakesAt), standing.wasNoAi);
    }

    /**
     * Whether the stun has ended at a game time.
     *
     * @param gameTime the game time
     * @return true from the wake onward
     */
    public boolean wokeBy(long gameTime) {
        return gameTime >= wakesAt;
    }
}
