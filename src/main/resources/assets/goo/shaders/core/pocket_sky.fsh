#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <goo:void_stars.glsl>

// The pocket's sky stars (decision sky-stars-sit-distant-sparser-and-dimmer-than-voidstone): drawn in the
// voidstone's own star color at the brightness PocketStarSky gave each star, fainter than the voidstone's,
// twinkling on the voidstone's clock. Red carries the brightness, green the twinkle rate and blue its phase.

in vec4 starColor;

out vec4 fragColor;

const float TICKS_PER_CLOCK = 24000.0;
const float SLOWEST_TWINKLE = 0.03;
const float TWINKLE_SPAN = 0.09;
const float PHASE_SPAN = 61.0;

void main() {
    float ticks = GameTime * TICKS_PER_CLOCK;
    float rate = SLOWEST_TWINKLE + TWINKLE_SPAN * starColor.g;
    fragColor = vec4(STAR * starColor.r * twinkle(ticks, rate, starColor.b * PHASE_SPAN), 1.0);
}
