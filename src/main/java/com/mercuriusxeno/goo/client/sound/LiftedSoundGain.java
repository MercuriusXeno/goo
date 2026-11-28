package com.mercuriusxeno.goo.client.sound;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import java.util.Map;

/**
 * The gain the client hands a sound's channel: vanilla's, its volume and its
 * slider each clamped to one, and for a sound recorded too quietly to be
 * heard under that clamp, vanilla's gain times that sound's lift.
 * drain-lifts-its-chosen-sounds-past-the-volume-cap
 */
public final class LiftedSoundGain {

    /**
     * The lifts, measured as the mono RMS of each sound's recordings. The
     * wind's brings ambience.ogg, -49.6 dBFS at its 0.85 entry volume and
     * Drain's 1.0, to Bore's whir, earth_rumble's takes at rock_bore.json's
     * 0.35, -17.8. The hit's raises soul_escape's 13 takes 9 dB at a mob 3
     * blocks off over their level at 613cd1118, where they played lifted
     * 13.46 times at Drain's 0.7 and fell off over vanilla's 12 blocks;
     * Drain's leech now plays it at 8.0, whose clamp leaves the gain to this
     * lift and whose volume widens the fall-off to 24 blocks, so its loudest
     * take peaks at -0.6 dBFS at 1 block.
     * drain-plays-its-wind-as-loud-as-bore-whir
     * drain-hit-plays-twice-as-loud
     */
    private static final Map<Identifier, Float> LIFTS = Map.of(
            Identifier.withDefaultNamespace("ambient.soul_sand_valley.loop"), 45.83f,
            Identifier.withDefaultNamespace("particle.soul_escape"), 5.69f);

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
