package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * A status ailment the overlay draws on a mob or player model, each with
 * its own color and pattern.
 * Decision ailment-overlay-shader-per-ailment.
 */
public enum AilmentKind {
    /** Aeon's stasis: a golden shimmer. */
    STASIS(0xFFD447, AilmentPattern.GLINT),
    /** Haste: the stasis shimmer's golden look. */
    HASTE(0xFFBF2E, AilmentPattern.GLINT),
    /** Scales: diamond blue, faceted rather than glinting. */
    SCALES(0x4AEDD9, AilmentPattern.FACETS),
    /** Banish: an ender shimmer. */
    BANISH(0xB05CFF, AilmentPattern.SHIMMER),
    /** Teleportitis: the banish shimmer. */
    TELEPORTITIS(0xB05CFF, AilmentPattern.SHIMMER),
    /** Hex's charm: a dark purple glisten. */
    HEX(0x6A1FB0, AilmentPattern.GLINT),
    /** Petrify: a stone encasement. */
    PETRIFY(0x8C8A86, AilmentPattern.STONE),
    /** Frozen: frost. */
    FROZEN(0xC4ECFF, AilmentPattern.FROST);

    /** Codec for the kind as an ability JSON writes it, in lower case. */
    public static final Codec<AilmentKind> CODEC = LowerCaseEnumCodec.of(AilmentKind.class, "ailment kind");

    private final int rgb;
    private final AilmentPattern pattern;

    AilmentKind(int rgb, AilmentPattern pattern) {
        this.rgb = rgb;
        this.pattern = pattern;
    }

    /** @return the overlay's color, 0xRRGGBB */
    public int rgb() {
        return rgb;
    }

    /** @return the field the overlay draws */
    public AilmentPattern pattern() {
        return pattern;
    }
}
