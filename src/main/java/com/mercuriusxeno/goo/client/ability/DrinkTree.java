package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tree an Unmake drink's streams union on this frame, built on the
 * drink's layout: each block's path runs from its far side to its join on its
 * trunk, or to the glove, the ends following the hand and gliding onto a new
 * course when the stream re-roots, a tributary
 * curving in to land along its trunk's flow and the trunk landing along the
 * pull of the look. About a join the trunk carries every stream whose liquid
 * is there, as wide as one of them times the fourth root of how many, each
 * swelling in over a short length either side of its join, so the two meet
 * like metaballs touching and a trunk grows gently however many feed it,
 * and every stream's width falls straight to a thread over the last two
 * fifths of its route, so the trunk enters the palm no wider than a thread.
 * The liquid's
 * pace is set by the goo massing where it flows: a lone block's stream runs
 * at the base pace and a trunk fed by many runs faster by the square root of
 * the goo through it, so a join pulls its tributaries' liquid on; a block's
 * liquid runs its own path then each trunk's remainder. Before a block
 * streams, one square of unstable goo flies from the hand to its near face,
 * the block standing as itself and nothing of its stream showing until the
 * square lands.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkTree {

    /** Blocks past a join over which the trunk swells to carry the stream joining it. */
    static final double MERGE = 0.6;
    /** The goo volume of a block whose stream has scale 1, in mB. */
    static final double BASE_VOLUME = 1000;
    /** The root of the count of streams a trunk's width grows by: the fourth, so a trunk never fattens far. */
    static final double TRUNK_ROOT = 4;
    /** The share of a stream's route past which its width falls, straight, from its full width to the thread at the palm. */
    static final double TAPER_FROM = 0.6;
    /** The radius of the thread the trunk is as it enters the palm, in blocks: the thinnest the skin reads round. */
    static final double THREAD = DrinkStream.THINNEST;
    /** Ticks a block's goo takes to weigh wholly on the pace of the trunks it feeds, so the pace glides rather than jumps. */
    static final double MASS_RAMP = 10;
    /** Stations along one block of path the liquid's travel time is summed at. */
    static final int STATIONS_PER_BLOCK = DrinkStream.RINGS_PER_BLOCK;
    private static final double HALF = 0.5;
    /** The two stations a distance along a path is read between, so the last pair serves the path's very end. */
    private static final int PAIR = 2;

    private DrinkTree() {
    }

    /**
     * One block of a drink.
     *
     * @param pos    the block
     * @param center its middle
     * @param scale  its stream's scale, the square root of its goo volume over {@link #BASE_VOLUME}
     * @param picked the game time it was picked, the square leaving the hand for it
     * @param start  the game time the square lands and it started streaming
     * @param end    the game time it is drained
     */
    public record Block(BlockPos pos, Vec3 center, double scale, long picked, long start, long end) {

        /**
         * @param now the game time, with the partial tick
         * @return whether the square is still on its way to the block, which stands as itself until it lands
         */
        public boolean awaitingAt(double now) {
            return now < start;
        }

        /**
         * @param now the game time, with the partial tick
         * @return how far the square has flown, 0 leaving the hand to 1 landing on the block's near face
         */
        public double flightShareAt(double now) {
            return Math.clamp((now - picked) / Math.max(1, start - picked), 0, 1);
        }

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
        private double route = Double.NaN;
        private double mass = Double.NaN;

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
         * @return the blocks of the block's whole route to the glove, read once since every ring asks
         */
        public double routeLength() {
            if (Double.isNaN(route)) {
                route = remainingFrom(0);
            }
            return route;
        }

        /**
         * @return the blocks of goo this stream and every stream feeding it weigh now, read once the tree is built
         *         since every ring's pace asks
         */
        double subtreeMass() {
            if (Double.isNaN(mass)) {
                double sum = block.massAt(now);
                for (Stream tributary : tributaries) {
                    sum += tributary.subtreeMass();
                }
                mass = sum;
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
         * @return whether the square is still on its way to the block, which stands as itself with no stream
         */
        public boolean awaiting() {
            return block.awaitingAt(now);
        }

        /**
         * @param distance blocks along the stream's own path from its far side
         * @return whether its liquid is still flowing there: its square on its way, or its tail not yet past
         */
        public boolean flowingAt(double distance) {
            return awaiting() || tailAt() < distance;
        }

        /**
         * Whether anything still flows through the stream at a distance along
         * its own path: its own liquid, or a tributary's entering at or before
         * there whose liquid still reaches its join; a trunk whose own liquid
         * has passed still carries what joins it, so its tributaries stay on
         * it and one stream enters the palm rather than each re-rooting to the
         * glove the moment the trunk's own tail passes.
         *
         * @param distance blocks along the stream's own path from its far side
         * @return whether liquid still flows there
         */
        public boolean carriesAt(double distance) {
            if (flowingAt(distance)) {
                return true;
            }
            for (Stream tributary : tributaries) {
                if (tributary.joinShare * path.length() <= distance && tributary.carriesAt(tributary.path.length())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @return whether nothing of the stream or of any stream feeding it is left to draw: the block drained,
         *         every tail past the route's end and no square on its way
         */
        public boolean spent() {
            boolean own = !awaiting() && block.progressAt(now) >= 1 && tailAt() >= routeLength() + DrinkStream.TIP;
            for (Stream tributary : tributaries) {
                own &= tributary.spent();
            }
            return own;
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
            int index = Math.min(table.length - PAIR, (int) (distance / step));
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
     * Whether a laid stream still carries liquid at a distance along its path:
     * where its stream was last built, where that stream carries anything,
     * its own liquid or a tributary's; a block streaming this frame but not
     * yet built flows everywhere, its liquid all still to come, so the blocks
     * of a drink's first frame join one trunk rather than each running to the
     * glove for good; a block no longer streaming flows nowhere, so its
     * tributaries re-root.
     *
     * @param last      each block's stream as last built
     * @param streaming the blocks streaming this frame
     * @return the test the layout lays and re-roots by
     */
    public static DrinkLayout.Flowing flowingOf(Map<BlockPos, Stream> last, Set<BlockPos> streaming) {
        return (pos, distance) -> last.containsKey(pos) ? last.get(pos).carriesAt(distance) : streaming.contains(pos);
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
                    ? new Stream(block, new DrinkStream.Path(node.farSide(), node.endToward(glove, now), block.seed(),
                            pull), null, 0, now)
                    : joining(block, node, trunk, now);
            built.put(block.pos(), stream);
            streams.add(stream);
        }
        return streams;
    }

    private static Stream joining(Block block, DrinkLayout.Node node, Stream trunk, double now) {
        double join = Math.min(1, node.joinAt() / trunk.path().length());
        Vec3 at = node.endToward(DrinkStream.pointAt(trunk.path(), join, now), now);
        Vec3 arrival = DrinkStream.flowAt(trunk.path(), join, now);
        return new Stream(block, new DrinkStream.Path(node.farSide(), at, block.seed(), arrival, trunk.path(), join),
                trunk, join, now);
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
        return laid(rings);
    }

    /**
     * The rings with their material brought down by one multiple of the
     * texture's period for all of them, so the first lies within a period of
     * zero: the texture reads the same, and the shader's floats keep their
     * texels where a long drink's material has run to the tens of thousands.
     *
     * @param rings a path's rings
     * @return the rings, their material shifted together
     */
    static List<DrinkStream.Ring> laid(List<DrinkStream.Ring> rings) {
        double shift = DrinkStream.TEXTURE_PERIOD
                * Math.floor(rings.getFirst().material() / DrinkStream.TEXTURE_PERIOD);
        List<DrinkStream.Ring> laid = new ArrayList<>();
        for (DrinkStream.Ring ring : rings) {
            laid.add(new DrinkStream.Ring(ring.center(), ring.flow(), ring.radius(), ring.material() - shift,
                    ring.share(), ring.speed()));
        }
        return laid;
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
                stream.materialAt(distance), distance / stream.routeLength(), stream.speedAt(share));
    }

    /**
     * The radius of a stream at a share of its path: every stream flowing
     * through there combined as the mean of them times the
     * {@link #TRUNK_ROOT}th root of how many, each counting by how much of it
     * is there, so a trunk grows gently with the streams it carries: nine
     * equal streams make about 1.7 times one, a stream swelling in counts in
     * proportion, and a trunk whose own liquid has passed is still as wide as
     * what flows through it; past {@link #TAPER_FROM} of the route the whole
     * width falls in a straight line to a {@link #THREAD} at the palm, so the
     * stream enters the hand as a thread and never blocks the view there.
     *
     * @param stream the stream
     * @param share  the share of its path
     * @return the radius there, 0 where nothing flows
     */
    static double radiusAt(Stream stream, double share) {
        Flow sum = flowUnder(stream, share);
        if (sum.presence() <= 0) {
            return 0;
        }
        double mean = sum.radius() / sum.presence();
        double taper = Math.pow(Math.min(1, sum.presence()), 1 / TRUNK_ROOT);
        double growth = Math.pow(Math.max(1, sum.presence()), 1 / TRUNK_ROOT);
        double full = mean * taper * growth;
        return THREAD + (full - THREAD) * widthHeldAt(stream, share);
    }

    /**
     * How much of its full width a stream keeps at a share of its path: all of
     * it to {@link #TAPER_FROM} of its whole route to the glove, then falling
     * in a straight line to none at the glove, where it is the thread; a
     * tributary's own path ends at its join, so it keeps the share its join's
     * place on its route leaves it.
     *
     * @param stream the stream
     * @param share  the share of its path
     * @return 1 for the whole width, 0 for the thread
     */
    static double widthHeldAt(Stream stream, double share) {
        double route = share * stream.path().length() / stream.routeLength();
        return 1 - Math.clamp((route - TAPER_FROM) / (1 - TAPER_FROM), 0, 1);
    }

    /**
     * Liquid at a point: its radius and how much of a whole stream it is.
     *
     * @param radius   the radius it gives the point
     * @param presence 1 for a stream wholly there, less as it tapers or swells in, 0 for none
     */
    record Flow(double radius, double presence) {

        Flow plus(Flow other) {
            return new Flow(radius + other.radius, presence + other.presence);
        }
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
        DrinkBody.Box box = progress < 1 && !stream.awaiting() ? DrinkBody.boxAt(block.center(), progress) : null;
        return new DrinkField.Skeleton(stream, rings(stream), box);
    }

    /**
     * The liquid of a stream and of every stream under it at a share of its
     * path, summed: its own, and each tributary's whole branch swelling in
     * over the merge about its join, a branch yet to join skipped whole, and
     * the walk down each branch carrying where its liquid stands so no chain
     * is walked twice; a drink of many blocks builds its rings in time.
     *
     * @param through the stream
     * @param share   a share of its path
     * @return the liquid there
     */
    private static Flow flowUnder(Stream through, double share) {
        double length = through.path().length();
        double distance = share * length;
        double[] sum = new double[PAIR];
        gather(through, distance, 1, sum);
        for (Stream tributary : through.tributaries()) {
            double pastJoin = distance - tributary.joinShare() * length;
            if (pastJoin >= -MERGE) {
                gatherUnder(tributary, tributary.path().length() + Math.max(0, pastJoin), mergeRamp(pastJoin), sum);
            }
        }
        return new Flow(sum[0], sum[1]);
    }

    /**
     * Adds a stream's liquid at a point of its route, and every stream's under
     * it where that stream's liquid stands there: its own path's length past
     * its join, all swelling in as the branch does.
     *
     * @param stream   the stream
     * @param distance blocks along the stream's route the point is
     * @param swell    how far the branch has swelled into the trunk the point is on, 0 to 1
     * @param sum      the radius then the presence, added to
     */
    private static void gatherUnder(Stream stream, double distance, double swell, double[] sum) {
        gather(stream, distance, swell, sum);
        double length = stream.path().length();
        for (Stream tributary : stream.tributaries()) {
            gatherUnder(tributary, tributary.path().length() + distance - tributary.joinShare() * length, swell, sum);
        }
    }

    /**
     * Adds one stream's own liquid at a point of its route: its width there
     * times how much of it is there, the taper at its ends and the swell;
     * nothing while its square is still flying to the block.
     *
     * @param stream   the stream
     * @param distance blocks along the stream's route the point is
     * @param swell    how far the point has swelled into the stream's liquid, 1 on its own path
     * @param sum      the radius then the presence, added to
     */
    private static void gather(Stream stream, double distance, double swell, double[] sum) {
        if (stream.awaiting()) {
            return;
        }
        double presence = DrinkStream.taperAt(distance, stream.tailAt(), stream.headAt()) * swell;
        if (presence > 0) {
            sum[0] += liquidWidth(stream, distance) * presence;
            sum[1] += presence;
        }
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
        return flowOf(stream, through, share).radius();
    }

    /**
     * The liquid one stream alone gives a point of a path it flows through,
     * where it stands there and swollen in about its join on a trunk.
     *
     * @param stream  the stream
     * @param through its own path or a trunk it flows through
     * @param share   a share of that path
     * @return its liquid there, none where it is not there now
     */
    static Flow flowOf(Stream stream, Stream through, double share) {
        Entry entry = entryOf(stream, through, share);
        if (entry == null) {
            return new Flow(0, 0);
        }
        double[] sum = new double[PAIR];
        gather(stream, entry.distance(), stream == through ? 1 : mergeRamp(entry.pastJoin()), sum);
        return new Flow(sum[0], sum[1]);
    }

    /**
     * @param stream   a stream whose block is streaming
     * @param distance blocks along its route
     * @return the width of its matter there before any taper
     */
    private static double liquidWidth(Stream stream, double distance) {
        Block block = stream.block();
        double now = stream.now();
        return DrinkBody.widthAt(distance, block.progressAt(now), block.scale()
                * DrinkStream.widthAt(stream.materialAt(distance), block.seed()), block.seed(), now);
    }

    /**
     * @param pastJoin blocks past a join along the trunk, below zero before it
     * @return how far the trunk has swelled to carry the stream that joins, 0 a merge before the join to 1 a merge past
     */
    static double mergeRamp(double pastJoin) {
        return DrinkStream.smoothRamp(-MERGE, MERGE, pastJoin);
    }
}
