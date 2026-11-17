package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import java.util.Optional;

/**
 * The sounds a held throw's stack makes: one as each knife or javelin
 * begins forming, one as it begins reverting to the blob, and one as each
 * is thrown, its pitch varied per throw. Dart shinks as its javelin forms,
 * shinks lower as it reverts and plays the trident's throw as it leaves;
 * Knives chimes as each knife forms and plays the snowball's throw as each leaves.
 * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
 * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
 *
 * @param form             the sound as one begins forming, empty for none
 * @param revert           the sound as one begins reverting, empty for none
 * @param thrown           the sound as one is thrown, at its middle pitch, empty for none
 * @param throwPitchSpread how far either side of its middle pitch each throw's sound may play
 */
public record FormSounds(Optional<SoundCue> form, Optional<SoundCue> revert, Optional<SoundCue> thrown,
                         float throwPitchSpread) {

    /** No sound at all. */
    public static final FormSounds NONE = new FormSounds(Optional.empty(), Optional.empty(), Optional.empty(), 0f);

    /** Stream codec carrying the sounds on the ability sync, so the holder's client plays them. */
    public static final StreamCodec<ByteBuf, FormSounds> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::form,
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::revert,
            ByteBufCodecs.optional(SoundCue.STREAM_CODEC), FormSounds::thrown,
            ByteBufCodecs.FLOAT, FormSounds::throwPitchSpread,
            FormSounds::new);

    /**
     * The sound one throw makes, its pitch drawn anywhere within the spread
     * either side of the middle pitch, so no two throws need sound alike.
     *
     * @param random the thrower's random source
     * @return the throw's sound, empty for none
     */
    public Optional<SoundCue> thrownWith(RandomSource random) {
        return thrown.map(cue -> new SoundCue(cue.sound(), cue.source(), cue.volume(),
                cue.pitch() + Mth.nextFloat(random, -throwPitchSpread, throwPitchSpread)));
    }
}
