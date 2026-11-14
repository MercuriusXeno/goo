package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.MixedNumber;
import net.minecraft.resources.Identifier;

/**
 * What a mob's life shows over its head: one heart filled from left to right
 * by its life over its max, and its life counted in hearts as a mixed number;
 * on a downed mob, a skull and crossbones in the heart's slot and its
 * negative health counted in hearts below zero.
 * health-bar-is-one-heart-filling-left-to-right
 * touched-mobs-show-hearts-or-a-count-over-max
 * downed-mob-shows-a-skull-and-its-negative-health
 *
 * @param sprite the sprite drawn whole in the heart's slot: the empty heart, or the skull
 * @param fill   the share of the heart the life fills, zero to one, and zero on a downed mob
 * @param count  the life in hearts, drawn right of the sprite by the mixed number spacing rule
 */
public record LifeMark(Identifier sprite, float fill, MixedNumber count) {

    /** Vanilla's empty heart, read as a whole texture as InWorldHud reads the effect background. */
    public static final Identifier HEART =
            Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    /** The skull and crossbones a downed mob shows in the heart's slot, at the heart's 9 pixel side. */
    public static final Identifier SKULL = Identifier.fromNamespaceAndPath(Goo.MODID, "textures/entity/downed_skull.png");

    /** The sign leading a downed mob's count below zero. */
    private static final String MINUS = "-";
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
        return new LifeMark(HEART, fill, GooFormat.mixedParts(GooFraction.of(halves, HALVES_PER_HEART)));
    }

    /**
     * The mark a downed mob draws: the skull with no fill, and its negative
     * health in hearts as a negative mixed number, rounded away from zero to
     * the half heart so a mob below zero never reads as zero; the minus sign
     * leads the whole, or stands in its place under one heart.
     * downed-mob-shows-a-skull-and-its-negative-health
     *
     * @param negativeHealth the health below zero it holds, in half hearts
     * @return the skull and the count beside it
     */
    public static LifeMark downed(float negativeHealth) {
        int halvesBelowZero = Math.max(0, (int) Math.ceil(-negativeHealth));
        MixedNumber hearts = GooFormat.mixedParts(GooFraction.of(halvesBelowZero, HALVES_PER_HEART));
        if (halvesBelowZero == 0) {
            return new LifeMark(SKULL, 0f, hearts);
        }
        return new LifeMark(SKULL, 0f, new MixedNumber(MINUS + hearts.whole(), hearts.numerator(),
                hearts.denominator()));
    }
}
