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
    /** Hex's charm: a dark purple glisten, drawn light so the mob shows through. */
    HEX(0x6A1FB0, AilmentPattern.GLINT, 0.55f),
    /** Petrify: a stone encasement. */
    PETRIFY(0x8C8A86, AilmentPattern.STONE),
    /** Frozen: frost. */
    FROZEN(0xC4ECFF, AilmentPattern.FROST),
    /** Glow's glisten: Scry's sweep marks a mob in glow yellow (decision scry-sphere-reveals-faces-and-glistens-mobs). */
    GLOW(0xFFE628, AilmentPattern.GLINT, 0.4f),
    /** Zoo's rally: a tawny glisten over the armed mob (decision zoo-rally-arms-the-peaceful). */
    ZOO(0xD89858, AilmentPattern.GLINT, 0.5f);

    /** Codec for the kind as an ability JSON writes it, in lower case. */
    public static final Codec<AilmentKind> CODEC = LowerCaseEnumCodec.of(AilmentKind.class, "ailment kind");

    /** The opacity an ailment draws at unless it names its own. */
    private static final float FULL_OPACITY = 1f;

    private final int rgb;
    private final AilmentPattern pattern;
    private final float opacity;

    AilmentKind(int rgb, AilmentPattern pattern) {
        this(rgb, pattern, FULL_OPACITY);
    }

    AilmentKind(int rgb, AilmentPattern pattern, float opacity) {
        this.rgb = rgb;
        this.pattern = pattern;
        this.opacity = opacity;
    }

    /** @return the overlay's color, 0xRRGGBB */
    public int rgb() {
        return rgb;
    }

    /** @return the field the overlay draws */
    public AilmentPattern pattern() {
        return pattern;
    }

    /** @return how opaque the overlay draws at full strength, 0 to 1 */
    public float opacity() {
        return opacity;
    }
}
