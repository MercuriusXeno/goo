package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.Optional;

/**
 * The sounds a travel form formed in the hand makes: one as it begins
 * forming from the palm blob, one as it begins reverting to the blob. Dart
 * shinks as its javelin forms and shinks lower as it reverts.
 * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
 *
 * @param form   the sound as the form begins forming, empty for none
 * @param revert the sound as the form begins reverting, empty for none
 */
public record FormSounds(Optional<SoundCue> form, Optional<SoundCue> revert) {

    /** No sound either way. */
    public static final FormSounds NONE = new FormSounds(Optional.empty(), Optional.empty());

    /** Stream codec carrying the sounds on the ability sync, so the holder's client plays them. */
    public static final StreamCodec<ByteBuf, FormSounds> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::form,
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::revert,
            FormSounds::new);
}
