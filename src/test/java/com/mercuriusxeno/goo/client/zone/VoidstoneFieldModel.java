package com.mercuriusxeno.goo.client.zone;

import org.joml.Matrix4fc;
import java.util.List;

/**
 * voidstone.fsh's field on a top face on the CPU, as the floor's draw hands
 * it a fragment and the room it tiles at, so a test reads the nebula's glow
 * and the star the shader shows at a world point, in the room or in a copy.
 */
final class VoidstoneFieldModel {

    /** The shader's expressions the model mirrors, each of which voidstone.fsh or void_stars.glsl must hold. */
    static final List<String> MIRRORED = List.of(
            "ivec3 blockTexels = CameraBlockPos * 16;",
            "vec3 local = voidLocal * TEXELS;",
            "across = blockTexels.xz + ivec2(floor(local.xz));",
            "within = fract(local.xz);",
            "plane = blockTexels.y + int(floor(local.y + 0.5));",
            "int seed = plane * 3 + side;",
            "float pick = hash(ivec3(starTexel, seed));",
            "float rate = 0.03 + 0.09 * hash(ivec3(starTexel, seed + 7));",
            "uint mixed = mixBits(uint(point.x) ^ mixBits(uint(point.y) ^ mixBits(uint(point.z))));",
            "^ mixBits(uint(point.w) ^ mixBits(uint(seed))))));",
            "const float BRIGHT_STARS = 0.0012;",
            "const float DIM_STARS = 0.0035;",
            "ivec4 offset = ivec4(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1, (corner >> 3) & 1);",
            "vec4 weight = mix(1.0 - into, into, vec4(offset));",
            "total += hash4(ivec4(cell) + offset, seed) * weight.x * weight.y * weight.z * weight.w;",
            "const int CENTER_SPLIT = " + VoidstoneRoom.CENTER_SPLIT + ";",
            "float extent = TextureMat[1][0];",
            "int centerZ = roomCenter(TextureMat[0][2], TextureMat[0][3]);",
            "vec2 widthsOut = floor((toMiddle + 0.5 * width) / width) * vec2(1.0, top ? 1.0 : 0.0);",
            "toMiddle -= widthsOut * width;",
            "starTexel.x = alongCenter * BLOCK_TEXELS + HALF_BLOCK_TEXELS + int(floor(toMiddle.x));",
            "starTexel.y = centerZ * BLOCK_TEXELS + HALF_BLOCK_TEXELS + int(floor(toMiddle.y));",
            "return int(high) * CENTER_SPLIT + int(low);",
            "float width = 2.0 * extent * TEXELS;",
            "return float(texel - center * BLOCK_TEXELS - HALF_BLOCK_TEXELS) + within;",
            "float angle = TURN * texelsFromMiddle / width;",
            "return vec2(cos(angle), sin(angle)) * (width / TURN / float(NEBULA_TEXELS));",
            "vec4 drift = vec4(ticks * NEBULA_DRIFT, 0.0, ticks * NEBULA_DRIFT * 0.6, 0.0) / float(NEBULA_TEXELS);",
            "vec4 cloudAt = vec4(aroundLoop(toMiddle.x, width), alongY) + drift;",
            "nebula = smoothstep(GLOW_FROM, GLOW_WHOLE, mix(valueNoise4(cloudAt, seed + 11),",
            "valueNoise4(cloudAt * WISP_SCALE, seed + 13), WISP_SHARE));",
            "const int NEBULA_TEXELS = 96;",
            "const float WISP_SCALE = 2.0;",
            "const float WISP_SHARE = 0.35;",
            "const float GLOW_FROM = 0.35;",
            "const float GLOW_WHOLE = 0.95;");

