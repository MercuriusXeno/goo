// The Outsider's stars, read by the voidstone and by the pocket's sky, so both draw the one pale star color
// sky-stars-sit-distant-sparser-and-dimmer-than-voidstone

const vec3 STAR = vec3(0.87, 0.86, 0.96);

// The voidstone's stars: the share of its texels holding a bright star and a dim one, the bright star's
// brightness between its floor and its peak, and the dim star's glow. The sky's stars stay below both.
const float BRIGHT_STARS = 0.012;
const float DIM_STARS = 0.035;
const float BRIGHT_STAR_FLOOR = 0.7;
const float BRIGHT_STAR_PEAK = 1.0;
const float DIM_STAR_GLOW = 0.28;

// The room's edge, the voidstone its boundary cuts: the share of its texels holding a star, about a third of the
// voidstone's, bright and dim in the voidstone's own proportion (decision room-edge-draws-a-sparser-starfield).
const float EDGE_STARS = 0.016;

// A star's twinkle on its own clock: a share of its brightness swinging between a tenth and the whole.
float twinkle(float ticks, float rate, float phase) {
    return 0.55 + 0.45 * sin(ticks * rate + phase);
}
