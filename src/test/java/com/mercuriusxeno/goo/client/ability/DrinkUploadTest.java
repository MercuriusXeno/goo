package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's upload cuts space into one-block regions and gives every region
 * some body's field reaches one block: the bodies reaching it as runs per
 * stream, the rings those runs need, each stream's coat and frame, boxes
 * named apart from segments, caps dropping whole streams or entries and
 * counting them, all packed camera-relative
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkUploadTest {

    private static final double DELTA = 1e-6;
    private static final double RADIUS = 0.2;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 MIDDLE = new Vec3(0, 0.5, 0.5);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 CAMERA = new Vec3(10, 20, 30);
    private static final GooRenderUtil.UvRect SPRITE = new GooRenderUtil.UvRect(0.1f, 0.2f, 0.3f, 0.4f);
    private static final int BLOCK_LIGHT = 160;
    private static final int SKY_LIGHT = 240;
    private static final DrinkUpload.Coat COAT = new DrinkUpload.Coat(0xFF8040C0, SPRITE, BLOCK_LIGHT, SKY_LIGHT,
            List.of(new DrinkUpload.Layer(0xFFFFFFFF, SPRITE, 0f, 0.75f, 3)));
    private static final DrinkUpload.Coat OTHER = new DrinkUpload.Coat(0xFF00FF00, SPRITE, 0, 0, List.of());
    private static final int PAIR = 2;

    private static List<DrinkTree.Stream> streams(int count) {
        List<DrinkTree.Block> blocks = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BlockPos pos = new BlockPos(7, 2 + index, 3);
            positions.add(pos);
            blocks.add(new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, 0, 0, 100));
        }
        DrinkLayout layout = new DrinkLayout();
        layout.place(positions, GLOVE);
        return DrinkTree.build(blocks, layout, GLOVE, EAST, 0);
    }

    private static List<DrinkUpload.Coat> coats(int count) {
        List<DrinkUpload.Coat> coats = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            coats.add(index == 0 ? COAT : OTHER);
        }
        return coats;
    }

    private static DrinkStream.Ring ring(Vec3 center, double radius) {
        return new DrinkStream.Ring(center, EAST, radius, center.x, center.x / 10, DrinkStream.FLOW);
    }

    private static DrinkField.Skeleton chain(DrinkTree.Stream stream, Vec3 origin, int rings, double radius) {
        return chain(stream, origin, rings, radius, 1);
    }

    /**
     * @param stream  the skeleton's stream
     * @param origin  where its chain starts
     * @param rings   how many rings
     * @param radius  their radius
     * @param spacing blocks between rings along x
     * @return a skeleton of a straight chain and no box
     */
    private static DrinkField.Skeleton chain(DrinkTree.Stream stream, Vec3 origin, int rings, double radius,
                                             double spacing) {
        List<DrinkStream.Ring> chain = new ArrayList<>();
        for (int index = 0; index < rings; index++) {
            chain.add(ring(origin.add(EAST.scale(index * spacing)), radius));
        }
        return new DrinkField.Skeleton(stream, chain, null);
    }

    private static float floatAt(ByteBuffer bytes, int at) {
        return bytes.getFloat(at);
    }

    private static int countAt(ByteBuffer bytes, int component) {
        return (int) floatAt(bytes, DrinkUpload.COUNTS_AT + component * Float.BYTES);
    }

    private static List<Integer> entriesOf(ByteBuffer bytes) {
        List<Integer> entries = new ArrayList<>();
        for (int index = 0; index < countAt(bytes, PAIR + 1); index++) {
            entries.add(bytes.getInt(DrinkUpload.TABLE_AT + index * Integer.BYTES));
        }
        return entries;
    }

    private static DrinkUpload.Block regionAt(List<DrinkUpload.Block> blocks, int x, int y, int z) {
        Vec3 low = new Vec3(x, y, z).subtract(CAMERA);
        return blocks.stream().filter(block -> block.proxies().getFirst().low().distanceTo(low) < DELTA).findFirst()
                .orElseThrow();
    }

    private static int dropped(List<DrinkUpload.Block> blocks) {
        return blocks.stream().mapToInt(DrinkUpload.Block::dropped).sum();
    }

    @Nested
    class Regions {

        @Test
        void aBodyIsListedInEveryRegionItsReachTouchesAndNowhereElse() {
            DrinkField.Skeleton lone = chain(streams(1).getFirst(), MIDDLE, 3, RADIUS);

            List<DrinkUpload.Block> blocks = DrinkUpload.of(List.of(lone), coats(1), CAMERA, false);

            assertEquals(4, blocks.size(), "the chain from x 0 to 2 reaches regions -1 to 2 and one each in y and z");
            DrinkUpload.Block first = regionAt(blocks, -1, 0, 0);
            assertEquals(0, first.proxies().getFirst().high().distanceTo(new Vec3(0, 1, 1).subtract(CAMERA)), DELTA,
                    "a region is one block, camera-relative");
            assertEquals(List.of(DrinkUpload.RUN_START), entriesOf(first.bytes()),
                    "the region before the chain lists only the first segment");
            assertEquals(List.of(DrinkUpload.RUN_START, 1), entriesOf(regionAt(blocks, 0, 0, 0).bytes()));
            assertEquals(List.of(DrinkUpload.RUN_START), entriesOf(regionAt(blocks, 2, 0, 0).bytes()),
                    "the region past the chain lists only the last segment, which is its first local one");
            assertEquals(2, countAt(regionAt(blocks, 2, 0, 0).bytes(), 1), "it copies the segment's two rings alone");
            assertEquals(1 - CAMERA.x, floatAt(regionAt(blocks, 2, 0, 0).bytes(), DrinkUpload.RINGS_AT), DELTA,
                    "the copied span starts at the segment's first ring");
            assertEquals(0, dropped(blocks));
        }

        @Test
        void aBodyOfNoRadiusTouchesNoRegion() {
            DrinkField.Skeleton gone = chain(streams(1).getFirst(), MIDDLE, 3, 0);

            assertTrue(DrinkUpload.of(List.of(gone), coats(1), CAMERA, false).isEmpty());
        }

        @Test
        void aBoxIsListedAsABoxWithItsStream() {
            List<DrinkTree.Stream> streams = streams(2);
            DrinkBody.Box box = new DrinkBody.Box(new Vec3(0.5, 0.5, 0.5), 0.5, 0.1);
            DrinkField.Skeleton boxed = new DrinkField.Skeleton(streams.get(0), List.of(), box);
            DrinkField.Skeleton beside = chain(streams.get(1), new Vec3(1.3, 0.5, 0.5), 2, RADIUS);

            ByteBuffer bytes = regionAt(DrinkUpload.of(List.of(boxed, beside), coats(2), CAMERA, false), 1, 0, 0)
                    .bytes();

            assertEquals(List.of(DrinkUpload.RUN_START | DrinkUpload.BOX_BASE, DrinkUpload.RUN_START), entriesOf(bytes),
                    "the box's run comes first, then the chain's");
            assertEquals(1, countAt(bytes, PAIR), "one box");
            assertEquals(0.5 - CAMERA.x, floatAt(bytes, DrinkUpload.BOXES_AT), DELTA, "the box is camera-relative");
            assertEquals(0.1, floatAt(bytes, DrinkUpload.BOXES_AT + DrinkUpload.VEC4), DELTA);
            assertEquals(0, floatAt(bytes, DrinkUpload.BOXES_AT + DrinkUpload.VEC4 + Float.BYTES), DELTA,
                    "the box names its stream");
            assertEquals(1, floatAt(bytes, DrinkUpload.RINGS_AT + DrinkUpload.VEC4 + PAIR * Float.BYTES), DELTA,
                    "the chain's rings name the second stream");
        }
    }

    @Nested
    class Streams {

        @Test
        void twoStreamsInOneRegionAreTwoRunsWithTheirOwnCoatsAndFrames() {
            List<DrinkTree.Stream> streams = streams(2);
            DrinkField.Skeleton mine = chain(streams.get(0), MIDDLE, 2, RADIUS);
            DrinkField.Skeleton other = chain(streams.get(1), MIDDLE.add(UP.scale(0.3)), 2, RADIUS);

            ByteBuffer bytes = regionAt(DrinkUpload.of(List.of(mine, other), coats(2), CAMERA, true), 0, 0, 0).bytes();

            assertEquals(2, countAt(bytes, 0), "two streams");
            assertEquals(4, countAt(bytes, 1), "four rings");
            assertEquals(List.of(DrinkUpload.RUN_START, DrinkUpload.RUN_START | 2), entriesOf(bytes),
                    "the second stream's segment is its first local one after the first stream's two rings");
            int second = DrinkUpload.STREAMS_AT + DrinkUpload.STREAM_VEC4S * DrinkUpload.VEC4;
            assertEquals(0x80 / 255f, floatAt(bytes, DrinkUpload.STREAMS_AT + DrinkUpload.TINT_SLOT * DrinkUpload.VEC4),
                    DELTA, "the first coat's red");
            assertEquals(0, floatAt(bytes, second + DrinkUpload.TINT_SLOT * DrinkUpload.VEC4), DELTA,
                    "the second coat has no red");
            assertEquals(1, floatAt(bytes, second + DrinkUpload.TINT_SLOT * DrinkUpload.VEC4 + Float.BYTES), DELTA,
                    "and is whole green");
            Vec3 side = DrinkStream.sideOf(other.stream().path());
            assertEquals(side.x, floatAt(bytes, second + DrinkUpload.SIDE_SLOT * DrinkUpload.VEC4), DELTA);
            assertEquals(1, floatAt(bytes, DrinkUpload.REGION_AT + (PAIR + 1) * Float.BYTES), DELTA, "the depth flag");
            assertEquals(-CAMERA.x, floatAt(bytes, DrinkUpload.REGION_AT), DELTA, "the region's low corner");
        }

        @Test
        void eachRunCarriesTheBoxItsBodiesReachAndItsSpanOfTheTable() {
            List<DrinkTree.Stream> streams = streams(2);
            DrinkField.Skeleton mine = chain(streams.get(0), MIDDLE, 2, RADIUS);
            DrinkField.Skeleton other = chain(streams.get(1), MIDDLE.add(UP.scale(0.3)), 2, RADIUS);
            double reach = RADIUS + DrinkField.REACH;

            ByteBuffer bytes = regionAt(DrinkUpload.of(List.of(mine, other), coats(2), CAMERA, true), 0, 0, 0).bytes();

            int low = DrinkUpload.STREAMS_AT + DrinkUpload.RUN_LOW_SLOT * DrinkUpload.VEC4;
            int high = DrinkUpload.STREAMS_AT + DrinkUpload.RUN_HIGH_SLOT * DrinkUpload.VEC4;
            int second = DrinkUpload.STREAMS_AT + DrinkUpload.STREAM_VEC4S * DrinkUpload.VEC4;
            assertEquals(-reach - CAMERA.x, floatAt(bytes, low), DELTA, "the run's box starts a reach before its ring");
            assertEquals(0.5 - reach - CAMERA.y, floatAt(bytes, low + Float.BYTES), DELTA);
            assertEquals(1 + reach - CAMERA.x, floatAt(bytes, high), DELTA, "and ends a reach past its last");
            assertEquals(0, floatAt(bytes, low + (PAIR + 1) * Float.BYTES), DELTA, "the first run starts the table");
            assertEquals(1, floatAt(bytes, high + (PAIR + 1) * Float.BYTES), DELTA, "and lists one segment");
            assertEquals(1, floatAt(bytes, second + DrinkUpload.RUN_LOW_SLOT * DrinkUpload.VEC4 + (PAIR + 1)
                    * Float.BYTES), DELTA, "the second run follows it");
            assertEquals(0.8 + reach - CAMERA.y, floatAt(bytes, second + DrinkUpload.RUN_HIGH_SLOT * DrinkUpload.VEC4
                    + Float.BYTES), DELTA, "its box is its own");
        }

        @Test
        void streamsPastTheCapAreDroppedWholeAndCounted() {
            List<DrinkTree.Stream> streams = streams(DrinkUpload.MOST_STREAMS + 1);
            List<DrinkField.Skeleton> skeletons = new ArrayList<>();
            for (int index = 0; index < streams.size(); index++) {
                skeletons.add(chain(streams.get(index), MIDDLE.add(UP.scale(index * 0.01)), 2, RADIUS));
            }

            List<DrinkUpload.Block> blocks = DrinkUpload.of(skeletons, coats(streams.size()), CAMERA, false);
            ByteBuffer bytes = regionAt(blocks, 0, 0, 0).bytes();

            assertEquals(DrinkUpload.MOST_STREAMS, countAt(bytes, 0));
            assertEquals(DrinkUpload.MOST_STREAMS, entriesOf(bytes).size());
            assertTrue(dropped(blocks) > 0, "the dropped stream is counted");
        }

        @Test
        void theLongestStreamWithNeighboursAtEveryJoinDropsNothing() {
            List<DrinkTree.Stream> streams = streams(4);
            double spacing = 1.0 / DrinkStream.RINGS_PER_BLOCK;
            int rings = DrinkTree.stationsAlong(SiphonRule.RANGE + DrinkStream.GLOVE_SLACK);
            List<DrinkField.Skeleton> skeletons = new ArrayList<>();
            skeletons.add(chain(streams.get(0), MIDDLE, rings, DrinkStream.WAIST, spacing));
            for (int index = 1; index < streams.size(); index++) {
                skeletons.add(chain(streams.get(index), MIDDLE.add(UP.scale(2 * DrinkStream.WAIST + 0.1))
                        .add(EAST.scale(index * 3)), DrinkStream.RINGS_PER_BLOCK, DrinkStream.WAIST, spacing));
            }

            List<DrinkUpload.Block> blocks = DrinkUpload.of(skeletons, coats(streams.size()), CAMERA, false);

            assertEquals(0, dropped(blocks));
            assertTrue(blocks.size() >= rings * spacing, "a region a block along the stream at least");
        }
    }

    @Test
    void theBlockPacksTheCoatTheLayersAndTheCameraRelativeRings() {
        DrinkField.Skeleton lone = chain(streams(1).getFirst(), new Vec3(1.2, 2.5, 3.5), 2, RADIUS);

        ByteBuffer bytes = regionAt(DrinkUpload.of(List.of(lone), coats(1), CAMERA, false), 1, 2, 3).bytes();

        assertEquals(DrinkUpload.BYTES, bytes.capacity());
        int coat = DrinkUpload.STREAMS_AT + DrinkUpload.COAT_SLOT * DrinkUpload.VEC4;
        assertEquals(BLOCK_LIGHT, floatAt(bytes, coat), DELTA);
        assertEquals(SKY_LIGHT, floatAt(bytes, coat + Float.BYTES), DELTA);
        assertEquals(1, floatAt(bytes, coat + PAIR * Float.BYTES), DELTA, "one layer");
        assertEquals(0.3f, floatAt(bytes, DrinkUpload.STREAMS_AT + DrinkUpload.SPRITE_SLOT * DrinkUpload.VEC4
                + PAIR * Float.BYTES), DELTA);
        int share = DrinkUpload.STREAMS_AT + DrinkUpload.LAYER_SHARE_SLOT * DrinkUpload.VEC4;
        assertEquals(0.75f, floatAt(bytes, share + Float.BYTES), DELTA);
        assertEquals(3, floatAt(bytes, share + PAIR * Float.BYTES), DELTA, "the layer's seed");
        assertEquals(1.2 - CAMERA.x, floatAt(bytes, DrinkUpload.RINGS_AT), DELTA);
        assertEquals(2.5 - CAMERA.y, floatAt(bytes, DrinkUpload.RINGS_AT + Float.BYTES), DELTA);
        assertEquals(RADIUS, floatAt(bytes, DrinkUpload.RINGS_AT + (PAIR + 1) * Float.BYTES), DELTA);
        assertEquals(1.2, floatAt(bytes, DrinkUpload.RINGS_AT + DrinkUpload.VEC4), DELTA, "the ring's material");
        assertEquals(0, floatAt(bytes, DrinkUpload.RINGS_AT + DrinkUpload.VEC4 + PAIR * Float.BYTES), DELTA,
                "the ring names its stream");
        assertEquals(0.12f, floatAt(bytes, DrinkUpload.RINGS_AT + DrinkUpload.VEC4 + Float.BYTES), DELTA,
                "and its share of the route, which the skin's crossfade reads");
    }
}
