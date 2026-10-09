package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a block's own cube turns into its stream under an Unmake drink: over
 * the drain every point of the cube moves from where it stands to its place
 * in the stream's body over the block's own span of the way, the face toward
 * the glove first and the far side last, the order jittered by a smooth
 * noise so the turn is gloopy, until nothing cube-shaped is left and what
 * stood there is the stream's tail. Nothing is cut away; the mesh is the
 * block's own faces bent into the stream.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkMorph {

    /** The share of the drain a point takes to turn once its turn comes. */
    static final double TURN = 0.35;
    /** How far the noise moves a point's turn earlier or later, as a share of the drain. */
    static final double GLOOP = 0.1;
    /** Noise cells across the block for the gloop. */
    private static final double GLOOP_SCALE = 3;
    private static final double HALF = 0.5;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final long GLOOP_SALT = 0x6A09_E667L;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkMorph() {
    }

    /**
     * Where a point of the block stands and faces as it turns.
     *
     * @param point  the point, in the world
     * @param normal the unit normal of the surface there
     */
    public record Place(Vec3 point, Vec3 normal) {
    }

    /**
     * The way a block's stream runs: from the block's far side, half its span
     * behind its middle away from the glove, through its middle to the glove.
     *
     * @param center the block's middle
     * @param glove  the glove
     * @param seed   the block's seed
     * @return the way
     */
    public static DrinkStream.Path pathOf(Vec3 center, Vec3 glove, long seed) {
        Vec3 along = glove.subtract(center);
        along = along.lengthSqr() > 0 ? along.normalize() : UP;
        return new DrinkStream.Path(center.subtract(along.scale(DrinkStream.BLOCK_SPAN * HALF)), glove, seed);
    }

    /**
     * How deep behind the face toward the glove a point of the block lies.
     *
     * @param point the point, in the world
     * @param path  the block's way
     * @return 0 at the near face, 1 at the far side
     */
    public static double depthOf(Vec3 point, DrinkStream.Path path) {
        double along = point.subtract(path.from()).dot(path.to().subtract(path.from()).normalize());
        return 1 - Math.clamp(along, 0, DrinkStream.BLOCK_SPAN) / DrinkStream.BLOCK_SPAN;
    }

    /**
     * How far a point of the block has turned into the stream: its turn comes
     * by its depth, the near face's first, put off up to {@link #GLOOP} of
     * the drain by the noise, and takes {@link #TURN} of the drain, so the
     * deepest point is wholly turned as the drain ends.
     *
     * @param local    the point, block-local, for the noise
     * @param depth    how deep behind the near face it lies, 0 to 1
     * @param progress how far the drain has gone, 0 to 1
     * @param seed     the block's seed
     * @return how far it has turned, 0 still the block to 1 wholly the stream
     */
    public static double turned(Vec3 local, double depth, double progress, long seed) {
        double due = depth * (1 - TURN - GLOOP) + GLOOP * MeltMeshNoise.smooth(local.x * GLOOP_SCALE,
                local.y * GLOOP_SCALE, local.z * GLOOP_SCALE, seed + GLOOP_SALT);
        double t = Math.clamp((progress - due) / TURN, 0, 1);
        return t * t * (THREE - TWO * t);
    }

    /**
     * Where a point of the block stands as it turns: on the way from where it
     * stood to its place on the stream's skin at the same distance along the
     * way and the same angle about it.
     *
     * @param point  the point, in the world
     * @param normal the unit normal of the block's face there
     * @param path   the block's way
     * @param span   how much of the way the stream covers
     * @param turned how far the point has turned, 0 to 1
     * @param now    the game time, with the partial tick
     * @return where it stands and faces
     */
    public static Place placeOf(Vec3 point, Vec3 normal, DrinkStream.Path path, DrinkStream.Span span, double turned,
                                double now) {
        Vec3 along = path.to().subtract(path.from()).normalize();
        Vec3 offset = point.subtract(path.from());
        double distance = offset.dot(along);
        double share = Math.clamp(distance, 0, DrinkStream.BLOCK_SPAN) / path.length();
        DrinkStream.Ring ring = DrinkStream.ring(path, share, span, now);
        Vec3 radial = offset.subtract(along.scale(distance));
        double angle = radial.lengthSqr() > 0 ? Math.atan2(radial.dot(ring.across()), radial.dot(ring.side())) : 0;
        Vec3 out = ring.outAt(angle);
        Vec3 target = ring.center().add(out.scale(ring.radius()));
        Vec3 facing = normal.lerp(out, turned);
        return new Place(point.lerp(target, turned), facing.lengthSqr() > 0 ? facing.normalize() : out);
    }
}
