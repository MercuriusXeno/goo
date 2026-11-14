package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * A status ailment drawn on a mob or player model, each with its own color:
 * an overlay field in its pattern, or a pulsing outline where it marks the
 * mob rather than coating it.
 * Decision ailment-overlay-shader-per-ailment.
 * Decision ailment-glisten-becomes-a-pulsing-outline.
 */
public enum AilmentKind {
    /** Rewind's stasis: a gold outline. */
    STASIS(0xFFD447),
    /** Scales: diamond blue, faceted. */
    SCALES(0x4AEDD9, AilmentPattern.FACETS),
    /** Zone: an ender shimmer. */
    ZONE(0xB05CFF, AilmentPattern.SHIMMER),
    /** Shifter: the zone shimmer. */
    SHIFTER(0xB05CFF, AilmentPattern.SHIMMER),
    /** Hex's charm: a dark purple outline. */
    HEX(0x6A1FB0),
    /** Petrify: a stone encasement. */
    PETRIFY(0x8C8A86, AilmentPattern.STONE),
    /** Frozen: frost. */
    FROZEN(0xC4ECFF, AilmentPattern.FROST),
    /** Glow's mark: Sunbeam and Lux outline a mob in glow yellow. */
    GLOW(0xFFE628),
    /**
     * Astral's mark: Scry's sweep outlines a mob in the astral type's color.
     * decision scry-ships-on-astral
     */
    ASTRAL(AilmentKind.UNRESOLVED_TINT),
    /** Weird's wobble: a magenta gel shell that jiggles and stretches (decision weird-bounces-and-softens-harm). */
    WOBBLE(0xFF33CC, AilmentPattern.WOBBLE, 0.6f),
    /**
     * Grab's hold: the flail its pose stamp reads, drawing no overlay
     * (decisions grab-holds-and-throws-a-physics-body, kinetic-is-electric-blue-and-white).
     */
    GRABBED(0xFFFFFF, AilmentPattern.SHIMMER, 0f),
    /** Ancient: aged stone veined with gold (decision ancient-makes-the-player-immortal). */
    ANCIENT(0xC9A85C, AilmentPattern.STONE, 0.75f);

    /** Codec for the kind as an ability JSON writes it, in lower case. */
    public static final Codec<AilmentKind> CODEC = LowerCaseEnumCodec.of(AilmentKind.class, "ailment kind");

    /** The opacity an ailment draws at unless it names its own. */
    private static final float FULL_OPACITY = 1f;
    /** The color a type-tinted kind draws where its type resolves to no entry, matching untinted goo. */
    private static final int UNRESOLVED_TINT = 0xFFFFFF;
    /**
     * The kinds whose color is a goo type's rather than their own, held
     * statically so the enum carries no unserializable instance field.
     */
    private static final Map<AilmentKind, ResourceKey<GooTypeDefinition>> TINT_TYPES =
            Map.of(ASTRAL, GooTypes.ASTRAL);
    /** The kinds that ride the render state and draw no overlay, held statically as TINT_TYPES is. */
    private static final Set<AilmentKind> UNDRAWN = Set.of(GRABBED);

    private final int rgb;
    /** The overlay field's pattern; null for a kind drawing the outline instead. */
    private final @Nullable AilmentPattern pattern;
    private final float opacity;

    AilmentKind(int rgb) {
        this(rgb, null, FULL_OPACITY);
    }

    AilmentKind(int rgb, AilmentPattern pattern) {
        this(rgb, pattern, FULL_OPACITY);
    }

    AilmentKind(int rgb, @Nullable AilmentPattern pattern, float opacity) {
        this.rgb = rgb;
        this.pattern = pattern;
        this.opacity = opacity;
    }

    /** @return the ailment's fixed color, 0xRRGGBB; a type-tinted kind answers white */
    public int rgb() {
        return rgb;
    }

    /**
     * The ailment's color: the tint type's color where the kind names one,
     * its fixed color otherwise, so a datapack recoloring the type recolors
     * the overlay or the outline.
     *
     * @param typeColor answers a goo type's color, called only for a type-tinted kind
     * @return 0xRRGGBB
     */
    public int rgb(ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        ResourceKey<GooTypeDefinition> tintType = TINT_TYPES.get(this);
        return tintType == null ? rgb : typeColor.applyAsInt(tintType);
    }

    /** @return the field the overlay draws, null for a kind drawing the outline */
    public @Nullable AilmentPattern pattern() {
        return pattern;
    }

    /** @return how opaque the overlay draws at full strength, 0 to 1 */
    public float opacity() {
        return opacity;
    }

    /**
     * Answers whether the ailment draws an overlay field on the model, rather
     * than an outline, or only riding the render state as Grab's flail does.
     *
     * @return true for an ailment the overlay draws
     */
    public boolean drawsOverlay() {
        return pattern != null && !UNDRAWN.contains(this);
    }

    /**
     * Answers whether the ailment marks its mob with the pulsing outline in
     * its color rather than drawing an overlay field.
     *
     * @return true for an outlined ailment
     */
    public boolean drawsOutline() {
        return pattern == null;
    }
}
