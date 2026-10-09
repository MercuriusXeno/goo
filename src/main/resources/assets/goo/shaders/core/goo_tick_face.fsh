#version 330

#moj_import <minecraft:globals.glsl>

// Tick's face overlay (decision tick-channel-marches-squares-on-the-face): a
// wave emitter on the aimed face. The face is cut into a grid of small
// squares; rings of squares light up and march out from the middle, each
// ring a square around the last, turned a little further than the one
// inside it so the march spirals, faster the more extra ticks the machine
// takes. Golden like the stasis shimmer (AilmentKind.STASIS). TRANSLUCENT blend.

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
// Squares across the face, and how many squares apart the rings march.
const float CELLS = 12.0;
const float RING_SPACING = 3.0;
// How far each ring turns past the one inside it, in turns of its square.
const float TWIST = 0.12;
const float TAU = 6.2831853;
const float PEAK_ALPHA = 0.55;

void main() {
    vec2 cell = floor((facePos * 0.5 + 0.5) * CELLS);
    vec2 inCell = fract((facePos * 0.5 + 0.5) * CELLS);
    vec2 fromMiddle = (cell + 0.5) / CELLS * 2.0 - 1.0;
    // The square ring a cell lies on: its Chebyshev distance from the middle, in cells.
    float ring = max(abs(fromMiddle.x), abs(fromMiddle.y)) * CELLS * 0.5;
    float ticks = GameTime * TICKS_PER_DAY;
    float march = ticks * rate * MAX_RINGS_PER_TICK * RING_SPACING;
    float wave = fract((ring - march) / RING_SPACING);
    float lit = 1.0 - smoothstep(0.0, 0.35, wave);
    // Each ring turns a little further than the one inside it, lighting one arc of its square brighter.
    float angle = atan(fromMiddle.y, fromMiddle.x) / TAU;
    float arc = 0.5 + 0.5 * cos(TAU * (angle - ring * TWIST + march * 0.05));
    lit *= mix(0.45, 1.0, arc);
    // A thin gap around each square, so the grid reads as squares.
    float edge = min(min(inCell.x, inCell.y), min(1.0 - inCell.x, 1.0 - inCell.y));
    float square = smoothstep(0.06, 0.14, edge);
    // Fade at the face's border, so the overlay sits inside the highlight.
    float border = 1.0 - smoothstep(0.85, 1.0, max(abs(facePos.x), abs(facePos.y)));
    float alpha = lit * square * border * PEAK_ALPHA * strength;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(mix(STASIS_GOLD, HIGHLIGHT, lit * arc * 0.5), alpha);
}
