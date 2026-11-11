package com.mercuriusxeno.goo.client.ability;

import java.util.ArrayList;
import java.util.List;

/**
 * What a mob Heal or Drain touches shows of its life over its head: a row of
 * hearts, one per heart of its max life, while that max is a few hearts; one
 * heart and its life over its max, counted in hearts, beyond that.
 * touched-mobs-show-hearts-or-a-count-over-max
 */
public sealed interface LifeMark {

    /** The most hearts a mob's max life spans and still draws as a row. */
    int MOST_HEARTS_IN_A_ROW = 5;
    /** Health counts half hearts. */
    int HALVES_PER_HEART = 2;
    /** What stands between the life and the max in a clustered count. */
    String OVER = " / ";
    /** What follows the whole hearts of a count ending in half a heart. */
    String AND_A_HALF = ".5";

    /**
     * The mark a mob's life draws. Life rounds up to the half heart, as
     * vanilla's own hearts do, so a living mob never reads as empty.
     *
     * @param health    the mob's health, in half hearts
     * @param maxHealth the mob's max health, in half hearts
     * @return the row of hearts, or the clustered count
     */
    static LifeMark of(float health, float maxHealth) {
        int maxHalves = (int) Math.ceil(maxHealth);
        int halves = Math.clamp((int) Math.ceil(health), 0, maxHalves);
        int maxHearts = (maxHalves + 1) / HALVES_PER_HEART;
        if (maxHearts > MOST_HEARTS_IN_A_ROW) {
            return new Clustered(inHearts(halves) + OVER + inHearts(maxHalves));
        }
        List<HeartFill> hearts = new ArrayList<>(maxHearts);
        for (int heart = 0; heart < maxHearts; heart++) {
            hearts.add(HeartFill.of(halves - HALVES_PER_HEART * heart));
        }
        return new Row(hearts);
    }

    private static String inHearts(int halves) {
        String whole = Integer.toString(halves / HALVES_PER_HEART);
        return halves % HALVES_PER_HEART == 0 ? whole : whole + AND_A_HALF;
    }

    /**
     * One heart per heart of max life, each full, half or empty by the life standing.
     *
     * @param hearts the hearts, left to right
     */
    record Row(List<HeartFill> hearts) implements LifeMark {
        /**
         * Copies the hearts so the record holds them unmodifiable.
         *
         * @param hearts the hearts, left to right
         */
        public Row {
            hearts = List.copyOf(hearts);
        }
    }

    /**
     * One heart beside the life and the max, counted in hearts, as "life / max".
     *
     * @param count the text drawn beside the heart
     */
    record Clustered(String count) implements LifeMark {
    }

    /** How much of one heart the life fills. */
    enum HeartFill {
        /** The whole heart. */
        FULL,
        /** Half the heart. */
        HALF,
        /** None of it: life the mob is missing. */
        EMPTY;

        /**
         * The fill of a heart given the half hearts of life left from it rightward.
         *
         * @param halvesFromHere the life's half hearts counted from this heart's left edge
         * @return full at two or more, half at one, empty below
         */
        static HeartFill of(int halvesFromHere) {
            if (halvesFromHere >= HALVES_PER_HEART) {
                return FULL;
            }
            return halvesFromHere > 0 ? HALF : EMPTY;
        }
    }
}
