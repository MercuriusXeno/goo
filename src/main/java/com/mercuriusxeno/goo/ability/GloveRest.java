package com.mercuriusxeno.goo.ability;

/**
 * Where a lifted blob rests in its caster's view, a steady point near and
 * above the glove: a set way ahead of the eye, out to the glove's side and
 * below the look. The client draws the blob there and the server reads the
 * same numbers for the side of the sight line the blob stands on.
 * goop-and-slick-spray-emanates-from-the-glove
 * bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
 */
public final class GloveRest {

    /** How far ahead of the eye the rest stands, along the look. */
    public static final double AHEAD = 0.7;
    /** How far out to the glove's side of the look the rest stands. */
    public static final double TO_THE_GLOVE_SIDE = 0.3;
    /** How far below the look the rest stands, a little above the glove's palm. */
    public static final double BELOW = 0.15;

    private GloveRest() {
    }
}
