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
 * plays at three quarters of the gain it played at in 6e53293ad, its
 * soul_escape hit plays at its a46598f59 level with no take clipping, every
 * other sound keeps vanilla's clamp, and the sliders still turn Drain's two
 * down. The loudness cases read vanilla's recordings from the asset cache the
 * build's downloadAssets task fills.
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
     * Drain's wind plays at three quarters of the gain it played at in
     * 6e53293ad, the operator's ruling after hearing it there.
     * drain-wind-plays-6-db-under-its-hit
     */
    @Nested
    class WindAtThreeQuarters {

        /** The wind's lift in 6e53293ad. */
        private static final float LIFT_AT_6E53293AD = 7.52f;
        private static final float THREE_QUARTERS = 0.75f;
        private static final float WITHIN = 0.01f;

        @Test
        void drainsWindPlaysAtThreeQuartersOfItsLastGain() {
            assertEquals(THREE_QUARTERS * LIFT_AT_6E53293AD, LiftedSoundGain.gainFor(SOUL_SAND_VALLEY_LOOP, 1f, 1f, 1f),
                    WITHIN);
        }
    }

    /**
     * The hit keeps the lift, leech volume and attenuation distance it played
     * with at a46598f59.
     * drain-hit-plays-at-its-a46598f59-level
     */
    @Nested
    class HitKeepsItsLevel {

        private static final float LIFT_AT_A46598F59 = 5.69f;
        private static final float LEECH_VOLUME_AT_A46598F59 = 8.0f;
        private static final float ATTENUATION_AT_A46598F59 = 24f;

        @Test
        void drainsHitKeepsItsLiftVolumeAndFallOff() throws IOException {
            assertEquals(LIFT_AT_A46598F59, LiftedSoundGain.liftOf(SOUL_ESCAPE), TOLERANCE);
            float leech = leechVolume();
            assertEquals(LEECH_VOLUME_AT_A46598F59, leech, TOLERANCE);
            for (VanillaRecordings.Take take : VanillaRecordings.fromBuild().takesOf(SOUL_ESCAPE.getPath())) {
                assertEquals(ATTENUATION_AT_A46598F59, attenuationOf(take, leech), TOLERANCE, take.name());
            }
        }
    }

    /**
     * soul_escape at a mob 3 blocks off plays at -27.5 dBFS within 1 dB, by
     * the mean RMS over its takes, and no take's peak at 1 block reaches full
     * scale.
     * drain-hit-plays-at-its-a46598f59-level
     */
    @Nested
    class HitAtItsLevel {

        private static final double HIT_DECIBELS = -27.5;
        private static final double WITHIN_DECIBELS = 1;
        private static final float MOB_DISTANCE = 3f;
        private static final float NEAREST_DISTANCE = 1f;

        @Test
        void drainsHitPlaysAtItsLevel() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float leech = leechVolume();
            double hit = 0;
            List<VanillaRecordings.Take> takes = recordings.takesOf(SOUL_ESCAPE.getPath());
            for (VanillaRecordings.Take take : takes) {
                hit += recordings.loudnessOf(take).rmsDecibels()
                        + decibels(hitGainOf(take, leech) * fallOff(MOB_DISTANCE, attenuationOf(take, leech)));
            }
            hit /= takes.size();
            assertEquals(HIT_DECIBELS, hit, WITHIN_DECIBELS, "Drain's hit plays at " + hit + " dBFS RMS at 3 blocks");
        }

        @Test
        void noTakeOfDrainsHitClipsAtOneBlock() throws IOException {
            VanillaRecordings recordings = VanillaRecordings.fromBuild();
            float leech = leechVolume();
            for (VanillaRecordings.Take take : recordings.takesOf(SOUL_ESCAPE.getPath())) {
                double peak = recordings.loudnessOf(take).peakDecibels()
                        + decibels(hitGainOf(take, leech) * fallOff(NEAREST_DISTANCE, attenuationOf(take, leech)));
                assertTrue(peak < 0, take.name() + " peaks at " + peak + " dBFS at 1 block");
            }
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

    private static float hitGainOf(VanillaRecordings.Take take, float leech) {
        return LiftedSoundGain.gainFor(SOUL_ESCAPE, leech * take.entryVolume(), 1f, 1f);
    }

    /** The distance a take falls off over at a volume, as vanilla's Sound.getAttenuationDistance widens it. */
    private static float attenuationOf(VanillaRecordings.Take take, float leech) {
        return Math.max(leech * take.entryVolume(), 1f) * take.attenuationDistance();
    }

    /** Minecraft's linear fall-off at a distance. */
    private static float fallOff(float distance, float attenuation) {
        return 1f - distance / attenuation;
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
