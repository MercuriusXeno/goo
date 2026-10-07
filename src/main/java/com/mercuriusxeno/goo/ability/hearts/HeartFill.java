package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How a heart brew lays its shields when it takes effect: a whole shield
 * over every present heart, or a copy of the player's current health halves
 * scaled by a factor the ability JSON names.
 */
public enum HeartFill {

    /**
     * A full shield over every present heart, a missing heart staying
     * missing (decision overlay-hearts-are-an-elemental-overshield).
     */
    WHOLE {
        @Override
        List<Integer> shields(float health, float factor) {
            return Collections.nCopies(HeartOverlay.filledSlots(health), HeartOverlay.FULL_SHIELD);
        }
    },
    /**
     * The player's current health halves times the factor, laid in full
     * shields from the left with any odd half last; Reserve banks half the
     * hearts the player has.
     * reserve-hearts-sit-behind-the-bar
     */
    FROM_CURRENT {
        @Override
        List<Integer> shields(float health, float factor) {
            int halves = (int) Math.floor(health * factor);
            List<Integer> laid = new ArrayList<>(Collections.nCopies(halves / HeartOverlay.FULL_SHIELD,
                    HeartOverlay.FULL_SHIELD));
            if (halves % HeartOverlay.FULL_SHIELD > 0) {
                laid.add(halves % HeartOverlay.FULL_SHIELD);
            }
            return laid;
        }
    };

    /**
     * Codec for the fill, written as its lower-case name.
     */
    public static final Codec<HeartFill> CODEC = LowerCaseEnumCodec.of(HeartFill.class, "heart fill");

    /**
     * The shields this fill lays, per heart slot from the left, in half hearts.
     *
     * @param health the player's real health, one point a half heart
     * @param factor the share of the current health a copy keeps
     * @return the half hearts of shield per slot
     */
    abstract List<Integer> shields(float health, float factor);
}
