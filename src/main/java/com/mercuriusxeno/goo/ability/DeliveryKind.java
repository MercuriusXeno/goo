package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;

/**
 * How an ability leaves the glove, the fixed code vocabulary an ability's
 * delivery block names (decision delivery-block-in-ability-json). Each kind
 * ships with one bundled ability proving it: glow laser and glow crystal the
 * beam, blaze spitfire the stream, ender blink and typhoon propulsion the
 * self, every other ability the arc. A further
 * ability is data, a goo_abilities JSON naming one of these kinds and its
 * params, and needs no new Java (decision one-proving-ability-per-kind).
 */
public enum DeliveryKind {
    /** A lobbed goo flying a parabola to its target. */
    ARC,
    /** A straight line reaching its target at a fixed speed. */
    BEAM,
    /** A held cone spat from the glove for as long as the use is held. */
    STREAM,
    /** A program run on the invoking player, sending nothing from the hand. */
    SELF;

    private static final String WHAT = "delivery kind";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<DeliveryKind> CODEC = LowerCaseEnumCodec.of(DeliveryKind.class, WHAT);
}
