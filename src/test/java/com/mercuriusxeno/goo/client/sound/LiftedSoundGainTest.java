package com.mercuriusxeno.goo.client.sound;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the gain goo's client hands a sound's channel: Drain's wind loop
 * and soul_escape accent play as loud as Blaze's fire loop, every other
 * sound keeps vanilla's clamp, and the sliders still turn Drain's two down.
 * The loudness cases read vanilla's recordings from the asset cache the
 * build's downloadAssets task fills.
 * drain-plays-its-chosen-sounds-as-loud-as-blaze-fire
 */
class LiftedSoundGainTest {

    private static final Identifier SOUL_SAND_VALLEY_LOOP =
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop");
    private static final Identifier SOUL_ESCAPE = Identifier.withDefaultNamespace("particle.soul_escape");
    private static final Identifier FIRE_AMBIENT = Identifier.withDefaultNamespace("block.fire.ambient");
    private static final float TOLERANCE = 1e-6f;
    private static final double WITHIN_DECIBELS = 3;
    private static final float HALF = 0.5f;

    /**
     * Drain's two sounds land within 3 dB of Blaze's fire loop's RMS, each
     * at the volume hex_drain.json names and its sounds.json entry volume.
     */
    @Nested
    class AsLoudAsBlazeFire {

        @Test
        void drainsWindLoopPlaysAsLoudAsBlazesFire() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float held = drain().delivery().sound().orElseThrow().volume();
            assertWithinBlazeFire(recordings, SOUL_SAND_VALLEY_LOOP, held);
        }

        @Test
        void drainsSoulEscapeAccentPlaysAsLoudAsBlazesFire() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            assertWithinBlazeFire(recordings, SOUL_ESCAPE, onLeechSound().volume().evaluateFloat(Variables.NONE));
        }

        private void assertWithinBlazeFire(VanillaRecordings recordings, Identifier sound, float volume)
                throws IOException {
            double fire = meanDecibels(recordings, FIRE_AMBIENT, 1f);
            double played = meanDecibels(recordings, sound, volume);
            assertEquals(fire, played, WITHIN_DECIBELS, sound + " plays at " + played + " dBFS RMS against Blaze"
                    + " fire's " + fire);
        }

        /** The mean over a sound's takes of each take's RMS at the gain the client plays it at, in dBFS. */
        private double meanDecibels(VanillaRecordings recordings, Identifier sound, float volume) throws IOException {
            List<VanillaRecordings.Take> takes = recordings.takesOf(sound.getPath());
            double sum = 0;
            for (VanillaRecordings.Take take : takes) {
                float gain = LiftedSoundGain.gainFor(sound, volume * take.entryVolume(), 1f, 1f);
                sum += recordings.rmsDecibelsOf(take) + 20 * Math.log10(gain);
            }
            return sum / takes.size();
        }
    }

    /** Every sound but Drain's two keeps vanilla's clamp, and the sliders still scale Drain's two. */
    @Nested
    class VanillaClampAndSliders {

        @Test
        void blazesFireKeepsVanillasClamp() {
            assertEquals(1f, LiftedSoundGain.gainFor(FIRE_AMBIENT, 1f, 1f, 1f), TOLERANCE);
        }

        @Test
        void boresRumbleKeepsVanillasClamp() {
            assertEquals(1f, LiftedSoundGain.gainFor(GooSoundIds.EARTH_RUMBLE, 1f, 1f, 1f), TOLERANCE);
        }

        @Test
        void halfTheMasterSliderHalvesDrainsTwoSounds() {
            for (Identifier sound : List.of(SOUL_SAND_VALLEY_LOOP, SOUL_ESCAPE)) {
                assertEquals(HALF * LiftedSoundGain.gainFor(sound, 1f, 1f, 1f),
                        LiftedSoundGain.gainFor(sound, 1f, 1f, HALF), TOLERANCE, sound.toString());
            }
        }

        @Test
        void thePlayersSliderAtZeroSilencesDrainsTwoSounds() {
            for (Identifier sound : List.of(SOUL_SAND_VALLEY_LOOP, SOUL_ESCAPE)) {
                assertEquals(0f, LiftedSoundGain.gainFor(sound, 1f, 0f, 1f), TOLERANCE, sound.toString());
            }
        }
    }

    private static AbilityDefinition drain() {
        return AbilityJson.decode("hex_drain");
    }

    private static SoundStep onLeechSound() {
        return drain().behaviors().stream().flatMap(LiftedSoundGainTest::withDescendants)
                .filter(LeechStep.class::isInstance).map(LeechStep.class::cast)
                .flatMap(leech -> leech.onLeech().stream())
                .filter(SoundStep.class::isInstance).map(SoundStep.class::cast)
                .findFirst().orElseThrow();
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LiftedSoundGainTest::withDescendants));
    }
}
