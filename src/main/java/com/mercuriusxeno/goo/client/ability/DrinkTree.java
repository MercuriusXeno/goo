package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The tree an Unmake drink's streams union on: the stream nearest the hand
 * runs into the glove, every other stream joins the nearest stream closer to
 * the hand, at the point of that trunk nearest its block pushed a little
 * toward the hand so it arrives at a shallow angle, and the joins cascade
 * until one trunk enters the glove. Past a join the trunk carries the area
 * sum of every stream whose liquid is there, swelling into it over a short
 * length so the two meet like metaballs touching; a block's liquid flows at
 * the one pace along its whole route, its own path then each trunk's
 * remainder, its goo mingling over the whole of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkTree {

    /** Blocks toward the hand a join is pushed past the point of the trunk nearest the joining block. */
    static final double LEAD = 1.2;
    /** Blocks past a join over which the trunk swells to carry the stream joining it. */
    static final double MERGE = 0.6;
    /** The goo volume of a block whose stream has scale 1, in mB. */
    static final double BASE_VOLUME = 1000;

    private DrinkTree() {
    }

    /**
     * One block of a drink.
     *
     * @param pos    the block
     * @param center its middle
     * @param scale  its stream's scale, the square root of its goo volume over {@link #BASE_VOLUME}
     * @param start  the game time it started streaming
     * @param end    the game time it is drained
     */
    public record Block(BlockPos pos, Vec3 center, double scale, long start, long end) {

        /**
         * @return the block's seed, so its stream is its own
         */
        public long seed() {
            return pos.asLong();
        }
    }

    /**
     * One stream of the tree: a block's own path and the trunk it feeds.
     */
    public static final class Stream {
        private final Block block;
        private final DrinkStream.Path path;
        private final @Nullable Stream trunk;
        private final double joinShare;
        private final List<Stream> tributaries = new ArrayList<>();

        private Stream(Block block, DrinkStream.Path path, @Nullable Stream trunk, double joinShare) {
            this.block = block;
            this.path = path;
            this.trunk = trunk;
            this.joinShare = joinShare;
            if (trunk != null) {
                trunk.tributaries.add(this);
            }
        }

        /**
         * @return the block the stream is
         */
        public Block block() {
            return block;
        }

        /**
         * @return its own path, from its block's far side to its join or the glove
         */
        public DrinkStream.Path path() {
            return path;
        }

        /**
         * @return the stream it joins, or null for the one entering the glove
         */
        public @Nullable Stream trunk() {
            return trunk;
        }

        /**
         * @return the share of the trunk's path it joins at, 0 for the stream entering the glove
         */
        public double joinShare() {
            return joinShare;
        }

        /**
         * @return the streams joining this one
         */
        public List<Stream> tributaries() {
            return tributaries;
        }

        /**
         * @param share a share of this stream's path
         * @return the blocks of route left from there to the glove, down every trunk
         */
        public double remainingFrom(double share) {
            return (1 - share) * path.length() + (trunk == null ? 0 : trunk.remainingFrom(joinShare));
        }

        /**
         * @return the blocks of the block's whole route to the glove
         */
        public double routeLength() {
            return remainingFrom(0);
        }
    }

    /**
     * @param volume a block's goo volume, in mB
     * @return its stream's scale, so the stream's area is proportional to the volume
     */
    public static double scaleOf(long volume) {
        return Math.sqrt(volume / BASE_VOLUME);
    }

    /**
     * Builds the tree of a drink's blocks this frame.
     *
     * @param blocks the blocks streaming
     * @param glove  the glove
     * @param now    the game time, with the partial tick
     * @return every stream, the one entering the glove first
     */
    public static List<Stream> build(List<Block> blocks, Vec3 glove, double now) {
        List<Block> nearestFirst = new ArrayList<>(blocks);
        nearestFirst.sort(Comparator.comparingDouble(block -> block.center().distanceToSqr(glove)));
        List<Stream> streams = new ArrayList<>();
        for (Block block : nearestFirst) {
            Stream trunk = nearestTrunk(streams, block.center());
            if (trunk == null) {
                streams.add(new Stream(block, DrinkMorph.pathOf(block.center(), glove, block.seed()), null, 0));
            } else {
                double join = joinShare(trunk, block.center());
                Vec3 at = DrinkStream.pointAt(trunk.path(), join, now);
                streams.add(new Stream(block, DrinkMorph.pathOf(block.center(), at, block.seed()), trunk, join));
            }
        }
        return streams;
    }

    /**
     * @param streams the streams standing, all closer to the hand
     * @param center  a block's middle
     * @return the stream whose straight line runs nearest the block, or null where none stands
     */
    static @Nullable Stream nearestTrunk(List<Stream> streams, Vec3 center) {
        Stream nearest = null;
        double least = Double.MAX_VALUE;
        for (Stream stream : streams) {
            double distance = stream.path().lineAt(stream.path().nearestShare(center)).distanceToSqr(center);
            if (distance < least) {
                least = distance;
                nearest = stream;
            }
        }
        return nearest;
    }

    /**
     * @param trunk  the stream a block joins
     * @param center the block's middle
     * @return the share of the trunk's path it joins at: the point nearest it, pushed {@link #LEAD} toward the hand
     */
    static double joinShare(Stream trunk, Vec3 center) {
        return Math.min(1, trunk.path().nearestShare(center) + LEAD / trunk.path().length());
    }

    /**
     * The rings of a stream's own path from a share to its end, each as wide
     * as every stream flowing through it there makes it.
     *
     * @param stream the stream
     * @param lowest the share of its path below which its block's own matter draws it, 0 for none
     * @param now    the game time, with the partial tick
     * @return the rings
     */
    public static List<DrinkStream.Ring> rings(Stream stream, double lowest, double now) {
        List<DrinkStream.Ring> rings = new ArrayList<>();
        int count = Math.max(DrinkStream.FEWEST_RINGS,
                (int) Math.ceil(stream.path().length() * DrinkStream.RINGS_PER_BLOCK * (1 - lowest)) + 1);
        for (int index = 0; index < count; index++) {
            rings.add(ring(stream, lowest + (1 - lowest) * index / (count - 1), now));
        }
        return rings;
    }

    /**
     * The ring of a stream's own path at a share: its radius the area sum of
     * every stream flowing through it there, its texture and its goo the
     * path owner's.
     *
     * @param stream the stream
     * @param share  the share of its path
     * @param now    the game time, with the partial tick
     * @return the ring
     */
    public static DrinkStream.Ring ring(Stream stream, double share, double now) {
        double distance = share * stream.path().length();
        double squares = square(contribution(stream, stream, share, now));
        for (Stream tributary : stream.tributaries()) {
            squares += squaresUnder(tributary, stream, share, now);
        }
        return DrinkStream.ring(stream.path(), share, now, Math.sqrt(squares), DrinkStream.materialAt(distance, now),
                distance / stream.routeLength());
    }

    private static double squaresUnder(Stream branch, Stream through, double share, double now) {
        double squares = square(contribution(branch, through, share, now));
        for (Stream tributary : branch.tributaries()) {
            squares += squaresUnder(tributary, through, share, now);
        }
        return squares;
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * Where a stream's liquid stands at a point of a trunk it flows through.
     *
     * @param distance the point's distance along the stream's own route, in blocks
     * @param join     the share of the trunk the stream's branch joined it at
     */
    private record Entry(double distance, double join) {
    }

    /**
     * @param stream  a stream
     * @param through its own path or a trunk it flows through
     * @param share   a share of that path
     * @return where the stream's liquid stands there, or null where the stream has not yet joined
     */
    private static @Nullable Entry entryOf(Stream stream, Stream through, double share) {
        if (stream == through) {
            return new Entry(share * through.path().length(), 0);
        }
        double distance = stream.path().length();
        Stream branch = stream;
        for (Stream next = branch.trunk(); next != through; next = branch.trunk()) {
            if (next == null) {
                return null;
            }
            distance += (1 - branch.joinShare()) * next.path().length();
            branch = next;
        }
        if (share < branch.joinShare()) {
            return null;
        }
        return new Entry(distance + (share - branch.joinShare()) * through.path().length(), branch.joinShare());
    }

    /**
     * The radius one stream alone gives a point of a path it flows through:
     * its own radius there, and on a trunk it joined, swelling in over
     * {@link #MERGE} past the join.
     *
     * @param stream  the stream
     * @param through its own path or a trunk it flows through
     * @param share   a share of that path
     * @param now     the game time, with the partial tick
     * @return the radius, 0 where its liquid is not there now
     */
    static double contribution(Stream stream, Stream through, double share, double now) {
        Entry entry = entryOf(stream, through, share);
        if (entry == null) {
            return 0;
        }
        Block block = stream.block();
        double radius = DrinkStream.radiusAt(block.scale(), DrinkStream.materialAt(entry.distance(), now),
                entry.distance(), DrinkStream.tailAt(block.end(), now), DrinkStream.headAt(block.start(), now),
                block.seed());
        return stream == through ? radius : radius * mergeRamp((share - entry.join()) * through.path().length());
    }

    /**
     * @param pastJoin blocks past a join along the trunk
     * @return how far the trunk has swelled to carry the stream that joined, 0 at the join to 1 past {@link #MERGE}
     */
    static double mergeRamp(double pastJoin) {
        return DrinkStream.smoothRamp(0, MERGE, pastJoin);
    }
}
