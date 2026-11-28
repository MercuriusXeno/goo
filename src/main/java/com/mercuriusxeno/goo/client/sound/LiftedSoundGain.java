package com.mercuriusxeno.goo.client.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import java.util.Map;

/**
 * The gain the client hands a sound's channel: vanilla's, its volume and its
 * slider each clamped to one, and for a sound recorded too quietly to reach
 * Blaze's fire loop under that clamp, vanilla's gain times that sound's lift.
 * drain-plays-its-chosen-sounds-as-loud-as-blaze-fire
 */
public final class LiftedSoundGain {

    /**
     * Each lift brings its sound's recording to Blaze fire's -34.0 dBFS RMS
     * (fire/fire.ogg at volume 1.0), measured as the mono RMS of vanilla's
     * Ogg takes: the wind loop's one take reads -49.6 at its 0.85 entry
     * volume and Drain's 1.0; soul_escape's 13 takes average -41.5 at their
     * 0.25 entry volume and Drain's 0.7.
     */
    private static final Map<Identifier, Float> LIFTS = Map.of(
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop"), 7.10f,
            Identifier.withDefaultNamespace("particle.soul_escape"), 13.46f);

    private LiftedSoundGain() {
    }

    /**
     * How many times vanilla's gain a sound plays at.
     *
     * @param sound the sound event's id
     * @return the lift, one for every sound but the lifted ones
     */
    public static float liftOf(Identifier sound) {
        return LIFTS.getOrDefault(sound, 1f);
    }

    /**
     * Vanilla's gain for a sound, lifted where the sound is lifted.
     *
     * @param sound       the sound event's id
     * @param vanillaGain the gain vanilla's clamp answered
     * @return the gain the channel plays at
     */
    public static float lifted(Identifier sound, float vanillaGain) {
        return vanillaGain * liftOf(sound);
    }

    /**
     * The gain a sound plays at under the volume sliders, as the client's
     * sound engine answers it: vanilla's clamped volume times its category
     * slider times the master slider, then lifted.
     *
     * @param sound    the sound event's id
     * @param volume   the instance's volume, its sounds.json entry volume included
     * @param category the slider of the category the sound plays under
     * @param master   the master slider
     * @return the gain the channel plays at
     */
    public static float gainFor(Identifier sound, float volume, float category, float master) {
        return lifted(sound, Mth.clamp(volume, 0f, 1f) * Mth.clamp(category * master, 0f, 1f));
    }
}
