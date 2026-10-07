package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * The sound a held stream makes: the sound, played every so many ticks of
 * the hold at a volume, each play's pitch varied a little either way so the
 * repeat never sounds mechanical. Mycosis bubbles out with a rapid, gentle
 * {@code block.bubble_column.bubble_pop} (decision mycosis-spore-stream-buds-and-poisons).
 *
 * @param sound       the sound's id
 * @param every       the ticks of hold between plays
 * @param volume      each play's volume
 * @param pitchSpread how far each play's pitch strays from one, either way
 */
public record StreamSound(Identifier sound, int every, float volume, float pitchSpread) {

    private static final int DEFAULT_EVERY = 2;
    private static final float DEFAULT_VOLUME = 0.4f;
    private static final float DEFAULT_PITCH_SPREAD = 0.2f;
    private static final float HALF = 0.5f;
    /** A roll's half either side of the middle covers the spread both ways. */
    private static final float BOTH_WAYS = 2f;

    /** Codec for a delivery's {@code sound} block. */
    public static final MapCodec<StreamSound> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("id").forGetter(StreamSound::sound),
            Codec.INT.optionalFieldOf("every", DEFAULT_EVERY).forGetter(StreamSound::every),
            Codec.FLOAT.optionalFieldOf("volume", DEFAULT_VOLUME).forGetter(StreamSound::volume),
            Codec.FLOAT.optionalFieldOf("pitch_spread", DEFAULT_PITCH_SPREAD).forGetter(StreamSound::pitchSpread)
    ).apply(inst, StreamSound::new));

    /**
     * Whether the sound plays on a tick of the hold.
     *
     * @param held the hold's tick count, one on its first tick
     * @return true on the first tick and every {@code every} ticks after
     */
    public boolean playsOn(int held) {
        return every <= 1 || (held - 1) % every == 0;
    }

    /**
     * A play's pitch for a roll between zero and one.
     *
     * @param roll a uniform roll
     * @return one, strayed by up to the spread either way
     */
    public float pitchFor(float roll) {
        return 1f + (roll - HALF) * BOTH_WAYS * pitchSpread;
    }
}
