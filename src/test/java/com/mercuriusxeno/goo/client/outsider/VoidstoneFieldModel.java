package com.mercuriusxeno.goo.client.outsider;

import org.joml.Matrix4fc;
import java.util.List;

/**
 * voidstone.fsh's field on a top face on the CPU, as the room's draw and a
 * copy's draw hand it a fragment, the room it wraps at and whether the face
 * draws sparse stars, so a test reads the glow and the star the shader shows
 * at a world point, and whether it draws the point at all.
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
            "ivec2 drift = ivec2(int(ticks * NEBULA_DRIFT), int(ticks * NEBULA_DRIFT * 0.6));",
            "float glow = smoothstep(0.45, 1.0, valueNoise(texel + drift, within, seed + 11));",
            "float pick = hash(ivec3(texel, seed));",
            "float rate = 0.03 + 0.09 * hash(ivec3(texel, seed + 7));",
            "uint mixed = mixBits(uint(point.x) ^ mixBits(uint(point.y) ^ mixBits(uint(point.z))));",
            "const int CENTER_SPLIT = " + VoidstoneRoom.CENTER_SPLIT + ";",
            "const float WRAP_BAND = 40.0;",
            "float extent = TextureMat[1][0];",
            "int centerX = roomCenter(TextureMat[0][0], TextureMat[0][1]);",
            "int centerZ = roomCenter(TextureMat[0][2], TextureMat[0][3]);",
            "return int(high) * CENTER_SPLIT + int(low);",
            "float band = min(WRAP_BAND, extent * TEXELS);",
            "return float(center * BLOCK_TEXELS + HALF_BLOCK_TEXELS - texel) + extent * TEXELS - within;",
            "if (any(lessThan(toFar, vec2(0.0))) || any(greaterThan(toFar, vec2(width)))) {",
            "weight = vec2(1.0) - smoothstep(vec2(0.0), vec2(band), toFar);",
            "float width = 2.0 * extent * TEXELS;",
            "float moved = within - fract(width);",
            "int carry = int(floor(moved));",
            "texelBack = texel - int(floor(width)) + carry;",
            "withinBack = moved - float(carry);",
            "vec2 field = mix(mix(here, behindX, weight.x), mix(behindY, behindBoth, weight.x), weight.y);",
            "float starShare = voidSparse > 0.5 ? EDGE_STARS / (BRIGHT_STARS + DIM_STARS) : 1.0;",
            "float brightShare = BRIGHT_STARS * starShare;",
            "float starredShare = (BRIGHT_STARS + DIM_STARS) * starShare;",
            "float bright = step(1.0 - brightShare, pick)",
            "float dim = step(1.0 - starredShare, pick) * (1.0 - step(1.0 - brightShare, pick)) * DIM_STAR_GLOW;",
            "const float EDGE_STARS = " + VoidstoneFieldModel.EDGE_STARS + ";");

    /** The edge's star share, as void_stars.glsl holds it. */
    static final double EDGE_STARS = 0.016;
    private static final int TEXELS = 16;
    private static final int HALF_BLOCK_TEXELS = 8;
    private static final double WRAP_BAND = 40.0;
    private static final int NEBULA_TEXELS = 40;
    private static final double NEBULA_DRIFT = 0.004;
    private static final double DRIFT_SOUTH = 0.6;
    private static final int NEBULA_SEED = 11;
    private static final int RATE_SEED = 7;
    private static final int BRIGHTNESS_SEED = 3;
    private static final int SEEDS_PER_PLANE = 3;
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
     * @param sparse  whether the face draws sparse stars
     * @param ticks   the clock
     * @return the look
     */
    static Look roomFace(double x, double z, int floorY, double cameraX, double cameraZ, Matrix4fc room,
            boolean sparse, double ticks) {
        return fragment(new Place(x - cameraX, z - cameraZ, floorY, cameraX, cameraZ), room, sparse, ticks);
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
     * @param sparse  whether the face draws sparse stars
     * @param ticks   the clock
     * @return the look
     */
    static Look copyFace(double x, double z, int floorY, RoomCopies.Offset offset, double cameraX, double cameraZ,
            Matrix4fc room, boolean sparse, double ticks) {
        return fragment(new Place(x - offset.east() - cameraX, z - offset.south() - cameraZ, floorY, cameraX,
                cameraZ), room, sparse, ticks);
    }

    /** Where the vertex shader hands the fragment a top face's point: its offset from the camera. */
    private record Place(double fromCameraX, double fromCameraZ, int floorY, double cameraX, double cameraZ) {
    }

    private static Look fragment(Place place, Matrix4fc room, boolean sparse, double ticks) {
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
        double starShare = sparse ? EDGE_STARS / (BRIGHT_STARS + DIM_STARS) : 1.0;
        double extent = room.m10();
        double width = 2.0 * extent * TEXELS;
        double weightX = 0;
        double weightZ = 0;
        boolean drawn = true;
        if (extent > 0) {
            double toFarX = toFarPlane(roomCenter(room.m00(), room.m01()), extent, acrossX, withinX);
            double toFarZ = toFarPlane(roomCenter(room.m02(), room.m03()), extent, acrossZ, withinZ);
            drawn = toFarX >= 0 && toFarZ >= 0 && toFarX <= width && toFarZ <= width;
            double band = Math.min(WRAP_BAND, extent * TEXELS);
            weightX = 1.0 - smoothstep(0.0, band, toFarX);
            weightZ = 1.0 - smoothstep(0.0, band, toFarZ);
        }
        int backX = texelBack(acrossX, withinX, width);
        int backZ = texelBack(acrossZ, withinZ, width);
        double withinBackX = withinBack(withinX, width);
        double withinBackZ = withinBack(withinZ, width);
        Look here = fieldAt(acrossX, acrossZ, withinX, withinZ, seed, starShare, ticks);
        Look behindX = fieldAt(backX, acrossZ, withinBackX, withinZ, seed, starShare, ticks);
        Look behindZ = fieldAt(acrossX, backZ, withinX, withinBackZ, seed, starShare, ticks);
        Look behindBoth = fieldAt(backX, backZ, withinBackX, withinBackZ, seed, starShare, ticks);
        return new Look(
                mix(mix(here.glow(), behindX.glow(), weightX), mix(behindZ.glow(), behindBoth.glow(), weightX),
                        weightZ),
                mix(mix(here.star(), behindX.star(), weightX), mix(behindZ.star(), behindBoth.star(), weightX),
                        weightZ),
                drawn);
    }

    private static int roomCenter(float high, float low) {
        return (int) high * VoidstoneRoom.CENTER_SPLIT + (int) low;
    }

    private static double toFarPlane(int center, double extent, int texel, double within) {
        return (center * TEXELS + HALF_BLOCK_TEXELS - texel) + extent * TEXELS - within;
    }

    private static int texelBack(int texel, double within, double width) {
        double moved = within - fract(width);
        return texel - (int) Math.floor(width) + (int) Math.floor(moved);
    }

    private static double withinBack(double within, double width) {
        double moved = within - fract(width);
        return moved - Math.floor(moved);
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    /**
     * The field at one texel, unwrapped.
     *
     * @param texelX    the texel's x
     * @param texelZ    the texel's z
     * @param withinX   how far into it along x
     * @param withinZ   how far into it along z
     * @param seed      the face's seed
     * @param starShare the share of the voidstone's stars the face keeps, 1 for full
     * @param ticks     the clock
     * @return the look
     */
    private static Look fieldAt(int texelX, int texelZ, double withinX, double withinZ, int seed, double starShare,
            double ticks) {
        int driftX = (int) (ticks * NEBULA_DRIFT);
        int driftZ = (int) (ticks * NEBULA_DRIFT * DRIFT_SOUTH);
        double glow = smoothstep(GLOW_FROM, 1.0,
                valueNoise(texelX + driftX, texelZ + driftZ, withinX, withinZ, seed + NEBULA_SEED));
        double pick = hash(texelX, texelZ, seed);
        double rate = RATE_FLOOR + RATE_SPAN * hash(texelX, texelZ, seed + RATE_SEED);
        double brightShare = BRIGHT_STARS * starShare;
        double starredShare = (BRIGHT_STARS + DIM_STARS) * starShare;
        double bright = step(1.0 - brightShare, pick)
                * mix(BRIGHT_STAR_FLOOR, BRIGHT_STAR_PEAK, hash(texelX, texelZ, seed + BRIGHTNESS_SEED));
        double dim = step(1.0 - starredShare, pick) * (1.0 - step(1.0 - brightShare, pick)) * DIM_STAR_GLOW;
        double twinkle = TWINKLE_REST + TWINKLE_SWING * Math.sin(ticks * rate + pick * PHASE_TURNS);
        return new Look(glow, (bright + dim) * twinkle, true);
    }

    private static double valueNoise(int texelX, int texelZ, double withinX, double withinZ, int seed) {
        int cellX = Math.floorDiv(texelX, NEBULA_TEXELS);
        int cellZ = Math.floorDiv(texelZ, NEBULA_TEXELS);
        double intoX = fade((texelX - cellX * NEBULA_TEXELS + withinX) / NEBULA_TEXELS);
        double intoZ = fade((texelZ - cellZ * NEBULA_TEXELS + withinZ) / NEBULA_TEXELS);
        double a = hash(cellX, cellZ, seed);
        double b = hash(cellX + 1, cellZ, seed);
        double c = hash(cellX, cellZ + 1, seed);
        double d = hash(cellX + 1, cellZ + 1, seed);
        return mix(mix(a, b, intoX), mix(c, d, intoX), intoZ);
    }

    private static double fade(double into) {
        return into * into * (3.0 - 2.0 * into);
    }

    private static double hash(int x, int y, int z) {
        int mixed = mixBits(x ^ mixBits(y ^ mixBits(z)));
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