    private static final int TEXELS = 16;
    private static final int HALF_BLOCK_TEXELS = 8;
    private static final int NEBULA_TEXELS = 96;
    private static final double WISP_SCALE = 2.0;
    private static final double WISP_SHARE = 0.35;
    private static final int WISP_SEED = 13;
    private static final double GLOW_WHOLE = 0.95;
    private static final double NEBULA_DRIFT = 0.004;
    private static final double DRIFT_SOUTH = 0.6;
    private static final int NEBULA_SEED = 11;
    private static final int RATE_SEED = 7;
    private static final int BRIGHTNESS_SEED = 3;
    private static final int SEEDS_PER_PLANE = 3;
    private static final int CORNERS = 16;
    private static final double GLOW_FROM = 0.35;
    private static final double RATE_FLOOR = 0.03;
    private static final double RATE_SPAN = 0.09;
    private static final double PHASE_TURNS = 61.0;
    private static final double TWINKLE_REST = 0.55;
    private static final double TWINKLE_SWING = 0.45;
    private static final double BRIGHT_STARS = 0.0012;
    private static final double DIM_STARS = 0.0035;
    private static final double BRIGHT_STAR_FLOOR = 0.7;
    private static final double BRIGHT_STAR_PEAK = 1.0;
    private static final double DIM_STAR_GLOW = 0.28;
    private static final double TURN = 2 * Math.PI;
    private static final double UINT_MAX = 4294967295.0;

    private VoidstoneFieldModel() {
    }

    /**
     * What a fragment shows: the nebula's glow under it and the star it holds.
     *
     * @param glow the nebula's glow, 0 to 1
     * @param star the star's brightness at the given clock, 0 where it holds none
     */
    record Look(double glow, double star) {
    }

    /**
     * The look of a floor block's top face at a world point, as the room's own draw hands the shader the
     * fragment: voidstone.vsh's place, the face laid camera-relative.
     *
     * @param x       the world x
     * @param z       the world z
     * @param floorY  the floor block's y
     * @param cameraX the camera's x
     * @param cameraZ the camera's z
     * @param room    the texture matrix the draw hands the shader
     * @param ticks   the clock
     * @return the look
     */
    static Look roomFace(double x, double z, int floorY, double cameraX, double cameraZ, Matrix4fc room,
            double ticks) {
        return fragment(new Place(x - cameraX, z - cameraZ, floorY, cameraX, cameraZ), room, ticks);
    }

    /** Where the vertex shader hands the fragment a top face's point: its offset from the camera. */
    private record Place(double fromCameraX, double fromCameraZ, int floorY, double cameraX, double cameraZ) {
    }

    private static Look fragment(Place place, Matrix4fc room, double ticks) {
        int blockX = (int) Math.floor(place.cameraX());
        int blockZ = (int) Math.floor(place.cameraZ());
        // voidLocal = Position - CameraOffset, CameraOffset being the camera's block less the camera
        float localX = (float) (place.fromCameraX() + (place.cameraX() - blockX)) * TEXELS;
        float localZ = (float) (place.fromCameraZ() + (place.cameraZ() - blockZ)) * TEXELS;
        int acrossX = blockX * TEXELS + (int) Math.floor(localX);
        int acrossZ = blockZ * TEXELS + (int) Math.floor(localZ);
        double withinX = localX - Math.floor(localX);
        double withinZ = localZ - Math.floor(localZ);
        int seed = (place.floorY() + 1) * TEXELS * SEEDS_PER_PLANE;
        double extent = room.m10();
        double width = 2.0 * extent * TEXELS;
        int centerX = roomCenter(room.m00(), room.m01());
        int centerZ = roomCenter(room.m02(), room.m03());
        double toMiddleX = fromMiddle(centerX, acrossX, withinX);
        double toMiddleZ = fromMiddle(centerZ, acrossZ, withinZ);
        // a place past the planes shows the room's own place whole room widths back
        toMiddleX -= Math.floor((toMiddleX + 0.5 * width) / width) * width;
        toMiddleZ -= Math.floor((toMiddleZ + 0.5 * width) / width) * width;
        int starX = centerX * TEXELS + HALF_BLOCK_TEXELS + (int) Math.floor(toMiddleX);
        int starZ = centerZ * TEXELS + HALF_BLOCK_TEXELS + (int) Math.floor(toMiddleZ);
        double[] alongX = aroundLoop(toMiddleX, width);
        double[] alongZ = aroundLoop(toMiddleZ, width);
        double drift = ticks * NEBULA_DRIFT / NEBULA_TEXELS;
        double[] cloudAt = {alongX[0] + drift, alongX[1], alongZ[0] + drift * DRIFT_SOUTH, alongZ[1]};
        double[] wispAt = new double[cloudAt.length];
        for (int axis = 0; axis < cloudAt.length; axis++) {
            wispAt[axis] = cloudAt[axis] * WISP_SCALE;
        }
        double glow = smoothstep(GLOW_FROM, GLOW_WHOLE, mix(valueNoise4(cloudAt, seed + NEBULA_SEED),
                valueNoise4(wispAt, seed + WISP_SEED), WISP_SHARE));
        return new Look(glow, starAt(starX, starZ, seed, ticks));
    }

