#version 330

#moj_import <minecraft:matrix.glsl>
#moj_import <minecraft:globals.glsl>

// The sky funnel and the doorway it rises from (decision
// sky-funnel-points-down-to-the-doorway-from-any-distance): vanilla's End portal fragment shader as
// a window into space. Its layers are read along the direction each point is seen through, not at
// the screen point or on the surface, so the stars stand infinitely far behind it: they stay put
// as the viewer walks and never stretch on the surface. The direction is projected onto the three
// planes facing its axes, each weighted by how squarely the direction faces it, so no cube seam
// shows. It applies no fog: each vertex draws pulled inside the far plane, so fog would read the
// pulled distance, and the brightness already fades the funnel with distance.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 seenAlong;
in vec4 brightness;

// How many repeats of the star layers span a direction plane's unit: more repeats, smaller stars.
const float STARS_ACROSS = 1.5;
// How sharply each plane's share falls off as the direction turns from it.
const float PLANE_FOCUS = 8.0;

const vec3[] COLORS = vec3[](
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 end_portal_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (GameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );

    mat2 rotate = mat2_rotate_z(radians((layer * layer * 4321.0 + layer * 9.0) * 2.0));

    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);

    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

// The End portal's layers read at one point of a direction plane.
vec3 layersAt(vec2 across) {
    vec4 point = vec4(across * STARS_ACROSS, 0.0, 1.0);
    vec3 color = textureProj(Sampler0, point).rgb * COLORS[0];
    for (int i = 0; i < PORTAL_LAYERS; i++) {
        color += textureProj(Sampler1, point * end_portal_layer(float(i + 1))).rgb * COLORS[i];
    }
    return color;
}

out vec4 fragColor;

void main() {
    vec3 along = normalize(seenAlong);
    vec3 facing = abs(along);
    vec3 share = pow(facing, vec3(PLANE_FOCUS));
    share /= share.x + share.y + share.z;
    vec3 color = share.x * layersAt(along.yz / max(facing.x, 1e-4))
            + share.y * layersAt(along.xz / max(facing.y, 1e-4))
            + share.z * layersAt(along.xy / max(facing.z, 1e-4));
    fragColor = vec4(color * brightness.rgb, 1.0);
}
