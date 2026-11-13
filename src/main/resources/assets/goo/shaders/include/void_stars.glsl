// The Outsider's stars, read by the voidstone and by the pocket's sky, so both draw the one pale star color
// sky-stars-sit-distant-sparser-and-dimmer-than-voidstone

const vec3 STAR = vec3(0.87, 0.86, 0.96);

// The voidstone's stars: the share of its texels holding a bright star and a dim one, very, very sparse, a
// tenth of the share the floor first held, as the operator asked; the bright star's brightness between its floor
// and its peak, and the dim star's glow. The sky's stars stay fainter than both.
const float BRIGHT_STARS = 0.0012;
const float DIM_STARS = 0.0035;
const float BRIGHT_STAR_FLOOR = 0.7;
const float BRIGHT_STAR_PEAK = 1.0;
const float DIM_STAR_GLOW = 0.28;

// A star's twinkle on its own clock: a share of its brightness swinging between a tenth and the whole.
float twinkle(float ticks, float rate, float phase) {
    return 0.55 + 0.45 * sin(ticks * rate + phase);
}
