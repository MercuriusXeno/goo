#version 330

// Metal goo's burnout explosion (decision elemental-explosion-per-type): an
// urchin of chrome spikes. Each cone shades from C0C0C0 to E8E8E8 by how
// squarely it faces the eye, a specular band sweeps down the spikes as the
// show runs, and each tip carries a white glint. TRANSLUCENT blend, the
// spikes drawn solid at the vertex alpha's opacity, whole for a landing.

in vec3 viewPos;
in vec3 viewNormal;
in float progress;
in float along;
in float opacity;

out vec4 fragColor;

const vec3 SHADOW_COLOR = vec3(0.753, 0.753, 0.753);
const vec3 LIT_COLOR = vec3(0.91, 0.91, 0.91);
const vec3 GLINT_COLOR = vec3(1.0, 1.0, 1.0);
const float BAND_SWEEPS = 3.0;
const float BAND_WIDTH = 0.12;
const float TIP_GLINT_START = 0.85;

void main() {
    float facing = abs(dot(normalize(viewNormal), normalize(-viewPos)));
    vec3 color = mix(SHADOW_COLOR, LIT_COLOR, facing);

    // The band runs base to tip BAND_SWEEPS times over the show.
    float bandPos = fract(progress * BAND_SWEEPS);
    float offset = (along - bandPos) / BAND_WIDTH;
    float band = exp(-offset * offset);
    float tipGlint = smoothstep(TIP_GLINT_START, 1.0, along);
    color = mix(color, GLINT_COLOR, clamp(band * 0.8 + tipGlint, 0.0, 1.0));

    fragColor = vec4(color, opacity);
}
