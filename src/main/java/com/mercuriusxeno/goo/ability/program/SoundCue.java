package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * One evaluated sound play, what a {@link SoundStep} hands its host once
 * its expressions are read, or what an ability's JSON names whole, as a held
 * effect's down sound (decision held-effects-sound-up-and-down).
 *
 * @param sound  the sound event id
 * @param source the category the sound plays under
 * @param volume the volume
 * @param pitch  the pitch
 */
public record SoundCue(Identifier sound, SoundKind source, float volume, float pitch) {

    private static final String FIELD_ID = "id";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_VOLUME = "volume";
    private static final String FIELD_PITCH = "pitch";
    private static final float WHOLE = 1f;

    /** Codec for a cue an ability's JSON names: the sound's id, with players' source and whole volume and pitch by default. */
    public static final Codec<SoundCue> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_ID).forGetter(SoundCue::sound),
            SoundKind.CODEC.optionalFieldOf(FIELD_SOURCE, SoundKind.PLAYERS).forGetter(SoundCue::source),
            Codec.FLOAT.optionalFieldOf(FIELD_VOLUME, WHOLE).forGetter(SoundCue::volume),
            Codec.FLOAT.optionalFieldOf(FIELD_PITCH, WHOLE).forGetter(SoundCue::pitch)
    ).apply(inst, SoundCue::new));

    /** Codec for a cue on the wire. */
    public static final StreamCodec<ByteBuf, SoundCue> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SoundCue::sound,
            ByteBufCodecs.idMapper(ordinal -> SoundKind.values()[ordinal], SoundKind::ordinal), SoundCue::source,
            ByteBufCodecs.FLOAT, SoundCue::volume,
            ByteBufCodecs.FLOAT, SoundCue::pitch,
            SoundCue::new);

    /**
     * A cue at players' source, whole volume and pitch.
     *
     * @param sound the sound event id
     * @return the cue
     */
    public static SoundCue of(Identifier sound) {
        return new SoundCue(sound, SoundKind.PLAYERS, WHOLE, WHOLE);
    }
}
