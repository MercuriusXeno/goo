package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleFunction;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A whole drink of many blocks run frame by frame the way the renderer runs
 * it: the layout placed on the last frame's streams, the tree built, spent
 * streams left out. Every block's liquid flows all the way to the hand: the
 * skin drawn along a block's liquid, down whichever paths it rides, never
 * shortens faster than the liquid flows, and a block whose liquid rides a
 * trunk keeps that trunk until its tail is home, so no stream stops drawing
 * or jumps in the air before its tail reaches the hand.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision unmake-streams-collapse-into-a-central-trunk
 */
class DrinkLifecycleTest {

    private static final Vec3 EYE = new Vec3(0, 1.6, 0.5);
    private static final Vec3 LOOK = new Vec3(1, 0, 0);
    private static final Vec3 GLOVE = new Vec3(0.5, 1.2, 0.8);
    private static final double FRAME = 0.25;
    /** A skin thinner than this is not seen. */
    private static final double SEEN = DrinkStream.THINNEST / 2;
    /** Blocks of a route a block's liquid is sampled at. */
    private static final double SAMPLE = 0.05;
    /** Blocks of seen liquid a frame may lose beyond how far the block's tail flowed in it. */
    private static final double SLACK = 0.25;
    /** Blocks a tail may run past the fastest pace in a frame: the station table's rounding. */
    private static final double SAME_PACE = 0.02;
    /**
     * How far past the fastest pace a tail may run: positions are read from the time since the liquid left through
     * this frame's paths, so as the hand or the pull shortens a path behind it, a tail on the trunk gains a little.
     */
    private static final double PACE_DRIFT = 1.25;
    private static final long PICKED = 0;
    private static final long LANDS = 9;
    private static final int REPORTED = 8;
    /** The most regions a frame of the cone drink may march, a guard set just above what it marches today. */
    private static final int WORK_REGIONS = 160;
    /** The most bodies a frame of the cone drink may list across its regions, a guard set just above today's. */
    private static final int WORK_ENTRIES = 4600;
    /** Degrees either way the look sweeps while drinking. */
    private static final double SWEEP_DEGREES = 25;
    /** Ticks a radian of the sweep takes. */
    private static final double SWEEP_TICKS = 12;
    /** Blocks the hand sways each way while drinking. */
    private static final double HAND_SWAY = 0.3;
    /** Ticks over which the blocks are picked one after another. */
    private static final long STAGGER_TICKS = 30;

