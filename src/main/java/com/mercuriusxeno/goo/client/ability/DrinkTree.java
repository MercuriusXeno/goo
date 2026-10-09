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
 * on its trunk, or to the glove, the ends following the hand, a tributary
 * curving in to land along its trunk's flow and the trunk landing along the
 * pull of the look. About a join the trunk carries every stream whose liquid
 * is there, combined so its area is the sum of theirs, swelling into each
 * over a short length either side of the join and bulging into a node where
 * the stream arrives, so the two meet like metaballs touching. The liquid's
 * pace is set by the goo massing where it flows: a lone block's stream runs
 * at the base pace and a trunk fed by many runs faster by the square root of
 * the goo through it, so a join pulls its tributaries' liquid on; a block's
 * liquid runs its own path then each trunk's remainder, its goo mingling
 * over the whole of it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkTree {

    /** Blocks past a join over which the trunk swells to carry the stream joining it. */
    static final double MERGE = 0.6;
    /** The goo volume of a block whose stream has scale 1, in mB. */
    static final double BASE_VOLUME = 1000;
    /** The power streams combine by: the trunk's radius is this root of the sum of this power of each, their areas summing. */
    static final double COMBINE = 2;
    /** How far a join bulges into a node, as a share of the radius of the stream arriving there. */
    static final double NODE = 0.7;
    /** Ticks a block's goo takes to weigh wholly on the pace of the trunks it feeds, so the pace glides rather than jumps. */
    static final double MASS_RAMP = 10;
    /** Stations along one block of path the liquid's travel time is summed at. */
    static final int STATIONS_PER_BLOCK = DrinkStream.RINGS_PER_BLOCK;
    private static final double HALF = 0.5;

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

        /**
         * @param now the game time, with the partial tick
         * @return how far the block's drain has gone, 0 to 1
         */
        public double progressAt(double now) {
            return Math.clamp((now - start) / Math.max(1, end - start), 0, 1);
        }

        /**
         * @param now the game time, with the partial tick
         * @return the blocks of goo the block weighs on the flow now: its volume over {@link #BASE_VOLUME}, ramping
         *         in over {@link #MASS_RAMP} from its start
         */
        public double massAt(double now) {
            return scale * scale * DrinkStream.smoothRamp(0, MASS_RAMP, now - start);
        }
    }

    /**
     * One stream of the tree: a block's own path and the trunk it feeds, with
     * the pace of its liquid along its route this frame.
     */
    public static final class Stream {
        private final Block block;
        private final DrinkStream.Path path;
        private final @Nullable Stream trunk;
        private final double joinShare;
        private final double now;
        private final List<Stream> tributaries = new ArrayList<>();
        private double @Nullable [] times;
        private double head = Double.NaN;
        private double tail = Double.NaN;
        private double arriving = Double.NaN;

        private Stream(Block block, DrinkStream.Path path, @Nullable Stream trunk, double joinShare, double now) {
            this.block = block;
            this.path = path;
            this.trunk = trunk;
            this.joinShare = joinShare;
            this.now = now;
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
         * @return the game time the tree was built for, with the partial tick
         */
        public double now() {
            return now;
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

        /**
         * @return the blocks of goo this stream and every stream feeding it weigh now
         */
        double subtreeMass() {
            double mass = block.massAt(now);
            for (Stream tributary : tributaries) {
                mass += tributary.subtreeMass();
            }
            return mass;
        }

        /**
         * @param share a share of this stream's path
         * @return the blocks of goo flowing through there: its own and, swelling in over the merge about each
         *         join, every stream feeding it
         */
        public double massAt(double share) {
            double mass = block.massAt(now);
            for (Stream tributary : tributaries) {
                mass += tributary.subtreeMass() * mergeRamp((share - tributary.joinShare) * path.length());
            }
            return mass;
        }

        /**
         * @param share a share of this stream's path
         * @return blocks a tick the liquid flows there: the base pace times the square root of the goo through
         *         it, never slower than the base pace
         */
        public double speedAt(double share) {
            return DrinkStream.FLOW * Math.sqrt(Math.max(1, massAt(share)));
        }

        /**
         * @param distance blocks along the stream's route from its block's far side
         * @return the ticks the liquid takes from the far side to there, down every trunk
         */
        public double timeTo(double distance) {
            double length = path.length();
            if (distance <= length || trunk == null) {
                return ownTimeTo(distance);
            }
            double joinDistance = joinShare * trunk.path.length();
            return ownTimeTo(length) + trunk.timeTo(joinDistance + distance - length) - trunk.timeTo(joinDistance);
        }

        /**
         * @param time ticks of travel from the block's far side
         * @return blocks along the stream's route the liquid reaches in that time, down every trunk
         */
        public double distanceAt(double time) {
            double[] table = times();
            double whole = table[table.length - 1];
            if (time <= whole || trunk == null) {
                return ownDistanceAt(time);
            }
            double joinDistance = joinShare * trunk.path.length();
            return path.length() + trunk.distanceAt(trunk.timeTo(joinDistance) + time - whole) - joinDistance;
        }

        /**
         * @param distance blocks along the stream's route
         * @return the point's place along the liquid, in blocks of liquid at the base pace, falling as the liquid
         *         flows on
         */
        public double materialAt(double distance) {
            return DrinkStream.FLOW * (timeTo(distance) - now);
        }

        /**
         * @return how far along its route its head has flowed, in blocks: a tip's length out of the block's near
         *         face the first tick, so the block itself never tapers
         */
        public double headAt() {
            if (Double.isNaN(head)) {
                head = distanceAt(timeTo(DrinkStream.BLOCK_SPAN + DrinkStream.TIP) + now - block.start());
            }
            return head;
        }

        /**
         * @return how far along its route its tail has flowed, in blocks: it leaves the block's near face as the
         *         block is drained
         */
        public double tailAt() {
            if (Double.isNaN(tail)) {
                tail = distanceAt(timeTo(DrinkStream.BLOCK_SPAN) + now - block.end());
            }
            return tail;
        }

        /**
         * @return the radius of the stream as it arrives at its end, every stream feeding it combined
         */
        double arrivingRadius() {
            if (Double.isNaN(arriving)) {
                arriving = radiusAt(this, 1);
            }
            return arriving;
        }

        private double[] times() {
            if (times == null) {
                int count = stationsAlong(path.length());
                double step = path.length() / (count - 1);
                times = new double[count];
                for (int index = 1; index < count; index++) {
                    times[index] = times[index - 1] + step / speedAt((index - HALF) / (count - 1));
                }
            }
            return times;
        }

        private double ownTimeTo(double distance) {
            double[] table = times();
            double length = path.length();
            if (distance <= 0) {
                return distance / speedAt(0);
            }
            if (distance >= length) {
                return table[table.length - 1] + (distance - length) / speedAt(1);
            }
            double step = length / (table.length - 1);
            int index = (int) (distance / step);
            return table[index] + (table[index + 1] - table[index]) * (distance - index * step) / step;
        }

        private double ownDistanceAt(double time) {
            double[] table = times();
            double whole = table[table.length - 1];
            if (time <= 0) {
                return time * speedAt(0);
            }
            if (time >= whole) {
                return path.length() + (time - whole) * speedAt(1);
            }
            int index = 0;
            while (table[index + 1] < time) {
                index++;
            }
            double step = path.length() / (table.length - 1);
            return (index + (time - table[index]) / (table[index + 1] - table[index])) * step;
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
     * @param pull   the unit direction the trunk flows as it enters the hand
     * @param now    the game time, with the partial tick
     * @return every stream, the one entering the glove first and each after the stream it joins
     */
    public static List<Stream> build(List<Block> blocks, DrinkLayout layout, Vec3 glove, Vec3 pull, double now) {
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
                    ? new Stream(block, new DrinkStream.Path(node.farSide(), glove, block.seed(), pull), null, 0, now)
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
        return new Stream(block, new DrinkStream.Path(node.farSide(), at, block.seed(), arrival), trunk, join, now);
    }

    /**
     * The rings of a stream's own path from its block's far side to its end,
     * its skeleton in the drink's field, each ring as wide as every stream
     * flowing through it there makes it.
     *
     * @param stream the stream
     * @return the rings
     */
    public static List<DrinkStream.Ring> rings(Stream stream) {
        List<DrinkStream.Ring> rings = new ArrayList<>();
        int count = stationsAlong(stream.path().length());
        for (int index = 0; index < count; index++) {
            rings.add(ring(stream, (double) index / (count - 1)));
        }
        return rings;
    }

    /**
     * @param length a path's length, in blocks
     * @return how many stations the path is read at, {@link #STATIONS_PER_BLOCK} to the block and its two ends
     */
    static int stationsAlong(double length) {
        return Math.max(DrinkStream.FEWEST_RINGS, (int) Math.ceil(length * STATIONS_PER_BLOCK) + 1);
    }

    /**
     * The ring of a stream's own path at a share: its radius combining every
     * stream flowing through it there and the node of every join there, its
     * texture and its goo the path owner's, its pace the mass's.
     *
     * @param stream the stream
     * @param share  the share of its path
     * @return the ring
     */
    public static DrinkStream.Ring ring(Stream stream, double share) {
        double distance = share * stream.path().length();
        return DrinkStream.ring(stream.path(), share, stream.now(), radiusAt(stream, share),
                stream.materialAt(distance), distance / stream.routeLength(), stream.speedAt(share),
                DrinkBody.carryAt(distance));
    }

    /**
     * @param stream the stream
     * @param share  the share of its path
     * @return the radius of the stream there: every stream flowing through it and every join's node, combined
     */
    static double radiusAt(Stream stream, double share) {
        double powers = power(contribution(stream, stream, share));
        for (Stream tributary : stream.tributaries()) {
            powers += powersUnder(tributary, stream, share);
            powers += power(nodeOf(tributary, (share - tributary.joinShare()) * stream.path().length()));
        }
        return Math.pow(powers, 1 / COMBINE);
    }

    /**
     * A stream's part of the drink's field: its skeleton, and its block's box while the block stands.
     *
     * @param stream the stream
     * @return the skeleton
     */
    public static DrinkField.Skeleton skeleton(Stream stream) {
        Block block = stream.block();
        double progress = block.progressAt(stream.now());
        DrinkBody.Box box = progress < 1 ? DrinkBody.boxAt(block.center(), progress) : null;
        return new DrinkField.Skeleton(stream, rings(stream), box);
    }

    private static double powersUnder(Stream branch, Stream through, double share) {
        double powers = power(contribution(branch, through, share));
        for (Stream tributary : branch.tributaries()) {
            powers += powersUnder(tributary, through, share);
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
     * its matter's width there, the block's blob where it stood narrowing
     * down the funnel to the stream's, tapering at its ends; on a trunk it joins,
     * swelling in symmetrically about the join over {@link #MERGE} each way,
     * so the two meet like metaballs touching.
     *
     * @param stream  the stream
     * @param through its own path or a trunk it flows through
     * @param share   a share of that path
     * @return the radius, 0 where its liquid is not there now
     */
    static double contribution(Stream stream, Stream through, double share) {
        Entry entry = entryOf(stream, through, share);
        if (entry == null) {
            return 0;
        }
        Block block = stream.block();
        double now = stream.now();
        double width = DrinkBody.widthAt(entry.distance(), block.progressAt(now), block.scale()
                * DrinkStream.widthAt(stream.materialAt(entry.distance()), block.seed()), block.seed(), now);
        double radius = width * DrinkStream.taperAt(entry.distance(), stream.tailAt(), stream.headAt());
        return stream == through ? radius : radius * mergeRamp(entry.pastJoin());
    }

    /**
     * The node a join bulges into: a bell about the join, {@link #MERGE} each
     * way, as big as {@link #NODE} of the radius the stream arrives with, so
     * the more goo joins the fatter the node.
     *
     * @param tributary the stream joining
     * @param pastJoin  blocks past its join along the trunk, below zero before it
     * @return the node's radius there, 0 outside the bell
     */
    static double nodeOf(Stream tributary, double pastJoin) {
        double t = pastJoin / MERGE;
        if (Math.abs(t) >= 1) {
            return 0;
        }
        double bell = (1 - t * t) * (1 - t * t);
        return NODE * tributary.arrivingRadius() * bell;
    }

    /**
     * @param pastJoin blocks past a join along the trunk, below zero before it
     * @return how far the trunk has swelled to carry the stream that joins, 0 a merge before the join to 1 a merge past
     */
    static double mergeRamp(double pastJoin) {
        return DrinkStream.smoothRamp(-MERGE, MERGE, pastJoin);
    }
}
