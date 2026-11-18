package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.SoundCue;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pitch each throw's sound plays at, varied per throw over the spread
 * the ability's data names: Dart's trident throw widely, Knives' snowball
 * throw as vanilla varies a snowball's
 * (decision knives-and-javelins-sound-as-each-forms-and-is-thrown).
 */
class FormSoundsTest {

    private static final int THROWS = 400;
    /** How far apart Dart's highest and lowest throw pitch stand at the least, varied widely. */
    private static final float DART_WIDEST_SPAN_AT_LEAST = 0.5f;
    /** Vanilla's snowball throw pitch, 0.4 over 0.8 to 1.2. */
    private static final float SNOWBALL_LOWEST = 0.4f / 1.2f;
    private static final float SNOWBALL_HIGHEST = 0.4f / 0.8f;
    private static final float WITHIN = 1e-4f;

    private static List<Float> pitchesThrownBy(String ability) {
        FormSounds sounds = AbilityJson.decode(ability).delivery().formSounds();
        RandomSource random = RandomSource.create(7L);
        List<Float> pitches = new ArrayList<>();
        for (int thrown = 0; thrown < THROWS; thrown++) {
            pitches.add(sounds.thrownWith(random).map(SoundCue::pitch).orElseThrow());
        }
        return pitches;
    }

    private static float spanOf(List<Float> pitches) {
        return pitches.stream().max(Float::compare).orElseThrow() - pitches.stream().min(Float::compare).orElseThrow();
    }

    @Test
    void dartsTridentThrowVariesItsPitchWidely() {
        List<Float> pitches = pitchesThrownBy("metal_dart");
        assertTrue(spanOf(pitches) >= DART_WIDEST_SPAN_AT_LEAST, "Dart's throw pitches span only " + spanOf(pitches));
    }

    @Test
    void knivesSnowballThrowVariesAsVanillasSnowballDoes() {
        List<Float> pitches = pitchesThrownBy("crystal_knives");
        assertTrue(pitches.stream().allMatch(pitch -> pitch >= SNOWBALL_LOWEST - WITHIN
                && pitch <= SNOWBALL_HIGHEST + WITHIN), "a knife throw pitched outside a snowball's, " + pitches);
        assertTrue(spanOf(pitches) > (SNOWBALL_HIGHEST - SNOWBALL_LOWEST) / 2, "knife throw pitches span only "
                + spanOf(pitches));
    }
}
