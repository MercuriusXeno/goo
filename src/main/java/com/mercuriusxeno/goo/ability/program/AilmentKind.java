package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * A status ailment the overlay draws on a mob or player model, each with
 * its own color and pattern.
 * Decision ailment-overlay-shader-per-ailment.
 */
public enum AilmentKind {
    /** Rewind's stasis: a golden shimmer. */
    STASIS(0xFFD447, AilmentPattern.GLINT),
    /** Scales: diamond blue, faceted rather than glinting. */
    SCALES(0x4AEDD9, AilmentPattern.FACETS),
    /** Zone: an ender shimmer. */
    ZONE(0xB05CFF, AilmentPattern.SHIMMER),
    /** Shifter: the zone shimmer. */
    SHIFTER(0xB05CFF, AilmentPattern.SHIMMER),
    /** Hex's charm: a dark purple glisten, drawn light so the mob shows through. */
    HEX(0x6A1FB0, AilmentPattern.GLINT, 0.55f),
    /** Petrify: a stone encasement. */
    PETRIFY(0x8C8A86, AilmentPattern.STONE),
    /** Frozen: frost. */
    FROZEN(0xC4ECFF, AilmentPattern.FROST),
    /** Glow's glisten: Sunbeam and Lux mark a mob in glow yellow. */
    GLOW(0xFFE628, AilmentPattern.GLINT, 0.4f),
    /**
     * Astral's glisten: Scry's sweep marks a mob in the astral type's color.
     * decision scry-sphere-reveals-faces-and-glistens-mobs
     * decision scry-ships-on-astral
     */
    ASTRAL(GooTypes.ASTRAL, AilmentPattern.GLINT, 0.4f),
    /** Weird's wobble: a magenta gel shell that jiggles and stretches (decision weird-bounces-and-softens-harm). */
    WOBBLE(0xFF33CC, AilmentPattern.WOBBLE, 0.6f),
    /** Grab's hold: a faint kinetic orange shimmer, and the flail its pose stamp reads (decision grab-holds-and-throws-a-physics-body). */
    GRABBED(0xFF7A1A, AilmentPattern.SHIMMER, 0.35f),
    /** Telekinesis: the kinetic shimmer, fainter, on the player and the arm (decision telekinesis-enacts-at-extended-reach). */
    TELEKINESIS(0xFF7A1A, AilmentPattern.SHIMMER, 0.2f),
    /** Xeno's mutation: sickly green veins writhing over the mob while it stays mutated (decision xeno-blob-mutates-the-struck). */
    MUTATED(0xA8CC48, AilmentPattern.WRITHE, 0.6f),
    /** Ancient: aged stone veined with gold (decision ancient-makes-the-player-immortal). */
    ANCIENT(0xC9A85C, AilmentPattern.STONE, 0.75f),
    /** Zoo's rally: a tawny glisten over the armed mob (decision zoo-rally-arms-the-peaceful). */
    ZOO(0xD89858, AilmentPattern.GLINT, 0.5f);

    /** Codec for the kind as an ability JSON writes it, in lower case. */
    public static final Codec<AilmentKind> CODEC = LowerCaseEnumCodec.of(AilmentKind.class, "ailment kind");

    /** The opacity an ailment draws at unless it names its own. */
    private static final float FULL_OPACITY = 1f;
    /** The color a type-tinted kind draws where its type resolves to no entry, matching untinted goo. */
    private static final int UNRESOLVED_TINT = 0xFFFFFF;

    private final int rgb;
    private final Optional<ResourceKey<GooTypeDefinition>> tintType;
    private final AilmentPattern pattern;
    private final float opacity;

    AilmentKind(int rgb, AilmentPattern pattern) {
        this(rgb, pattern, FULL_OPACITY);
    }

    AilmentKind(int rgb, AilmentPattern pattern, float opacity) {
        this(rgb, Optional.empty(), pattern, opacity);
    }

    AilmentKind(ResourceKey<GooTypeDefinition> tintType, AilmentPattern pattern, float opacity) {
        this(UNRESOLVED_TINT, Optional.of(tintType), pattern, opacity);
    }

    AilmentKind(int rgb, Optional<ResourceKey<GooTypeDefinition>> tintType, AilmentPattern pattern, float opacity) {
        this.rgb = rgb;
        this.tintType = tintType;
        this.pattern = pattern;
        this.opacity = opacity;
    }

    /** @return the overlay's fixed color, 0xRRGGBB; a type-tinted kind answers white */
    public int rgb() {
        return rgb;
    }

    /** @return the goo type whose colors tint the overlay, empty for a kind with a fixed color */
    public Optional<ResourceKey<GooTypeDefinition>> tintType() {
        return tintType;
    }

    /**
     * The overlay's color: the tint type's color where the kind names one,
     * its fixed color otherwise, so a datapack recoloring the type recolors the overlay.
     *
     * @param typeColor answers a goo type's color, called only for a type-tinted kind
     * @return 0xRRGGBB
     */
    public int rgb(ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        return tintType.map(typeColor::applyAsInt).orElse(rgb);
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
