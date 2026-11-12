#version 330

#moj_import <minecraft:globals.glsl>

// Bore's drill (decision bore-shows-a-big-drill-from-the-glove): a solid cone
// bit, as the Bore icon draws it. Steel lands with brown grooves cut between
// them, two grooves winding round the bit from the glove to the tip, a dark
// line where a groove meets a land, the bit lit from the upper left of the
// view as the icon is. The grooves turn about the bit while Bore runs, so the
// bit spins. Opaque.

in float along;
in vec2 discPos;
in vec3 viewNormal;

out vec4 fragColor;

const float TAU = 6.28318530718;
// GameTime runs from zero to one over a day of 24000 ticks; this many seconds make one day. The spin
// below turns a whole number of times a day, so the bit spins on unbroken as the day wraps.
const float SECONDS_PER_DAY = 1200.0;
// The Bore icon's tones (rock_bore.png): the steel lands, light to dark, and the grooves, light to dark.
const vec3 STEEL_LIGHT = vec3(0.886, 0.914, 0.949);
const vec3 STEEL_DARK = vec3(0.420, 0.420, 0.420);
const vec3 GROOVE_LIGHT = vec3(0.757, 0.584, 0.427);
const vec3 GROOVE_DARK = vec3(0.345, 0.220, 0.141);
const vec3 EDGE_COLOR = vec3(0.149, 0.141, 0.133);
// Grooves winding round the bit, and the turns each makes from the glove to the tip.
const float GROOVES = 2.0;
const float TURNS = 3.0;
// How many turns a second the bit spins. Tuned by eye.
const float SPIN_TURNS_PER_SECOND = 3.0;
// The share of each groove's period the groove takes; the land takes the rest.
const float GROOVE_SHARE = 0.45;
// How wide the dark line where a groove meets a land runs, as a share of the period.
const float EDGE_WIDTH = 0.035;
// The light, in view space: from the upper left, toward the viewer.
const vec3 LIGHT = vec3(-0.55, 0.6, 0.58);
const float AMBIENT = 0.35;

void main() {
    float around = atan(discPos.y, discPos.x) / TAU;
    float seconds = GameTime * SECONDS_PER_DAY;
    float period = fract((around + along * TURNS + seconds * SPIN_TURNS_PER_SECOND) * GROOVES);

    vec3 normal = normalize(viewNormal);
    if (!gl_FrontFacing) {
        normal = -normal;
    }
    float lit = AMBIENT + (1.0 - AMBIENT) * max(dot(normal, normalize(LIGHT)), 0.0);

    vec3 color;
    if (period < GROOVE_SHARE) {
        // A groove: its floor curves down and back up, darkest in the middle.
        float depth = sin(period / GROOVE_SHARE * 3.14159265);
        color = mix(GROOVE_LIGHT, GROOVE_DARK, 0.25 + 0.5 * depth) * (0.55 + 0.6 * lit);
    } else {
        color = mix(STEEL_DARK, STEEL_LIGHT, lit);
    }
    float edge = min(period, abs(period - GROOVE_SHARE));
    edge = min(edge, 1.0 - period);
    color = mix(EDGE_COLOR, color, smoothstep(0.0, EDGE_WIDTH, edge));

    fragColor = vec4(color, 1.0);
}
