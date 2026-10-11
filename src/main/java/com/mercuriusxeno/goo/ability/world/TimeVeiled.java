package com.mercuriusxeno.goo.ability.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One mob a chronosphere holds, saved with the mob so the AI state it had
 * before the veil survives a chunk unload or a world reload.
 * chronosphere-hastes-players-slows-mobs
 *
 * @param until    the game time the veil lets it go unless it touches it again
 * @param aiPeriod the ticks between one AI tick it runs and the next
 * @param aiWasOff true when the mob had no AI before the veil, which the veil then leaves alone
 */
public record TimeVeiled(long until, int aiPeriod, boolean aiWasOff) {

    /** A mob no veil holds. */
    public static final TimeVeiled NONE = new TimeVeiled(0L, 1, false);

    private static final String FIELD_UNTIL = "until";
    private static final String FIELD_AI_PERIOD = "ai_period";
    private static final String FIELD_AI_WAS_OFF = "ai_was_off";

    /** Codec for the attachment's save. */
    public static final MapCodec<TimeVeiled> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_UNTIL).forGetter(TimeVeiled::until),
            Codec.INT.fieldOf(FIELD_AI_PERIOD).forGetter(TimeVeiled::aiPeriod),
            Codec.BOOL.fieldOf(FIELD_AI_WAS_OFF).forGetter(TimeVeiled::aiWasOff)
    ).apply(inst, TimeVeiled::new));
}
