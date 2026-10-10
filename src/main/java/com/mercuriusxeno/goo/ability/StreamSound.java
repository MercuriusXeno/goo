package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The sound a held stream makes: the sound, played every so many ticks of
 * the hold at a volume and a pitch, each play's pitch varied a little either
 * way so the repeat never sounds mechanical. Mycosis bubbles out with a
 * rapid, gentle {@code block.bubble_column.bubble_pop} (decision
 * mycosis-spore-stream-buds-and-poisons); Decay's gnats buzz with a bee's
 * loop pitched high (decision decay-gnats-degrade-each-block-once).
 *
 * @param sound       the sound's id
 * @param every       the ticks of hold between plays
 * @param volume      each play's volume
 * @param pitchSpread how far each play's pitch strays from the pitch, either way
 * @param pitch       the pitch each play strays around
 * @param loop        whether the holder hears the sound as one loop fading in and out rather than as plays;
 *                    the server's plays then reach everyone near but the holder
 */
public record StreamSound(Identifier sound, int every, float volume, float pitchSpread, float pitch,
                          boolean loop) {

    private static final int DEFAULT_EVERY = 2;
    private static final float DEFAULT_VOLUME = 0.4f;
    private static final float DEFAULT_PITCH_SPREAD = 0.2f;
    private static final float DEFAULT_PITCH = 1f;
    private static final float HALF = 0.5f;
    /** A roll's half either side of the middle covers the spread both ways. */
    private static final float BOTH_WAYS = 2f;

    /** Codec for a delivery's {@code sound} block. */
    public static final MapCodec<StreamSound> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("id").forGetter(StreamSound::sound),
            Codec.INT.optionalFieldOf("every", DEFAULT_EVERY).forGetter(StreamSound::every),
            Codec.FLOAT.optionalFieldOf("volume", DEFAULT_VOLUME).forGetter(StreamSound::volume),
            Codec.FLOAT.optionalFieldOf("pitch_spread", DEFAULT_PITCH_SPREAD).forGetter(StreamSound::pitchSpread),
            Codec.FLOAT.optionalFieldOf("pitch", DEFAULT_PITCH).forGetter(StreamSound::pitch),
            Codec.BOOL.optionalFieldOf("loop", false).forGetter(StreamSound::loop)
    ).apply(inst, StreamSound::new));

    /** Stream codec carrying the sound on the ability sync, so the holder's client can loop it. */
    public static final StreamCodec<ByteBuf, StreamSound> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, StreamSound::sound,
            ByteBufCodecs.VAR_INT, StreamSound::every,
            ByteBufCodecs.FLOAT, StreamSound::volume,
            ByteBufCodecs.FLOAT, StreamSound::pitchSpread,
            ByteBufCodecs.FLOAT, StreamSound::pitch,
            ByteBufCodecs.BOOL, StreamSound::loop,
            StreamSound::new);

    /**
     * A sound played, not looped, around a pitch.
     *
     * @param sound       the sound's id
     * @param every       the ticks of hold between plays
     * @param volume      each play's volume
     * @param pitchSpread how far each play's pitch strays from the pitch, either way
     * @param pitch       the pitch each play strays around
     */
    public StreamSound(Identifier sound, int every, float volume, float pitchSpread, float pitch) {
        this(sound, every, volume, pitchSpread, pitch, false);
    }

    /**
     * A sound playing around a pitch of one.
     *
     * @param sound       the sound's id
     * @param every       the ticks of hold between plays
     * @param volume      each play's volume
     * @param pitchSpread how far each play's pitch strays from one, either way
     */
    public StreamSound(Identifier sound, int every, float volume, float pitchSpread) {
        this(sound, every, volume, pitchSpread, DEFAULT_PITCH);
    }

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
     * @return the pitch, strayed by up to the spread either way
     */
    public float pitchFor(float roll) {
        return pitch + (roll - HALF) * BOTH_WAYS * pitchSpread;
    }
}
