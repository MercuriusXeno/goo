#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A hole's mouth showing the view through it (decision hole-closes-the-space-to-an-opening-ahead):
// the far side rendered from the camera mirrored through the pair, sampled at this fragment's own
// screen point, laid over the wall see-through, inside an edge torn and shifting rather than a clean
// oval, a band of the mouth's color along the tear. The torn edge and the band draw on sixteen
// texels a block, each texel inside or outside the mouth whole, while the tear keeps shifting in
// time; the view through stays at full resolution
// (decision portals-and-wormhole-draw-sixteen-texels-a-block).

uniform sampler2D Sampler0;

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// How strongly the far side shows through the mouth, the wall showing through the rest.
const float VIEW_OPACITY = 0.6;
// How deep the tear bites into the oval, as a share of its radius.
const float TEAR_DEPTH = 0.16;
// The band of color along the tear runs whole over the edge's own texel, then fades inward over
// this many texels, measured in texels so it runs unbroken all round.
const float BAND_SOLID = 1.0;
const float BAND_FEATHER = 3.0;
// How fast the tear shifts; GameTime runs once a day, so this sets turns per day.
const float TEAR_SPEED = 2400.0;

float tear(float angle, float time) {
    return 0.5 * sin(angle * 5.0 + time * 1.7)
        + 0.3 * sin(angle * 11.0 - time * 2.9)
        + 0.2 * sin(angle * 23.0 + time * 4.3);
}

void main() {
    vec2 across = portalTexelCenter(frameCoord, texels) * 2.0 - 1.0;
    float out_ = length(across);
    float time = GameTime * TEAR_SPEED;
    float edge = 1.0 - TEAR_DEPTH * (0.5 + 0.5 * tear(atan(across.y, across.x), time));
    if (out_ > edge) {
        discard;
    }
    vec3 view = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    float band = portalTexelRim(portalTexelsInside(across, texels, edge), BAND_SOLID, BAND_FEATHER);
    vec3 color = mix(view, frameColor.rgb * 1.4, band);
    fragColor = vec4(color, mix(VIEW_OPACITY, 1.0, band));
}
