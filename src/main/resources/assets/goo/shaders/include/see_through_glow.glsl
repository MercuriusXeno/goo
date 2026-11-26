#ifndef GOO_SEE_THROUGH_GLOW_GLSL
#define GOO_SEE_THROUGH_GLOW_GLSL

// An additive goo glow under improved transparency draws in the OIT phase as a see-through layer with coverage,
// which dims what it covers rather than brightening it, so each fragment is retuned toward the light the glow adds
// with improved transparency off: its coverage rises, so a faint layer still shows and a dense one nearly covers,
// and its color whitens as it grows dense, as stacked additive light whitens toward its middle. Off the OIT phase
// the glow is left as it is, its pipeline adding it
// (decision additive-glows-draw-as-translucent-layers-in-the-oit-phase).

// How far a fully dense layer whitens.
const float SEE_THROUGH_WHITEN = 0.5;

vec4 seeThroughGlow(vec4 glow) {
#ifdef OIT
    float coverage = glow.a * (2.0 - glow.a);
    return vec4(mix(glow.rgb, vec3(1.0), SEE_THROUGH_WHITEN * glow.a), coverage);
#else
    return glow;
#endif
}

#endif
