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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the gain goo's client hands a sound's channel: Drain's wind loop
 * plays as loud as Bore's whir, its soul_escape hit plays about twice as
 * loud as it did at 613cd1118 with no take clipping, every other sound
 * keeps vanilla's clamp, and the sliders still turn Drain's two down. The
 * loudness cases read vanilla's recordings from the asset cache the build's
 * downloadAssets task fills, and Bore's from goo's resources.
 * drain-lifts-its-chosen-sounds-past-the-volume-cap
 */
class LiftedSoundGainTest {

    private static final Identifier SOUL_SAND_VALLEY_LOOP =
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop");
    private static final Identifier SOUL_ESCAPE = Identifier.withDefaultNamespace("particle.soul_escape");
    private static final Identifier FIRE_AMBIENT = Identifier.withDefaultNamespace("block.fire.ambient");
    private static final float TOLERANCE = 1e-6f;
    private static final float HALF = 0.5f;

    /**
     * Drain's wind lands within 1.5 dB of Bore's whir, goo:effects.earth_rumble
     * at the 0.35 rock_bore.json names.
     * drain-plays-its-wind-as-loud-as-bore-whir
     */
    @Nested
    class WindAsLoudAsBoresWhir {

        private static final float WHIR_VOLUME = 0.35f;
        private static final double WITHIN_DECIBELS = 1.5;

        @Test
        void drainsWindLoopPlaysAsLoudAsBoresWhir() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float held = drain().delivery().sound().orElseThrow().volume();
            double wind = 0;
            List<VanillaRecordings.Take> windTakes = recordings.takesOf(SOUL_SAND_VALLEY_LOOP.getPath());
            for (VanillaRecordings.Take take : windTakes) {
                float gain = LiftedSoundGain.gainFor(SOUL_SAND_VALLEY_LOOP, held * take.entryVolume(), 1f, 1f);
                wind += recordings.loudnessOf(take).rmsDecibels() + decibels(gain);
            }
            wind /= windTakes.size();
            double whir = 0;
            List<VanillaRecordings.Take> whirTakes = VanillaRecordings.gooTakesOf(GooSoundIds.EARTH_RUMBLE.getPath());
            for (VanillaRecordings.Take take : whirTakes) {
                whir += VanillaRecordings.gooLoudnessOf(take).rmsDecibels() + decibels(WHIR_VOLUME * take.entryVolume());
            }
            whir /= whirTakes.size();
            assertEquals(whir, wind, WITHIN_DECIBELS, "Drain's wind plays at " + wind + " dBFS RMS against Bore's"
                    + " whir at " + whir);
        }
    }

    /**
     * soul_escape at a mob 3 blocks off rises 8 to 12 dB over its level at
     * 613cd1118, by the mean RMS over its takes, and no take's peak at 1
     * block reaches full scale.
     * drain-hit-plays-twice-as-loud
     */
    @Nested
    class HitTwiceAsLoud {

        /** soul_escape's lift at 613cd1118. */
        private static final float LIFT_AT_613CD1118 = 13.46f;
        /** The volume Drain's leech played soul_escape at, at 613cd1118. */
        private static final float LEECH_VOLUME_AT_613CD1118 = 0.7f;
        /** The distance soul_escape fell off over at 613cd1118, vanilla's entry at that volume. */
        private static final float ATTENUATION_AT_613CD1118 = 12f;
        private static final float MOB_DISTANCE = 3f;
        private static final float NEAREST_DISTANCE = 1f;
        private static final double LEAST_RISE = 8;
        private static final double MOST_RISE = 12;

        @Test
        void drainsHitRisesAboutTwiceAsLoud() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float leech = leechVolume();
            double then = 0;
            double now = 0;
            List<VanillaRecordings.Take> takes = recordings.takesOf(SOUL_ESCAPE.getPath());
            for (VanillaRecordings.Take take : takes) {
                double rms = recordings.loudnessOf(take).rmsDecibels();
                then += rms + decibels(LEECH_VOLUME_AT_613CD1118 * take.entryVolume() * LIFT_AT_613CD1118
                        * fallOff(MOB_DISTANCE, ATTENUATION_AT_613CD1118));
                now += rms + decibels(gainOf(take, leech) * fallOff(MOB_DISTANCE, attenuationOf(take, leech)));
            }
            double rise = (now - then) / takes.size();
            assertTrue(rise >= LEAST_RISE && rise <= MOST_RISE, "Drain's hit rises " + rise + " dB at 3 blocks over"
                    + " 613cd1118, outside " + LEAST_RISE + " to " + MOST_RISE);
        }

        @Test
        void noTakeOfDrainsHitClipsAtOneBlock() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float leech = leechVolume();
            for (VanillaRecordings.Take take : recordings.takesOf(SOUL_ESCAPE.getPath())) {
                double peak = recordings.loudnessOf(take).peakDecibels()
                        + decibels(gainOf(take, leech) * fallOff(NEAREST_DISTANCE, attenuationOf(take, leech)));
                assertTrue(peak < 0, take.name() + " peaks at " + peak + " dBFS at 1 block");
            }
        }

        private float gainOf(VanillaRecordings.Take take, float leech) {
            return LiftedSoundGain.gainFor(SOUL_ESCAPE, leech * take.entryVolume(), 1f, 1f);
        }

        /** The distance a take falls off over at a volume, as vanilla's Sound.getAttenuationDistance widens it. */
        private float attenuationOf(VanillaRecordings.Take take, float leech) {
            return Math.max(leech * take.entryVolume(), 1f) * take.attenuationDistance();
        }

        /** Minecraft's linear fall-off at a distance. */
        private float fallOff(float distance, float attenuation) {
            return 1f - distance / attenuation;
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

    private static double decibels(double gain) {
        return 20 * Math.log10(gain);
    }

    private static AbilityDefinition drain() {
        return AbilityJson.decode("hex_drain");
    }

    private static float leechVolume() {
        return drain().behaviors().stream().flatMap(LiftedSoundGainTest::withDescendants)
                .filter(LeechStep.class::isInstance).map(LeechStep.class::cast)
                .flatMap(leech -> leech.onLeech().stream())
                .filter(SoundStep.class::isInstance).map(SoundStep.class::cast)
                .findFirst().orElseThrow().volume().evaluateFloat(Variables.NONE);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(LiftedSoundGainTest::withDescendants));
    }
}