    private static int roomCenter(float high, float low) {
        return (int) high * VoidstoneRoom.CENTER_SPLIT + (int) low;
    }

    private static double fromMiddle(int center, int texel, double within) {
        return texel - center * TEXELS - HALF_BLOCK_TEXELS + within;
    }

    private static double[] aroundLoop(double texelsFromMiddle, double width) {
        double angle = TURN * texelsFromMiddle / width;
        double radius = width / TURN / NEBULA_TEXELS;
        return new double[] {Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    private static double starAt(int texelX, int texelZ, int seed, double ticks) {
        double pick = hash(texelX, texelZ, seed);
        double rate = RATE_FLOOR + RATE_SPAN * hash(texelX, texelZ, seed + RATE_SEED);
        double bright = step(1.0 - BRIGHT_STARS, pick)
                * mix(BRIGHT_STAR_FLOOR, BRIGHT_STAR_PEAK, hash(texelX, texelZ, seed + BRIGHTNESS_SEED));
        double dim = step(1.0 - BRIGHT_STARS - DIM_STARS, pick) * (1.0 - step(1.0 - BRIGHT_STARS, pick))
                * DIM_STAR_GLOW;
        return (bright + dim) * (TWINKLE_REST + TWINKLE_SWING * Math.sin(ticks * rate + pick * PHASE_TURNS));
    }

    private static double valueNoise4(double[] point, int seed) {
        int[] cell = new int[point.length];
        double[] into = new double[point.length];
        for (int axis = 0; axis < point.length; axis++) {
            cell[axis] = (int) Math.floor(point[axis]);
            double share = point[axis] - cell[axis];
            into[axis] = share * share * (3.0 - 2.0 * share);
        }
        double total = 0;
        for (int corner = 0; corner < CORNERS; corner++) {
            double weight = 1;
            int[] at = new int[point.length];
            for (int axis = 0; axis < point.length; axis++) {
                int offset = corner >> axis & 1;
                at[axis] = cell[axis] + offset;
                weight *= offset == 1 ? into[axis] : 1.0 - into[axis];
            }
            total += hash4(at, seed) * weight;
        }
        return total;
    }

    private static double hash(int x, int y, int z) {
        int mixed = mixBits(x ^ mixBits(y ^ mixBits(z)));
        return Integer.toUnsignedLong(mixed) / UINT_MAX;
    }

    private static double hash4(int[] point, int seed) {
        int mixed = mixBits(point[0] ^ mixBits(point[1] ^ mixBits(point[2] ^ mixBits(point[3] ^ mixBits(seed)))));
        return Integer.toUnsignedLong(mixed) / UINT_MAX;
    }

    private static int mixBits(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        mixed ^= mixed >>> 16;
        return mixed;
    }

    private static double smoothstep(double from, double to, double value) {
        double t = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double mix(double from, double to, double share) {
        return from + (to - from) * share;
    }

    private static double step(double edge, double value) {
        return value < edge ? 0.0 : 1.0;
    }
}
