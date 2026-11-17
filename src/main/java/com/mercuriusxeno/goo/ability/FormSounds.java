package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.Optional;

/**
 * The sounds a held throw's stack makes: one as each knife or javelin
 * begins forming, one as it begins reverting to the blob, and one as each
 * is thrown. Dart shinks as its javelin forms, shinks lower as it reverts
 * and plays the trident's throw as it leaves; Knives chimes as each knife
 * forms and plays the arrow's shot, pitched up, as each leaves.
 * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
 * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
 *
 * @param form   the sound as one begins forming, empty for none
 * @param revert the sound as one begins reverting, empty for none
 * @param thrown the sound as one is thrown, empty for none
 */
public record FormSounds(Optional<SoundCue> form, Optional<SoundCue> revert, Optional<SoundCue> thrown) {

    /** No sound at all. */
    public static final FormSounds NONE = new FormSounds(Optional.empty(), Optional.empty(), Optional.empty());

    /** Stream codec carrying the sounds on the ability sync, so the holder's client plays them. */
    public static final StreamCodec<ByteBuf, FormSounds> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::form,
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::revert,
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::thrown,
            FormSounds::new);
}
