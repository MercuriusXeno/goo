// The copies of an Outsider room (decision room-tiles-to-render-distance-held-to-the-picture): each instance of
// the room's mesh is one copy, moved by its own translation. RoomCopyDraws writes each copy's east then south as a
// fixed point of sixteen bits whole and sixteen fraction, little end first, one byte a texel.

uniform isamplerBuffer RoomCopyTranslations;

const int BYTES_PER_FIXED = 4;
const int FIXED_PER_COPY = 2;
const float FIXED_ONE = 65536.0;

float roomCopyFixedAt(int first) {
    int bits = 0;
    for (int i = 0; i < BYTES_PER_FIXED; i++) {
        bits |= (texelFetch(RoomCopyTranslations, first + i).r & 0xFF) << (8 * i);
    }
    return float(bits) / FIXED_ONE;
}

vec3 roomCopyTranslation() {
    int first = gl_InstanceID * FIXED_PER_COPY * BYTES_PER_FIXED;
    return vec3(roomCopyFixedAt(first), 0.0, roomCopyFixedAt(first + BYTES_PER_FIXED));
}
