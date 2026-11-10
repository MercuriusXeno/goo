#version 330

#moj_import <minecraft:globals.glsl>
#moj_import "portal_texels.glsl"

// A portal with no view to show (decision portals-link-by-looking-through-portal-vision): an
// oval filling the frame, near black at its heart, the portal's color swirling in from a bright
// glowing rim, so it reads as open but leading nowhere yet. All of it draws on sixteen texels a
// block; the swirl turns smoothly in time (decision portals-and-wormhole-draw-sixteen-texels-a-block).

in vec2 frameCoord;
flat in vec2 texels;
in vec4 frameColor;

out vec4 fragColor;

// GameTime runs over a day of 24000 ticks; this many seconds make one day.
const float SECONDS_PER_DAY = 1200.0;
// How many arms the swirl turns with.
const float ARMS = 3.0;
// How tightly the arms wind toward the heart.
const float WIND = 9.0;
// How fast the swirl turns, radians per second.
const float TURN = 2.5;
const float HEART = 0.03;

void main() {
    vec2 across = portalTexelCenter(frameCoord, texels) * 2.0 - 1.0;
    float out_ = length(across);
    if (out_ > 1.0) {
        discard;
    }
    float seconds = GameTime * SECONDS_PER_DAY;
    float angle = atan(across.y, across.x);
    float swirl = 0.5 + 0.5 * sin(angle * ARMS + out_ * WIND - seconds * TURN);
    float rim = portalTexelRim(portalTexelsInside(across, texels, 1.0));
    vec3 heart = vec3(HEART);
    vec3 arms = frameColor.rgb * swirl * out_ * 0.8;
    vec3 glow = frameColor.rgb * rim * 1.6;
    fragColor = vec4(heart + arms + glow, frameColor.a);
}
