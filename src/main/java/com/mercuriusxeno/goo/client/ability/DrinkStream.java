package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The shape of one block's stream into an Unmake drink: one thick goopy
 * stream running from the block's face to the glove, snaking off the
 * straight line as a smooth noise field bends it, the bends carried slowly
 * downstream and drifting with time so the snake never repeats; its liquid
 * slides toward the glove, swelling and pinching along its length and
 * lumpy with the same noise; its head runs out from the block until it
 * enters the glove, and once the block is drained its tail leaves the
 * block and follows the rest in, tapering to a point at both ends.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkStream {

    /** Ticks the liquid takes from the block to the glove. */
    public static final int TRAVEL_TICKS = 12;
    /** Rings along one block of stream; enough that the snake reads as one smooth body. */
    public static final int RINGS_PER_BLOCK = 10;
    /** The fewest rings a stream with anything in the air has, its tail and its head. */
    public static final int FEWEST_RINGS = 2;
    /** The stream's radius where it is neither swollen nor pinched, in blocks. */
    static final double RADIUS = 0.13;
    /** How far the swells rise and the pinches fall about the radius, as a share of it. */
    static final double SWELL = 0.4;
    /** Blocks from one swell to the next along the liquid. */
    static final double SWELL_SPACING = 0.6;
    /** How far the noise lumps the radius about its swell, as a share of it. */
    static final double LUMP = 0.3;
    /** Noise cells along one block of liquid for its lumps. */
    static final double LUMP_SCALE = 1.4;
    /** How far the stream snakes off the straight line at most, in blocks. */
    static final double SNAKE = 0.45;
    /** Noise cells along one block of stream for its bends. */
    static final double SNAKE_SCALE = 0.6;
    /** The share of the liquid's speed the bends are carried downstream at. */
    static final double BEND_CARRY = 0.35;
    /** How fast the bends drift of their own, in noise cells a tick. */
    static final double DRIFT = 0.02;
    /** Blocks over which an end of the stream tapers to its point. */
    static final double TIP = 0.6;
    /** How far the stream pinches as it enters the glove, as a share of its radius. */
    static final double GLOVE_PINCH = 0.5;
    /** Blocks of texture laid along one block of liquid, mirrored each block so it has no seam. */
    private static final double TEXTURE_PER_BLOCK = 1;
    private static final double TWO = 2;
    private static final double HALF = 0.5;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double TANGENT_STEP = 1e-3;
    private static final double BEND_SEED_Y = 11.3;
    private static final double BEND_SEED_Z = 29.7;
    private static final long ACROSS_SALT = 0x51ED_270BL;
    private static final long LUMP_SALT = 0x2545_F491L;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private DrinkStream() {
    }

    /**
     * How much of the way to the glove the stream covers now.
     *
     * @param tail the share of the way its tail has reached, 0 while the block still feeds it
     * @param head the share of the way its head has reached, 1 once it enters the glove
     */
    public record Span(double tail, double head) {

        /**
         * @return whether nothing of the stream is in the air
         */
        public boolean isEmpty() {
            return head <= tail;
        }
    }

    /**
     * One ring of the stream's skin.
     *
     * @param center   the ring's middle
     * @param side     a unit vector across the stream
     * @param across   the unit vector across the stream square to {@code side}, with it right-handed about the flow
     * @param radius   the ring's radius, in blocks
     * @param material how far along the liquid the ring is, in blocks, sliding toward the glove
     * @param share    the share of the way to the glove the ring stands at
     */
    public record Ring(Vec3 center, Vec3 side, Vec3 across, double radius, double material, double share) {

        /**
         * @param angle an angle about the ring, in radians
         * @return the unit vector from the ring's middle out to its skin there
         */
        public Vec3 outAt(double angle) {
            return side.scale(Math.cos(angle)).add(across.scale(Math.sin(angle)));
        }
    }

    /**
     * @param streaming the block
     * @param now       the game time, with the partial tick
     * @return how much of the way its stream covers now
     */
    public static Span span(DrinkPayload.Streaming streaming, double now) {
        double head = Math.clamp((now - streaming.start()) / TRAVEL_TICKS, 0, 1);
        double tail = Math.clamp((now - streaming.end()) / TRAVEL_TICKS, 0, 1);
        return new Span(tail, head);
    }

    /**
     * @param streaming the block
     * @param now       the game time, with the partial tick
     * @return whether its stream has wholly entered the glove, so nothing of it is left to draw
     */
    public static boolean gone(DrinkPayload.Streaming streaming, double now) {
        return now >= streaming.end() + TRAVEL_TICKS;
    }

    /**
     * The rings of a stream's skin from its tail to its head, evenly along the way.
     *
     * @param from the point on the block's face the stream leaves
     * @param to   the glove
     * @param span how much of the way the stream covers
     * @param now  the game time, with the partial tick
     * @param seed the block's seed, so its snake is its own
     * @return the rings, none for an empty span
     */
    public static List<Ring> rings(Vec3 from, Vec3 to, Span span, double now, long seed) {
        List<Ring> rings = new ArrayList<>();
        double length = to.distanceTo(from);
        if (span.isEmpty() || length == 0) {
            return rings;
        }
        int count = Math.max(FEWEST_RINGS,
                (int) Math.ceil(length * RINGS_PER_BLOCK * (span.head() - span.tail())) + 1);
        for (int index = 0; index < count; index++) {
            double share = span.tail() + (span.head() - span.tail()) * index / (count - 1);
            rings.add(ringAt(from, to, share, span, now, seed));
        }
        return rings;
    }

    private static Ring ringAt(Vec3 from, Vec3 to, double share, Span span, double now, long seed) {
        double length = to.distanceTo(from);
        Vec3 center = pointAt(from, to, share, now, seed);
        Vec3 tangent = pointAt(from, to, share + TANGENT_STEP, now, seed)
                .subtract(pointAt(from, to, share - TANGENT_STEP, now, seed));
        tangent = tangent.lengthSqr() > 0 ? tangent.normalize() : to.subtract(from).normalize();
        Vec3 side = tangent.cross(UP);
        side = side.lengthSqr() > 0 ? side.normalize() : EAST;
        Vec3 across = tangent.cross(side);
        double material = materialAt(share, length, now);
        return new Ring(center, side, across, radiusAt(share, span, material, length, seed), material, share);
    }

    /**
     * Where the stream's middle runs: the straight line from the face to the
     * glove, bent sideways by a smooth noise field read along the stream,
     * most in the middle and not at all at either end.
     *
     * @param from  the point on the block's face the stream leaves
     * @param to    the glove
     * @param share the share of the way to the glove
     * @param now   the game time, with the partial tick
     * @param seed  the block's seed
     * @return the point of the stream's middle there
     */
    static Vec3 pointAt(Vec3 from, Vec3 to, double share, double now, long seed) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length == 0) {
            return from;
        }
        Vec3 along = line.normalize();
        Vec3 side = along.cross(UP);
        side = side.lengthSqr() > 0 ? side.normalize() : EAST;
        Vec3 across = along.cross(side);
        double bendAt = (share * length - now * length / TRAVEL_TICKS * BEND_CARRY) * SNAKE_SCALE;
        double drift = now * DRIFT;
        double bendSide = MeltMeshNoise.smooth(bendAt, drift, BEND_SEED_Y, seed) - HALF;
        double bendAcross = MeltMeshNoise.smooth(bendAt, drift, BEND_SEED_Z, seed + ACROSS_SALT) - HALF;
        double reach = TWO * SNAKE * Math.sin(Math.PI * share);
        return from.add(along.scale(share * length)).add(side.scale(bendSide * reach)).add(across.scale(bendAcross
                * reach));
    }

    /**
     * How far along the liquid a point of the stream is: the liquid slides
     * toward the glove the length of the way every {@link #TRAVEL_TICKS}.
     *
     * @param share  the share of the way to the glove
     * @param length the length of the way, in blocks
     * @param now    the game time, with the partial tick
     * @return the point's place along the liquid, in blocks, falling as the liquid slides on
     */
    static double materialAt(double share, double length, double now) {
        return share * length - now * length / TRAVEL_TICKS;
    }

    /**
     * The stream's radius at a point: swelling and pinching along the liquid,
     * lumped by noise, pinched as it enters the glove, and tapering to a point
     * at its tail and its head.
     *
     * @param share    the share of the way to the glove
     * @param span     how much of the way the stream covers
     * @param material the point's place along the liquid
     * @param length   the length of the way, in blocks
     * @param seed     the block's seed
     * @return the radius there, in blocks
     */
    static double radiusAt(double share, Span span, double material, double length, long seed) {
        double swell = 1 + SWELL * Math.sin(TWO_PI * material / SWELL_SPACING);
        double lump = 1 + LUMP * (TWO * MeltMeshNoise.smooth(material * LUMP_SCALE, 0, 0, seed + LUMP_SALT) - 1);
        double pinch = 1 - GLOVE_PINCH * share;
        double tailTaper = Math.clamp((share - span.tail()) * length / TIP, 0, 1);
        double headTaper = Math.clamp((span.head() - share) * length / TIP, 0, 1);
        return RADIUS * swell * lump * pinch * taper(tailTaper) * taper(headTaper);
    }

    private static double taper(double t) {
        return Math.sqrt(t);
    }

    /**
     * Where along the texture a point of the liquid is: the texture is laid
     * along the liquid and mirrored every block, so it slides with the
     * liquid and has no seam.
     *
     * @param material the point's place along the liquid, in blocks
     * @return the texture's share, 0 to 1
     */
    public static float textureU(double material) {
        double along = material * TEXTURE_PER_BLOCK / TWO;
        double wrapped = along - Math.floor(along);
        return (float) (1 - Math.abs(TWO * wrapped - 1));
    }
}
