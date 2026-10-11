package com.mercuriusxeno.goo.ability.program;

/**
 * The field an ailment overlay draws over a model; the overlay shader picks
 * its pattern by the constant's ordinal, so the order here is the shader's
 * PATTERN_* numbering.
 * Decision ailment-overlay-shader-per-ailment.
 */
public enum AilmentPattern {
    /** A scrolling shimmer like the enchantment glint. */
    GLINT,
    /** Crisp diamond facets catching the light in turn. */
    FACETS,
    /** A swirling speckled shimmer, the ender warp's look. */
    SHIMMER,
    /** A rough opaque stone crust over the whole model. */
    STONE,
    /** Pale frost crystals rimming the model. */
    FROST,
    /** Veins writhing and coiling over the model (decision xeno-blob-mutates-the-struck). */
    WRITHE
}
