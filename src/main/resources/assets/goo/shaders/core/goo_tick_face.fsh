#version 330

#moj_import <minecraft:globals.glsl>

// Tick's face overlay (decision tick-channel-marches-squares-on-the-face): a
// wave emitter on the aimed face. The face is cut into a grid of small
// squares; rings of squares roll out from the middle without pause, each
// wavefront a bright ring of squares trailing dimmer ones behind it, so
// several rings stand lit in every frame and the march reads as motion. A
// bright arc sweeps around each ring, turned a little further on each ring
// out, so the march spirals. Faster the more extra ticks the machine takes.
// Golden like the stasis shimmer (AilmentKind.STASIS). TRANSLUCENT blend.

in vec2 facePos;
in float rate;
in float strength;

out vec4 fragColor;

// GameTime is the fraction of a 24000-tick day.
const float TICKS_PER_DAY = 24000.0;
// TickFaceOverlay.MAX_RINGS_PER_TICK: the rate the blue channel's one stands for.
const float MAX_RINGS_PER_TICK = 0.5;
const vec3 STASIS_GOLD = vec3(1.0, 0.831, 0.278);
const vec3 HIGHLIGHT = vec3(1.0, 0.957, 0.788);
// Squares across the face, and how many squares apart the wavefronts roll.
const float CELLS = 12.0;
const float RING_SPACING = 3.0;
// How steeply a wavefront's trail fades behind it.
const float TRAIL = 0.9;
// How far each ring's bright arc turns past the one inside it, in turns.
const float TWIST = 0.12;
// Turns a tick the bright arc sweeps around each ring, two a second.
const float SWEEP_PER_TICK = 0.1;
const float TAU = 6.2831853;
const float PEAK_ALPHA = 0.7;
// A faint glow every square keeps, so the face reads as gridded between wavefronts.
const float FLOOR_GLOW = 0.12;

void main() {
    vec2 grid = (facePos * 0.5 + 0.5) * CELLS;
    vec2 cell = floor(grid);
    vec2 inCell = fract(grid);
    vec2 fromMiddle = (cell + 0.5) / CELLS * 2.0 - 1.0;
    // The square ring a cell lies on: its Chebyshev distance from the middle, in cells.
    float ring = max(abs(fromMiddle.x), abs(fromMiddle.y)) * CELLS * 0.5;
    float ticks = GameTime * TICKS_PER_DAY;
    float march = ticks * rate * MAX_RINGS_PER_TICK * RING_SPACING;
    // How far behind the nearest wavefront this ring lies, 0 at the front to 1 at the next.
    float behind = fract((march - ring) / RING_SPACING);
    float lit = exp(-TRAIL * behind * RING_SPACING);
    // The bright arc sweeping around each ring, turned further on each ring out.
    float angle = atan(fromMiddle.y, fromMiddle.x) / TAU;
    float arc = 0.5 + 0.5 * cos(TAU * (angle - ring * TWIST - ticks * SWEEP_PER_TICK));
    float glow = max(FLOOR_GLOW, lit * mix(0.55, 1.0, arc));
    // A thin gap around each square, so the grid reads as squares.
    float edge = min(min(inCell.x, inCell.y), min(1.0 - inCell.x, 1.0 - inCell.y));
    float square = smoothstep(0.06, 0.14, edge);
    // Fade at the face's border, so the overlay sits inside the highlight.
    float border = 1.0 - smoothstep(0.85, 1.0, max(abs(facePos.x), abs(facePos.y)));
    float alpha = glow * square * border * PEAK_ALPHA * strength;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(mix(STASIS_GOLD, HIGHLIGHT, lit * arc), alpha);
}
