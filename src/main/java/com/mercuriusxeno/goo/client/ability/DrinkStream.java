package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The shape of one stream of an Unmake drink along one path of its tree: a
 * goopy stream flowing languidly out of the block's lump at its entry, from
 * its far side through where the block stood to its join or the glove, snaking off the straight line as a
 * smooth noise field bends it, the bends carried slowly downstream and
 * drifting with time so the snake never repeats; its width a slow profile of
 * elongated bulbs and hourglass waists with gentle grades between them, the
 * bulb clamped at a few times the waist, scaled by the square root of its
 * block's goo volume since transmutation is compressive, the profile riding
 * the flow without jogging the centreline; its head runs out along its route
 * at the pace the tree's mass there sets, and once the block is drained its
 * tail follows the rest, the stream tapering to a point at both ends.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkStream {

    /** Blocks the liquid flows a tick where a lone block of goo flows: four blocks a second, and faster where more goo masses. */
    public static final double FLOW = 0.2;
    /** Blocks of the way the block's own matter spans, from its far side through its middle to its near face. */
    public static final double BLOCK_SPAN = 1;
    /** Blocks past the cone's reach a route to the glove can run, the glove hanging off the eye and a tributary going round. */
    static final double GLOVE_SLACK = 4;
    /** The ticks the longest route's travel takes at the slowest pace; a drink is kept this long past its last block's drain. */
    public static final int LONGEST_TRAVEL_TICKS = (int) Math.ceil((SiphonRule.RANGE + GLOVE_SLACK) / FLOW);
    /** Rings along one block of stream in the field's skeleton: the capsules between them follow the snake's bends. */
    public static final int RINGS_PER_BLOCK = 5;
    /** The fewest rings a path has, its two ends. */
    public static final int FEWEST_RINGS = 2;
    /** The radius at a waist of a stream of scale 1, in blocks, over a cell of the surface's grid so it reads round. */
    static final double WAIST = 0.1;
    /** The radius at a bulb of a stream of scale 1, in blocks, twice the waist. */
    static final double BULB = 0.2;
    /** Blocks of liquid from one bulb or waist to the next, about, so the stream undulates along its length. */
    static final double FEATURE_SPACING = 1.2;
    /** The share of the width profile's field under which the stream sits at its waist. */
    static final double WAIST_EDGE = 0.1;
    /** The share of the width profile's field over which the stream sits at its bulb. */
    static final double BULB_EDGE = 0.9;
    /** How far the stream snakes off the straight line at most, in blocks. */
    static final double SNAKE = 0.45;
    /** Noise cells along one block of stream for its bends. */
    static final double SNAKE_SCALE = 0.6;
    /** The share of the liquid's speed the bends are carried downstream at. */
    static final double BEND_CARRY = 0.35;
    /** How fast the bends drift of their own, in noise cells a tick. */
    static final double DRIFT = 0.01;
    /** Blocks over which an end of the stream tapers to its point. */
    static final double TIP = 0.6;
    /** Blocks before its end over which a path curves to land along its arrival: its trunk's flow, or the look. */
    static final double ARRIVAL_REACH = 1.5;
    /** Blocks of texture laid along one block of skin, mirrored each block so it has no seam. */
    private static final double TEXTURE_PER_BLOCK = 1;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final double HALF = 0.5;
    private static final double TANGENT_STEP = 1e-3;
    private static final double BEND_SEED_Y = 11.3;
    private static final double BEND_SEED_Z = 29.7;
    private static final long ACROSS_SALT = 0x51ED_270BL;
    private static final long WIDTH_SALT = 0x2545_F491L;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private DrinkStream() {
    }

    /**
     * One path of a drink's tree.
     *
     * @param from    the block's far side, where the path starts
     * @param to      where it ends: its join on the trunk it feeds, or the glove
     * @param seed    the block's seed, so its snake and its width are its own
     * @param arrival the unit direction the path arrives along, the trunk's flow at the join, or null to run straight
     */
    public record Path(Vec3 from, Vec3 to, long seed, @Nullable Vec3 arrival) {

        /**
         * A straight path.
         *
         * @param from the block's far side
         * @param to   the glove
         * @param seed the block's seed
         */
        public Path(Vec3 from, Vec3 to, long seed) {
            this(from, to, seed, null);
        }

        /**
         * @return the length of the path, in blocks, as the crow flies
         */
        public double length() {
            return to.distanceTo(from);
        }

        /**
         * @param share the share of the path
         * @return the point of its spine there: the straight line, or the curve that leaves the far side straight
         *         and lands along its arrival
         */
        Vec3 spineAt(double share) {
            Vec3 line = to.subtract(from);
            if (arrival == null) {
                return from.add(line.scale(share));
            }
            Vec3 leaving = from.add(line.scale(1 / THREE));
            Vec3 landing = to.subtract(arrival.scale(Math.min(ARRIVAL_REACH, length() * HALF)));
            double rest = 1 - share;
            return from.scale(rest * rest * rest).add(leaving.scale(THREE * rest * rest * share))
                    .add(landing.scale(THREE * rest * share * share)).add(to.scale(share * share * share));
        }

        /**
         * @param point a point
         * @return the share of the straight line nearest the point, 0 to 1
         */
        public double nearestShare(Vec3 point) {
            Vec3 line = to.subtract(from);
            return line.lengthSqr() == 0 ? 0 : Math.clamp(point.subtract(from).dot(line) / line.lengthSqr(), 0, 1);
        }
    }

    /**
     * One ring of a stream's skeleton: a point of its middle with the radius of
     * its liquid there, the surface being meshed from the field about these.
     *
     * @param center   the ring's middle
     * @param flow     the unit direction the liquid flows there
     * @param radius   the liquid's radius there, in blocks
     * @param material how far along the path's own liquid the ring is, in blocks of liquid at the slowest pace
     * @param share    the share of the path's owner's whole route to the glove the ring stands at
     * @param speed    blocks a tick the liquid flows there
     */
    public record Ring(Vec3 center, Vec3 flow, double radius, double material, double share, double speed) {
    }

    /**
     * @param streaming the block
     * @param now       the game time, with the partial tick
     * @return whether its stream has had the longest route's travel since its drain, so none of it can be in the air
     */
    public static boolean gone(DrinkPayload.Streaming streaming, double now) {
        return now >= streaming.end() + LONGEST_TRAVEL_TICKS;
    }

    /**
     * The ring of a stream's skeleton at a share of a path.
     *
     * @param path     the path
     * @param share    the share of the path
     * @param now      the game time, with the partial tick
     * @param radius   the liquid's radius there, in blocks
     * @param material how far along the path's own liquid the ring is
     * @param route    the share of the path owner's whole route the ring stands at
     * @param speed    blocks a tick the liquid flows there
     * @return the ring
     */
    public static Ring ring(Path path, double share, double now, double radius, double material, double route,
                            double speed) {
        return new Ring(pointAt(path, share, now), flowAt(path, share, now), radius, material, route, speed);
    }

    /**
     * @param path the path
     * @param share the share of the path
     * @param now  the game time, with the partial tick
     * @return the unit direction the stream flows there
     */
    public static Vec3 flowAt(Path path, double share, double now) {
        Vec3 tangent = pointAt(path, share + TANGENT_STEP, now).subtract(pointAt(path, share - TANGENT_STEP, now));
        return tangent.lengthSqr() > 0 ? tangent.normalize() : path.to().subtract(path.from()).normalize();
    }

    /**
     * Where the stream's middle runs: the path's spine, bent sideways by a
     * smooth noise field read along the stream, most in the middle and not at
     * all at either end.
     *
     * @param path  the path
     * @param share the share of the path
     * @param now   the game time, with the partial tick
     * @return the point of the stream's middle there
     */
    public static Vec3 pointAt(Path path, double share, double now) {
        Vec3 line = path.to().subtract(path.from());
        double length = line.length();
        if (length == 0) {
            return path.from();
        }
        Vec3 along = line.normalize();
        Vec3 side = along.cross(UP);
        side = side.lengthSqr() > 0 ? side.normalize() : EAST;
        Vec3 across = along.cross(side);
        double bendAt = (share * length - now * FLOW * BEND_CARRY) * SNAKE_SCALE;
        double drift = now * DRIFT;
        double bendSide = MeltMeshNoise.smooth(bendAt, drift, BEND_SEED_Y, path.seed()) - HALF;
        double bendAcross = MeltMeshNoise.smooth(bendAt, drift, BEND_SEED_Z, path.seed() + ACROSS_SALT) - HALF;
        double reach = TWO * SNAKE * Math.sin(Math.PI * share);
        return path.spineAt(share).add(side.scale(bendSide * reach)).add(across.scale(bendAcross * reach));
    }

    /**
     * The radius one stream alone has at a point of its route: its width
     * profile along its liquid, scaled, tapering to a point at its tail and
     * its head, nothing where its liquid is not.
     *
     * @param scale    the stream's scale, 1 for a block of 1000 mB
     * @param material the point's place along the liquid
     * @param distance the point's distance along the route, in blocks
     * @param tail     how far along the route the tail has flowed
     * @param head     how far along the route the head has flowed
     * @param seed     the block's seed
     * @return the radius there, in blocks
     */
    public static double radiusAt(double scale, double material, double distance, double tail, double head, long seed) {
        return scale * widthAt(material, seed) * taperAt(distance, tail, head);
    }

    /**
     * @param distance a point's distance along the route, in blocks
     * @param tail     how far along the route the tail has flowed
     * @param head     how far along the route the head has flowed
     * @return how much of its width the stream has there, 1 between its ends, tapering to 0 at each over {@link #TIP}
     */
    public static double taperAt(double distance, double tail, double head) {
        double tailTaper = Math.clamp((distance - tail) / TIP, 0, 1);
        double headTaper = Math.clamp((head - distance) / TIP, 0, 1);
        return Math.sqrt(tailTaper) * Math.sqrt(headTaper);
    }

    /**
     * The radius along the liquid of a stream of scale 1 before any taper:
     * the waist, the bulb, or a gentle grade between them where the slow
     * field crosses over.
     *
     * @param material the point's place along the liquid
     * @param seed     the block's seed
     * @return the radius there, in blocks
     */
    static double widthAt(double material, long seed) {
        double field = MeltMeshNoise.smooth(material / FEATURE_SPACING, 0, 0, seed + WIDTH_SALT);
        return WAIST + (BULB - WAIST) * smoothRamp(WAIST_EDGE, BULB_EDGE, field);
    }

    /**
     * @param edge0 where the ramp starts
     * @param edge1 where it ends
     * @param x     the input
     * @return the smooth ramp from 0 at edge0 to 1 at edge1, flat at both
     */
    static double smoothRamp(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (THREE - TWO * t);
    }

    /**
     * Where along the texture a point of the liquid is: the texture is laid
     * along the liquid and mirrored every block, so it rides the flow and
     * has no seam.
     *
     * @param material the point's place along the liquid, in blocks
     * @return the texture's share, 0 to 1
     */
    public static float textureU(double material) {
        return mirrored(material * TEXTURE_PER_BLOCK / TWO);
    }

    /**
     * Where on the texture a point of the skin is along one world axis: the
     * texture laid over the world at its own size, a block of texture to a
     * block of skin, mirrored every block so it tiles with no seam, and slid
     * against the flow so it rides the liquid; it never stretches, however
     * the surface bends.
     *
     * @param along the point's coordinate along the axis, in blocks, already slid with the flow
     * @return the texture's share along that axis, 0 to 1
     */
    public static float textureAt(double along) {
        return mirrored(along * TEXTURE_PER_BLOCK / TWO);
    }

    /**
     * @param along a place along a texture laid end to end, in textures, one texture mirrored every other
     * @return the texture's share, 0 to 1, continuous through every mirror
     */
    private static float mirrored(double along) {
        double wrapped = along - Math.floor(along);
        return (float) (1 - Math.abs(TWO * wrapped - 1));
    }
}
