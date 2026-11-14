package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.MixedNumber;

/**
 * What a mob's life shows over its head: one heart filled from left to right
 * by its life over its max, and its life counted in hearts as a mixed number.
 * health-bar-is-one-heart-filling-left-to-right
 * touched-mobs-show-hearts-or-a-count-over-max
 *
 * @param fill  the share of the heart the life fills, zero to one
 * @param count the life in hearts, drawn right of the heart by the mixed number spacing rule
 */
public record LifeMark(float fill, MixedNumber count) {

    /** Health counts half hearts. */
    private static final int HALVES_PER_HEART = 2;

    /**
     * The mark a mob's life draws. The count rounds life up to the half heart,
     * as vanilla's own hearts do, so a living mob never reads as empty.
     *
     * @param health    the mob's health, in half hearts
     * @param maxHealth the mob's max health, in half hearts
     * @return the heart's fill and the count beside it
     */
    public static LifeMark of(float health, float maxHealth) {
        float fill = maxHealth > 0f ? Math.clamp(health / maxHealth, 0f, 1f) : 0f;
        int halves = Math.clamp((int) Math.ceil(health), 0, (int) Math.ceil(maxHealth));
        return new LifeMark(fill, GooFormat.mixedParts(GooFraction.of(halves, HALVES_PER_HEART)));
    }
}
