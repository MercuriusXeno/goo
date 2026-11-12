package com.mercuriusxeno.goo.client.outsider;

import org.joml.Matrix4fc;
import java.util.List;

/**
 * voidstone.fsh's field on a top face on the CPU, as the room's draw and a
 * copy's draw hand it a fragment and the room it tiles at, so a test reads
 * the nebula's glow and the star the shader shows at a world point, and
 * whether it draws the point at all.
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
            "float pick = hash(ivec3(across, seed));",
            "float rate = 0.03 + 0.09 * hash(ivec3(across, seed + 7));",
            "uint mixed = mixBits(uint(point.x) ^ mixBits(uint(point.y) ^ mixBits(uint(point.z))));",
            "^ mixBits(uint(point.w) ^ mixBits(uint(seed))))));",
            "ivec4 offset = ivec4(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1, (corner >> 3) & 1);",
            "vec4 weight = mix(1.0 - into, into, vec4(offset));",
            "total += hash4(ivec4(cell) + offset, seed) * weight.x * weight.y * weight.z * weight.w;",
            "const int CENTER_SPLIT = " + VoidstoneRoom.CENTER_SPLIT + ";",
            "float extent = TextureMat[1][0];",
            "int centerX = roomCenter(TextureMat[0][0], TextureMat[0][1]);",
            "int centerZ = roomCenter(TextureMat[0][2], TextureMat[0][3]);",
            "return int(high) * CENTER_SPLIT + int(low);",
            "float width = 2.0 * extent * TEXELS;",
            "return float(texel - center * BLOCK_TEXELS - HALF_BLOCK_TEXELS) + within;",
            "if (abs(toMiddle.x) > width * 0.5 || (facing.y > 0.5 && abs(toMiddle.y) > width * 0.5)) {",
            "float angle = TURN * texelsFromMiddle / width;",
            "return vec2(cos(angle), sin(angle)) * (width / TURN / float(NEBULA_TEXELS));",
            "vec4 drift = vec4(ticks * NEBULA_DRIFT, 0.0, ticks * NEBULA_DRIFT * 0.6, 0.0) / float(NEBULA_TEXELS);",
            "nebula = smoothstep(0.45, 1.0, valueNoise4(vec4(aroundLoop(toMiddle.x, width), alongY) + drift, seed + 11));");

    private static final int TEXELS = 16;
    private static final int HALF_BLOCK_TEXELS = 8;
    private static final int NEBULA_TEXELS = 40;
    private static final double NEBULA_DRIFT = 0.004;
    private static final double DRIFT_SOUTH = 0.6;
    private static final int NEBULA_SEED = 11;
    private static final int RATE_SEED = 7;
    private static final int BRIGHTNESS_SEED = 3;
    private static final int SEEDS_PER_PLANE = 3;
    private static final int CORNERS = 16;
    private static final double GLOW_FROM = 0.45;
    private static final double RATE_FLOOR = 0.03;
    private static final double RATE_SPAN = 0.09;
    private static final double PHASE_TURNS = 61.0;
    private static final double TWINKLE_REST = 0.55;
    private static final double TWINKLE_SWING = 0.45;
    private static final double BRIGHT_STARS = 0.012;
    private static final double DIM_STARS = 0.035;
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
     * @param glow  the nebula's glow, 0 to 1
     * @param star  the star's brightness at the given clock, 0 where it holds none
     * @param drawn whether the shader draws the fragment, false past the room's boundary planes
     */
    record Look(double glow, double star, boolean drawn) {
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

    /**
     * The look of a copy's floor block's top face at a world point, as a copy's draw hands the shader the
     * fragment: voidstone_copy.vsh's place, the room's own point, the copy's translation moving only where it
     * draws.
     *
     * @param x       the world x the copy draws at
     * @param z       the world z the copy draws at
     * @param floorY  the floor block's y
     * @param offset  the copy's offset
     * @param cameraX the camera's x
     * @param cameraZ the camera's z
     * @param room    the texture matrix the draw hands the shader
     * @param ticks   the clock
     * @return the look
     */
    static Look copyFace(double x, double z, int floorY, RoomCopies.Offset offset, double cameraX, double cameraZ,
            Matrix4fc room, double ticks) {
        return fragment(new Place(x - offset.east() - cameraX, z - offset.south() - cameraZ, floorY, cameraX,
                cameraZ), room, ticks);
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
        double toMiddleX = fromMiddle(roomCenter(room.m00(), room.m01()), acrossX, withinX);
        double toMiddleZ = fromMiddle(roomCenter(room.m02(), room.m03()), acrossZ, withinZ);
        boolean drawn = Math.abs(toMiddleX) <= width * 0.5 && Math.abs(toMiddleZ) <= width * 0.5;
        double[] alongX = aroundLoop(toMiddleX, width);
        double[] alongZ = aroundLoop(toMiddleZ, width);
        double drift = ticks * NEBULA_DRIFT / NEBULA_TEXELS;
        double glow = smoothstep(GLOW_FROM, 1.0, valueNoise4(new double[] {alongX[0] + drift, alongX[1],
            alongZ[0] + drift * DRIFT_SOUTH, alongZ[1]}, seed + NEBULA_SEED));
        return new Look(glow, starAt(acrossX, acrossZ, seed, ticks), drawn);
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
