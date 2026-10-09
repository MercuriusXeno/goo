package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tree an Unmake drink's streams union on this frame, built on the
 * drink's fixed layout: each block's path runs from its far side to its join
 * on its trunk, or to the glove, the ends following the hand and a tributary
 * curving in to land along its trunk's flow. About a join the trunk carries
 * every stream whose liquid is there, combined by the fourth root of the sum
 * of fourth powers so a trunk of many streams is fatter but dampened,
 * swelling into each over a short length either side of the join so the two
 * meet like metaballs touching; a block's liquid flows at the one pace along
 * its whole route, its own path then each trunk's remainder, its goo
 * mingling over the whole of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkTree {

    /** Blocks past a join over which the trunk swells to carry the stream joining it. */
    static final double MERGE = 0.6;
    /** The goo volume of a block whose stream has scale 1, in mB. */
    static final double BASE_VOLUME = 1000;
    /** The power streams combine by: the trunk's radius is this root of the sum of this power of each. */
    static final double COMBINE = 4;

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
     * Builds the tree of a drink's blocks this frame on its layout.
     *
     * @param blocks the blocks streaming
     * @param layout the drink's layout, every block laid
     * @param glove  the glove
     * @param now    the game time, with the partial tick
     * @return every stream, the one entering the glove first and each after the stream it joins
     */
    public static List<Stream> build(List<Block> blocks, DrinkLayout layout, Vec3 glove, double now) {
        Map<BlockPos, Block> byPos = new HashMap<>();
        blocks.forEach(block -> byPos.put(block.pos(), block));
        Map<BlockPos, Stream> built = new HashMap<>();
        List<Stream> streams = new ArrayList<>();
        for (DrinkLayout.Node node : layout.nodes()) {
            Block block = byPos.get(node.pos());
            Stream trunk = node.trunk() == null ? null : built.get(node.trunk());
            if (block == null) {
                continue;
            }
            Stream stream = trunk == null
                    ? new Stream(block, new DrinkStream.Path(node.farSide(), glove, block.seed()), null, 0)
                    : joining(block, node, trunk, now);
            built.put(block.pos(), stream);
            streams.add(stream);
        }
        return streams;
    }

    private static Stream joining(Block block, DrinkLayout.Node node, Stream trunk, double now) {
        double join = Math.min(1, node.joinAt() / trunk.path().length());
        Vec3 at = DrinkStream.pointAt(trunk.path(), join, now);
        Vec3 arrival = DrinkStream.flowAt(trunk.path(), join, now);
        return new Stream(block, new DrinkStream.Path(node.farSide(), at, block.seed(), arrival), trunk, join);
    }

    /**
     * The rings of a stream's own path from its block's entry to its end,
     * each as wide as every stream flowing through it there makes it.
     *
     * @param stream the stream
     * @param now    the game time, with the partial tick
     * @return the rings
     */
    public static List<DrinkStream.Ring> rings(Stream stream, double now) {
        List<DrinkStream.Ring> rings = new ArrayList<>();
        double lowest = Math.min(1, DrinkStream.BLOCK_SPAN / stream.path().length());
        int count = Math.max(DrinkStream.FEWEST_RINGS,
                (int) Math.ceil(stream.path().length() * DrinkStream.RINGS_PER_BLOCK * (1 - lowest)) + 1);
        for (int index = 0; index < count; index++) {
            rings.add(ring(stream, lowest + (1 - lowest) * index / (count - 1), now));
        }
        return rings;
    }

    /**
     * The ring of a stream's own path at a share: its radius combining every
     * stream flowing through it there, its texture and its goo the path owner's.
     *
     * @param stream the stream
     * @param share  the share of its path
     * @param now    the game time, with the partial tick
     * @return the ring
     */
    public static DrinkStream.Ring ring(Stream stream, double share, double now) {
        double distance = share * stream.path().length();
        double powers = power(contribution(stream, stream, share, now));
        for (Stream tributary : stream.tributaries()) {
            powers += powersUnder(tributary, stream, share, now);
        }
        return DrinkStream.ring(stream.path(), share, now, Math.pow(powers, 1 / COMBINE),
                DrinkBody.roundnessAt(distance), DrinkStream.materialAt(distance, now), distance / stream.routeLength());
    }

    private static double powersUnder(Stream branch, Stream through, double share, double now) {
        double powers = power(contribution(branch, through, share, now));
        for (Stream tributary : branch.tributaries()) {
            powers += powersUnder(tributary, through, share, now);
        }
        return powers;
    }

    private static double power(double radius) {
        return Math.pow(radius, COMBINE);
    }

    /**
     * Where a stream's liquid stands at a point of a trunk it flows through.
     *
     * @param distance the point's distance along the stream's own route, in blocks
     * @param pastJoin blocks past the stream's join along the trunk, below zero before it
     */
    private record Entry(double distance, double pastJoin) {
    }

    /**
     * @param stream  a stream
     * @param through its own path or a trunk it flows through
     * @param share   a share of that path
     * @return where the stream's liquid stands there, or null where the stream is not yet near its join
     */
    private static @Nullable Entry entryOf(Stream stream, Stream through, double share) {
        if (stream == through) {
            return new Entry(share * through.path().length(), Double.MAX_VALUE);
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
        double pastJoin = (share - branch.joinShare()) * through.path().length();
        if (pastJoin < -MERGE) {
            return null;
        }
        return new Entry(distance + Math.max(0, pastJoin), pastJoin);
    }

    /**
     * The radius one stream alone gives a point of a path it flows through:
     * its matter's width there, the block's own at its entry narrowing down
     * the funnel to the stream's, tapering at its ends; on a trunk it joins,
     * swelling in symmetrically about the join over {@link #MERGE} each way,
     * so the two meet like metaballs touching.
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
        double width = DrinkBody.widthAt(entry.distance(), block.scale()
                * DrinkStream.widthAt(DrinkStream.materialAt(entry.distance(), now), block.seed()));
        double radius = width * DrinkStream.taperAt(entry.distance(), DrinkStream.tailAt(block.end(), now),
                DrinkStream.headAt(block.start(), now));
        return stream == through ? radius : radius * mergeRamp(entry.pastJoin());
    }

    /**
     * @param pastJoin blocks past a join along the trunk, below zero before it
     * @return how far the trunk has swelled to carry the stream that joins, 0 a merge before the join to 1 a merge past
     */
    static double mergeRamp(double pastJoin) {
        return DrinkStream.smoothRamp(-MERGE, MERGE, pastJoin);
    }
}
