package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;

/**
 * The kind of heart overlay a brew lays over the player's health bar, which
 * names the overlay's rules: what a bare real heart costs, how fast a bare
 * heart regrows its shield and whether the overlay ends with its last shield
 * (decision overlay-hearts-are-an-elemental-overshield).
 */
public enum HeartKind {
    /**
     * Blaze Kindle: ember shields over hearts that read ash when bare, ash
     * costing double, one ember back every n / 2 + 2 seconds, quenched by water
     * (decision kindle-ember-hearts-ash-and-retaliate).
     */
    KINDLE(2.0f, false, true, true) {
        @Override
        long regrowInterval(int shieldHalves) {
            // kindle-ember-hearts-ash-and-retaliate: n / 2 + 2 seconds a heart, n the embers, so a high bar
            // regrows slower; a half takes half that, and n hearts are 2n halves
            return (long) KINDLE_BASE_SECONDS * TICKS_PER_SECOND / HALVES_PER_HEART
                    + (long) shieldHalves * TICKS_PER_SECOND / EMBER_HALVES_PER_EXTRA_SECOND;
        }
    },
    /**
     * Leaf Barkskin: bark shields over normal hearts, one bark back every five
     * seconds, standing with no bark left and regrowing it until ended
     * (decisions barkskin-bark-hearts-thorn-and-burn and heart-effects-crawl-while-held).
     */
    BARKSKIN(1.0f, false, false, true) {
        @Override
        long regrowInterval(int shieldHalves) {
            // barkskin-bark-hearts-thorn-and-burn: a bark heart every 5 seconds, a half every 2.5
            return (long) BARK_REGROW_SECONDS * TICKS_PER_SECOND / HALVES_PER_HEART;
        }
    },
    /**
     * Vital Reserve: health drained while right click is held, banked behind
     * the bar at a lossy ratio, spent before real health and gone with its
     * last half; it has no weakness, retaliates at nothing and never regrows.
     * Appended last, so the synced ordinals of the kinds before it stand.
     * reserve-hearts-sit-behind-the-bar
     */
    RESERVE(1.0f, true, false, false) {
        @Override
        long regrowInterval(int shieldHalves) {
            return 0L;
        }
    },
    /**
     * Rock Stoneskin: stone over the hearts the player is missing, crawling
     * into each further missing heart a stone every five seconds, standing
     * with no stone left and crawling it back until ended, and holding the
     * player's health from regenerating while it stands; appended after Reserve, so the synced ordinals of the kinds
     * before it stand (decisions stoneskin-stone-hearts-block-regeneration
     * and heart-effects-crawl-while-held).
     */
    STONESKIN(1.0f, false, false, true) {
        @Override
        long regrowInterval(int shieldHalves) {
            // heart-effects-crawl-while-held: stone crawls at bark's pace, a stone heart every 5 seconds
            return (long) BARK_REGROW_SECONDS * TICKS_PER_SECOND / HALVES_PER_HEART;
        }

        @Override
        public boolean fillsMissing() {
            return true;
        }
    },
    /**
     * Nether Undead: nether over the hearts the player is missing, crawling
     * into each further missing heart fast, a nether heart a second, taking
     * hits before real health and standing with no nether left until ended;
     * appended last, so the synced ordinals of the kinds before it stand
     * (decisions undead-nether-hearts-burn-in-sunlight and heart-effects-crawl-while-held).
     */
    UNDEAD(1.0f, false, false, true) {
        @Override
        long regrowInterval(int shieldHalves) {
            // undead-nether-hearts-burn-in-sunlight: nether hearts regenerate rapidly
            return UNDEAD_REGROW_TICKS_PER_HALF;
        }

        @Override
        public boolean fillsMissing() {
            return true;
        }
    };

    /**
     * Codec for the kind, written as its lower-case name.
     */
    public static final Codec<HeartKind> CODEC = LowerCaseEnumCodec.of(HeartKind.class, "heart kind");

    static final int TICKS_PER_SECOND = 20;
    private static final int KINDLE_BASE_SECONDS = 2;
    private static final int HALVES_PER_HEART = 2;
    /** Every eight ember halves standing slow the next half by a second: n / 2 seconds a heart is n / 8 a half. */
    private static final int EMBER_HALVES_PER_EXTRA_SECOND = 8;
    private static final int BARK_REGROW_SECONDS = 5;
    /** Half a second a half, a nether heart a second. */
    private static final long UNDEAD_REGROW_TICKS_PER_HALF = 10L;

    private final float bareCostMultiplier;
    private final boolean endsWhenBare;
    private final boolean quenchedByWater;
    private final boolean regrows;

    HeartKind(float bareCostMultiplier, boolean endsWhenBare, boolean quenchedByWater, boolean regrows) {
        this.bareCostMultiplier = bareCostMultiplier;
        this.endsWhenBare = endsWhenBare;
        this.quenchedByWater = quenchedByWater;
        this.regrows = regrows;
    }

    /**
     * Answers whether a bare heart regrows its shield over time.
     *
     * @return true when shields regrow
     */
    boolean regrows() {
        return regrows;
    }

    /**
     * The ticks the next half of shield takes to regrow with a number of shield halves standing.
     *
     * @param shieldHalves the shield halves standing
     * @return the interval in ticks
     */
    abstract long regrowInterval(int shieldHalves);

    /**
     * Answers whether the kind lays its shields over the hearts the player is
     * missing rather than the hearts the player holds.
     *
     * @return true for a kind filling the missing hearts
     */
    public boolean fillsMissing() {
        return false;
    }

    /**
     * What a point of damage costs a bare real heart while the overlay stands.
     *
     * @return the cost multiplier
     */
    float bareCostMultiplier() {
        return bareCostMultiplier;
    }

    /**
     * Answers whether the overlay ends the moment its last shield breaks.
     *
     * @return true when the overlay lasts only while a shield stands
     */
    boolean endsWhenBare() {
        return endsWhenBare;
    }

    /**
     * Answers whether water or ice strips every shield.
     *
     * @return true when water quenches the shields
     */
    boolean quenchedByWater() {
        return quenchedByWater;
    }
}