    /**
     * @return a cone of blocks ahead of the eye, each draining over its own time
     */
    static List<DrinkTree.Block> cone() {
        List<DrinkTree.Block> blocks = new ArrayList<>();
        for (int x = 3; x <= 7; x++) {
            int reach = x < 5 ? 0 : 1;
            for (int y = 1 - reach; y <= 1 + reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    long drain = 20 + Math.floorMod(pos.hashCode() * 31L, 41);
                    blocks.add(new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, PICKED, LANDS, LANDS + drain));
                }
            }
        }
        return blocks;
    }

    /**
     * @param stream   a block's stream
     * @param distance blocks along its route
     * @return the radius the drawn skin has at that point of its route, 0 where the path owning it is not drawn
     */
    private static double skinOnRoute(DrinkTree.Stream stream, double distance) {
        DrinkTree.Stream owner = stream;
        double along = distance;
        while (along > owner.path().length() && owner.trunk() != null) {
            along = owner.joinShare() * owner.trunk().path().length() + along - owner.path().length();
            owner = owner.trunk();
        }
        if (owner.spent() || along > owner.path().length()) {
            return 0;
        }
        double skin = DrinkTree.radiusAt(owner, along / owner.path().length());
        double toJoin = owner.path().length() - along;
        if (owner.trunk() != null && toJoin < DrinkStream.RIDE) {
            DrinkTree.Stream trunk = owner.trunk();
            double onTrunk = owner.joinShare() * trunk.path().length() - toJoin;
            if (onTrunk >= 0 && !trunk.spent()) {
                skin = Math.max(skin, DrinkTree.radiusAt(trunk, onTrunk / trunk.path().length()));
            }
        }
        return skin;
    }

    /**
     * @param stream a block's stream
     * @return blocks of its liquid in the air, down whichever paths it rides, the skin is seen along
     */
    private static double seenLiquid(DrinkTree.Stream stream) {
        if (stream.awaiting()) {
            return 0;
        }
        double from = Math.max(DrinkStream.BLOCK_SPAN, stream.tailAt());
        double to = Math.min(stream.routeLength(), stream.headAt());
        double seen = 0;
        for (double distance = from; distance < to; distance += SAMPLE) {
            if (skinOnRoute(stream, distance) >= SEEN) {
                seen += Math.min(SAMPLE, to - distance);
            }
        }
        return seen;
    }

    /**
     * @param stream a block's stream
     * @return its skin's radius every quarter block from its tail to the end of its route, and whether each point's
     *         owner is spent, so a report shows where the skin went
     */
    private static String skinAlong(DrinkTree.Stream stream) {
        StringBuilder skin = new StringBuilder();
        for (double distance = Math.max(DrinkStream.BLOCK_SPAN, stream.tailAt()); distance < stream.routeLength();
             distance += 0.25) {
            DrinkTree.Stream owner = stream;
            double along = distance;
            while (along > owner.path().length() && owner.trunk() != null) {
                along = owner.joinShare() * owner.trunk().path().length() + along - owner.path().length();
                owner = owner.trunk();
            }
            DrinkTree.Flow own = DrinkTree.flowOf(stream, owner, along / owner.path().length());
            skin.append(String.format("%.2f:%.3f(%s %.2f/%.2f%s own %.3f/%.3f) ", distance,
                    skinOnRoute(stream, distance), owner.block().pos().toShortString(), along, owner.path().length(),
                    owner.spent() ? " spent" : "", own.width(), own.presence()));
        }
        return skin.toString();
    }

    private static List<BlockPos> chainOf(DrinkTree.Stream stream) {
        List<BlockPos> chain = new ArrayList<>();
        for (DrinkTree.Stream up = stream.trunk(); up != null; up = up.trunk()) {
            chain.add(up.block().pos());
        }
        return chain;
    }

    private static List<String> massesOf(DrinkTree.Stream stream) {
        List<String> masses = new ArrayList<>();
        for (DrinkTree.Stream up = stream; up != null; up = up.trunk()) {
            masses.add(String.format("%.1f", up.subtreeMass()));
        }
        return masses;
    }

    private static String stateOf(DrinkTree.Stream stream) {
        return String.format("tail %.2f, head %.2f, path %.2f, route %.2f, chain %s, masses %s, end %d",
                stream.tailAt(), stream.headAt(), stream.path().length(), stream.routeLength(), chainOf(stream),
                massesOf(stream), stream.block().end());
    }

    @Test
    void everyBlocksLiquidFlowsAllTheWayToTheHandWithoutVanishingOrJumping() {
        assertFlowsHome(cone(), now -> LOOK, now -> GLOVE);
    }

    @Test
    void everyBlocksLiquidFlowsHomeWhileTheLookSweepsAndTheHandMoves() {
        assertFlowsHome(cone(), now -> {
            double yaw = Math.toRadians(SWEEP_DEGREES) * Math.sin(now / SWEEP_TICKS);
            return new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        }, now -> GLOVE.add(HAND_SWAY * Math.sin(now / 10), HAND_SWAY * Math.sin(now / 7), HAND_SWAY
                * Math.cos(now / 9)));
    }

    @Test
    void everyBlocksLiquidFlowsHomeWhenTheBlocksArePickedOverTime() {
        List<DrinkTree.Block> staggered = new ArrayList<>();
        int index = 0;
        for (DrinkTree.Block block : cone()) {
            long picked = (index++ * 7L) % STAGGER_TICKS;
            long drain = block.end() - block.start();
            staggered.add(new DrinkTree.Block(block.pos(), block.center(), 1, picked, picked + LANDS,
                    picked + LANDS + drain));
        }
        assertFlowsHome(staggered, now -> LOOK, now -> GLOVE);
    }

    /**
     * Every region the drink is uploaded in holds every body that reaches it,
     * so the shader draws the whole field there: a body a region's caps left
     * out broke the skin at that region's faces, block-sized segments, and
     * cut its stream out of that region.
     * decision unmake-waves-dissolve-by-crucible-cost
     */
    @Test
    void everyRegionOfTheDrinksUploadHoldsEveryBodyReachingIt() {
        List<DrinkTree.Block> blocks = cone();
        Set<BlockPos> streaming = new HashSet<>();
        blocks.forEach(block -> streaming.add(block.pos()));
        DrinkLayout layout = new DrinkLayout();
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        List<String> dropped = new ArrayList<>();
        for (double now = PICKED; now <= LANDS + 80 && dropped.size() < REPORTED; now += 1) {
            Vec3 pull = layout.pullToward(LOOK, now);
            layout.place(streaming, GLOVE, new DrinkLayout.Aim(EYE, LOOK), now,
                    DrinkTree.flowingOf(built, streaming));
            Map<BlockPos, DrinkTree.Stream> frame = new HashMap<>();
            List<DrinkField.Skeleton> skeletons = new ArrayList<>();
            List<DrinkUpload.Coat> coats = new ArrayList<>();
            for (DrinkTree.Stream stream : DrinkTree.build(blocks, layout, GLOVE, pull, now)) {
                frame.put(stream.block().pos(), stream);
                if (!stream.spent()) {
                    skeletons.add(DrinkTree.skeleton(stream));
                    coats.add(new DrinkUpload.Coat(0, 0, 0, 0));
                }
            }
            built = frame;
            int left = 0;
            int regions = 0;
            for (DrinkUpload.Block region : DrinkUpload.of(skeletons, coats, Vec3.ZERO, true)) {
                left += region.dropped();
                regions += region.dropped() > 0 ? 1 : 0;
            }
            if (left > 0) {
                dropped.add(String.format("at %.0f: %d bodies left out of %d regions; worst region needs %s", now,
                        left, regions, worstDemandOf(skeletons)));
            }
        }
        assertTrue(dropped.isEmpty(), "the upload's caps left bodies out:\n" + String.join("\n", dropped));
        assertTrue(DrinkUpload.BYTES <= DrinkUpload.MOST_BYTES, "a region's block is " + DrinkUpload.BYTES
                + " bytes, past what every driver allows a uniform block");
    }

    /** Measures the shader's work over the cone drink: the regions it marches and the bodies they list. */
    @Test
    void theDrinksShaderWorkStaysWithinBudget() {
        List<DrinkTree.Block> blocks = cone();
        Set<BlockPos> streaming = new HashSet<>();
        blocks.forEach(block -> streaming.add(block.pos()));
        DrinkLayout layout = new DrinkLayout();
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        int mostRegions = 0;
        int mostEntries = 0;
        long totalEntries = 0;
        int frames = 0;
        for (double now = PICKED; now <= LANDS + 90; now += 1) {
            Vec3 pull = layout.pullToward(LOOK, now);
            layout.place(streaming, GLOVE, new DrinkLayout.Aim(EYE, LOOK), now,
                    DrinkTree.flowingOf(built, streaming));
            Map<BlockPos, DrinkTree.Stream> frame = new HashMap<>();
            List<DrinkField.Skeleton> skeletons = new ArrayList<>();
            List<DrinkUpload.Coat> coats = new ArrayList<>();
            for (DrinkTree.Stream stream : DrinkTree.build(blocks, layout, GLOVE, pull, now)) {
                frame.put(stream.block().pos(), stream);
                if (!stream.spent()) {
                    skeletons.add(DrinkTree.skeleton(stream));
                    coats.add(new DrinkUpload.Coat(0, 0, 0, 0));
                }
            }
            built = frame;
            List<DrinkUpload.Block> regions = DrinkUpload.of(skeletons, coats, Vec3.ZERO, true);
            int entries = 0;
            for (DrinkUpload.Block region : regions) {
                entries += (int) region.bytes().getFloat(DrinkUpload.COUNTS_AT + 3 * Float.BYTES);
            }
            mostRegions = Math.max(mostRegions, regions.size());
            mostEntries = Math.max(mostEntries, entries);
            totalEntries += entries;
            frames++;
        }
        assertTrue(mostRegions <= WORK_REGIONS && mostEntries <= WORK_ENTRIES, String.format(
                "the drink's shader work: at most %d regions and %d listed bodies a frame, %d bodies a frame on average",
                mostRegions, mostEntries, totalEntries / frames));
    }

    /**
     * @param skeletons a frame's skeletons
     * @return the most streams, boxes, rings and table entries any one region asks for, against the caps
     */
    private static String worstDemandOf(List<DrinkField.Skeleton> skeletons) {
        Map<BlockPos, Map<Integer, List<Integer>>> regions = new HashMap<>();
        for (int index = 0; index < skeletons.size(); index++) {
            for (int body = 0; body < skeletons.get(index).bodies(); body++) {
                if (!DrinkUpload.radiates(skeletons.get(index), body)) {
                    continue;
                }
                DrinkUpload.Bound bound = DrinkUpload.boundOf(skeletons.get(index), index, body);
                for (int x = DrinkUpload.regionOf(bound.low().x); x <= DrinkUpload.regionOf(bound.high().x); x++) {
                    for (int y = DrinkUpload.regionOf(bound.low().y); y <= DrinkUpload.regionOf(bound.high().y); y++) {
                        for (int z = DrinkUpload.regionOf(bound.low().z); z <= DrinkUpload.regionOf(bound.high().z);
                             z++) {
                            regions.computeIfAbsent(new BlockPos(x, y, z), ignored -> new HashMap<>())
                                    .computeIfAbsent(index, ignored -> new ArrayList<>()).add(body);
                        }
                    }
                }
            }
        }
        int streams = 0;
        int boxes = 0;
        int rings = 0;
        int entries = 0;
        for (Map<Integer, List<Integer>> region : regions.values()) {
            int regionBoxes = 0;
            int regionRings = 0;
            int regionEntries = 0;
            for (Map.Entry<Integer, List<Integer>> run : region.entrySet()) {
                List<Integer> bodies = run.getValue().stream().sorted().toList();
                int segments = skeletons.get(run.getKey()).segments();
                regionRings += DrinkUpload.spanOf(bodies, segments).count();
                regionBoxes += bodies.getLast() >= segments ? 1 : 0;
                regionEntries += bodies.size();
            }
            streams = Math.max(streams, region.size());
            boxes = Math.max(boxes, regionBoxes);
            rings = Math.max(rings, regionRings);
            entries = Math.max(entries, regionEntries);
        }
        return String.format("%d/%d streams, %d/%d boxes, %d/%d rings, %d/%d entries", streams,
                DrinkUpload.MOST_STREAMS, boxes, DrinkUpload.MOST_BOXES, rings, DrinkUpload.MOST_RINGS, entries,
                DrinkUpload.MOST_ENTRIES);
    }

    /**
     * Runs a drink frame by frame as the renderer does, each block joining the drink once it is picked, and asserts
     * no block's liquid stops drawing, leaps or jumps trunks in the air.
     *
     * @param all     the drink's blocks
     * @param lookAt  the drinker's look at a time
     * @param gloveAt the glove at a time
     */
    private static void assertFlowsHome(List<DrinkTree.Block> all, DoubleFunction<Vec3> lookAt,
                                        DoubleFunction<Vec3> gloveAt) {
        double last = all.stream().mapToLong(DrinkTree.Block::end).max().orElseThrow() + 200;
        double fastest = DrinkStream.FLOW * Math.sqrt(all.size());
        DrinkLayout layout = new DrinkLayout();
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        Map<BlockPos, Double> seenBefore = new HashMap<>();
        List<String> faults = new ArrayList<>();
        for (double now = PICKED; now <= last && faults.size() < REPORTED; now += FRAME) {
            double at = now;
            List<DrinkTree.Block> blocks = all.stream().filter(block -> block.picked() <= at).toList();
            Set<BlockPos> streaming = new HashSet<>();
            blocks.forEach(block -> streaming.add(block.pos()));
            Vec3 look = lookAt.apply(now);
            Vec3 glove = gloveAt.apply(now);
            Vec3 pull = layout.pullToward(look, now);
            layout.place(streaming, glove, new DrinkLayout.Aim(EYE, look), now,
                    DrinkTree.flowingOf(built, streaming));
            Map<BlockPos, DrinkTree.Stream> frame = new HashMap<>();
            for (DrinkTree.Stream stream : DrinkTree.build(blocks, layout, glove, pull, now)) {
                frame.put(stream.block().pos(), stream);
            }
            Map<BlockPos, Double> seenNow = new HashMap<>();
            for (DrinkTree.Stream stream : frame.values()) {
                BlockPos pos = stream.block().pos();
                double seen = seenLiquid(stream);
                seenNow.put(pos, seen);
                DrinkTree.Stream was = built.get(pos);
                double lost = seenBefore.getOrDefault(pos, 0.0) - seen;
                double advanced = was == null || was.awaiting() ? 0
                        : Math.clamp(stream.tailAt(), DrinkStream.BLOCK_SPAN, stream.routeLength())
                                - Math.clamp(was.tailAt(), DrinkStream.BLOCK_SPAN, was.routeLength());
                if (advanced > fastest * FRAME * PACE_DRIFT + SAME_PACE) {
                    faults.add(String.format("%s at %.2f: its tail leapt %.2f blocks in a frame: now %s; before %s",
                            pos.toShortString(), now, advanced, stateOf(stream), stateOf(was)));
                }
                if (lost > Math.max(0, advanced) + SLACK) {
                    faults.add(String.format("%s at %.2f lost %.2f of %.2f blocks seen: now %s; before %s%n"
                                    + "    skin now %s%n    skin before %s", pos.toShortString(), now, lost,
                            seenBefore.get(pos), stateOf(stream), stateOf(was), skinAlong(stream),
                            skinAlong(was)));
                }
                boolean riding = was != null && !was.awaiting() && was.headAt() > was.path().length()
                        && was.tailAt() < was.routeLength();
                if (riding && !Objects.equals(chainOf(was), chainOf(stream))) {
                    faults.add(String.format("%s at %.2f jumped trunks while its liquid rode them: now %s; before %s",
                            pos.toShortString(), now, stateOf(stream), stateOf(was)));
                }
            }
            built = frame;
            seenBefore = seenNow;
        }
        assertTrue(faults.isEmpty(), "a block's liquid stopped drawing or jumped in the air:\n"
                + String.join("\n", faults));
    }
}
