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
 * A stream's upload gives every radiating body of the stream a proxy grown by
 * the reach, lists for each proxy every body of the drink whose reach meets
 * it as runs per skeleton with the stream's own first, copies the rings those
 * runs need with their path's frame, names boxes apart from segments, leaves
 * out what the caps have no room for, and packs it all camera-relative in the
 * block the shader reads (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkUploadTest {

    private static final double DELTA = 1e-6;
    private static final double RADIUS = 0.2;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 CAMERA = new Vec3(10, 20, 30);
    private static final GooRenderUtil.UvRect SPRITE = new GooRenderUtil.UvRect(0.1f, 0.2f, 0.3f, 0.4f);
    private static final int BLOCK_LIGHT = 160;
    private static final int SKY_LIGHT = 240;
    private static final DrinkUpload.Coat COAT = new DrinkUpload.Coat(0xFF8040C0, SPRITE, BLOCK_LIGHT, SKY_LIGHT, true,
            false, List.of(new DrinkUpload.Layer(0xFFFFFFFF, SPRITE, 0f, 0.75f, 3)));
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

    private static DrinkStream.Ring ring(Vec3 center, double radius) {
        return new DrinkStream.Ring(center, EAST, radius, center.x, center.x / 10, DrinkStream.FLOW);
    }

    /**
     * @param stream the skeleton's stream
     * @param origin where its chain starts
     * @param rings  how many rings, a block apart along x
     * @param radius their radius
     * @return a skeleton of a straight chain and no box
     */
    private static DrinkField.Skeleton chain(DrinkTree.Stream stream, Vec3 origin, int rings, double radius) {
        return chain(stream, origin, rings, radius, 1);
    }

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

    private static int entryAt(ByteBuffer bytes, int index) {
        return bytes.getInt(DrinkUpload.TABLE_AT + index * Integer.BYTES);
    }

    private static int proxyFirst(ByteBuffer bytes, int proxy) {
        return (int) floatAt(bytes, DrinkUpload.PROXY_LOW_AT + proxy * DrinkUpload.VEC4 + Integer.BYTES * (PAIR + 1));
    }

    private static int proxyCount(ByteBuffer bytes, int proxy) {
        return (int) floatAt(bytes, DrinkUpload.PROXY_HIGH_AT + proxy * DrinkUpload.VEC4 + Integer.BYTES * (PAIR + 1));
    }

    private static List<Integer> entriesOf(ByteBuffer bytes, int proxy) {
        List<Integer> entries = new ArrayList<>();
        for (int index = 0; index < proxyCount(bytes, proxy); index++) {
            entries.add(entryAt(bytes, proxyFirst(bytes, proxy) + index));
        }
        return entries;
    }

    private static int ringsCount(ByteBuffer bytes) {
        return (int) floatAt(bytes, DrinkUpload.COUNTS_AT);
    }

    @Nested
    class Proxies {

        @Test
        void everySegmentGetsAProxyGrownByTheReachAndListsTheSegmentsReachingIt() {
            DrinkField.Skeleton lone = chain(streams(1).getFirst(), Vec3.ZERO, 3, RADIUS);

            DrinkUpload.Block block = DrinkUpload.of(List.of(lone), 0, CAMERA, COAT);
            ByteBuffer bytes = block.bytes();

            assertEquals(2, block.proxies().size());
            assertEquals(0, block.proxies().getFirst().low()
                    .distanceTo(new Vec3(-RADIUS - DrinkField.REACH, -RADIUS - DrinkField.REACH,
                            -RADIUS - DrinkField.REACH).subtract(CAMERA)), DELTA);
            assertEquals(0, block.proxies().getFirst().high()
                    .distanceTo(new Vec3(1 + RADIUS + DrinkField.REACH, RADIUS + DrinkField.REACH,
                            RADIUS + DrinkField.REACH).subtract(CAMERA)), DELTA);
            assertEquals(List.of(DrinkUpload.RUN_START, 1), entriesOf(bytes, 0),
                    "the proxy lists its own segment, starting the run, and the next one, which reaches it");
            assertEquals(List.of(DrinkUpload.RUN_START, 1), entriesOf(bytes, 1));
            assertEquals(3, ringsCount(bytes));
            assertEquals(2, (int) floatAt(bytes, DrinkUpload.COUNTS_AT + PAIR * Float.BYTES), "two proxies");
        }

        @Test
        void everyProxyOfTheLongestStreamWithANeighbourAtEachJoinKeepsItsEntries() {
            List<DrinkTree.Stream> streams = streams(4);
            double spacing = 1.0 / DrinkStream.RINGS_PER_BLOCK;
            int rings = DrinkTree.stationsAlong(SiphonRule.RANGE + DrinkStream.GLOVE_SLACK);
            DrinkField.Skeleton mine = chain(streams.get(0), Vec3.ZERO, rings, DrinkStream.WAIST, spacing);
            List<DrinkField.Skeleton> skeletons = new ArrayList<>(List.of(mine));
            for (int index = 1; index < streams.size(); index++) {
                skeletons.add(chain(streams.get(index), UP.scale(2 * DrinkStream.WAIST + 0.1).add(EAST.scale(index * 3)),
                        DrinkStream.RINGS_PER_BLOCK, DrinkStream.WAIST, spacing));
            }

            DrinkUpload.Block block = DrinkUpload.of(skeletons, 0, CAMERA, COAT);

            assertEquals(rings - 1, block.proxies().size());
            assertEquals(0, block.dropped(), "no entry is dropped");
            for (int proxy = 0; proxy < block.proxies().size(); proxy++) {
                assertTrue(entriesOf(block.bytes(), proxy).contains(proxy == 0 ? DrinkUpload.RUN_START : proxy)
                        || entriesOf(block.bytes(), proxy).contains(proxy | DrinkUpload.RUN_START),
                        "proxy " + proxy + " lists its own segment");
            }
        }

        @Test
        void aBoxIsAProxyAndIsListedAsABox() {
            DrinkTree.Stream stream = streams(1).getFirst();
            DrinkBody.Box box = new DrinkBody.Box(new Vec3(-1, 0, 0), 0.5, 0.1);
            DrinkField.Skeleton skeleton = new DrinkField.Skeleton(stream,
                    List.of(ring(Vec3.ZERO, RADIUS), ring(EAST, RADIUS)), box);

            ByteBuffer bytes = DrinkUpload.of(List.of(skeleton), 0, CAMERA, COAT).bytes();

            assertEquals(1, (int) floatAt(bytes, DrinkUpload.COUNTS_AT + Float.BYTES), "one box");
            assertEquals(List.of(DrinkUpload.RUN_START, DrinkUpload.BOX_BASE), entriesOf(bytes, 0),
                    "the segment's proxy lists the box beside it");
            assertEquals(List.of(DrinkUpload.RUN_START, DrinkUpload.BOX_BASE), entriesOf(bytes, 1),
                    "the box's proxy lists the segment and itself");
            assertEquals(-1 - CAMERA.x, floatAt(bytes, DrinkUpload.BOXES_AT), DELTA, "the box is camera-relative");
            assertEquals(0.1, floatAt(bytes, DrinkUpload.BOXES_AT + DrinkUpload.VEC4), DELTA);
        }

        @Test
        void aBodyOfNoRadiusGetsNoProxyAndIsNotListed() {
            List<DrinkTree.Stream> streams = streams(2);
            DrinkField.Skeleton gone = chain(streams.get(0), Vec3.ZERO, 3, 0);
            DrinkField.Skeleton beside = chain(streams.get(1), UP.scale(0.1), 2, RADIUS);

            DrinkUpload.Block block = DrinkUpload.of(List.of(gone, beside), 0, CAMERA, COAT);
            DrinkUpload.Block other = DrinkUpload.of(List.of(gone, beside), 1, CAMERA, COAT);

            assertTrue(block.proxies().isEmpty());
            assertEquals(List.of(DrinkUpload.RUN_START), entriesOf(other.bytes(), 0),
                    "a body of no radius is listed by no neighbour");
            assertEquals(2, ringsCount(other.bytes()));
        }
    }

    @Nested
    class Neighbours {

        @Test
        void aNeighbourWithinReachIsCopiedAsItsOwnRunAndAFarOneIsLeftOut() {
            List<DrinkTree.Stream> streams = streams(3);
            DrinkField.Skeleton mine = chain(streams.get(0), Vec3.ZERO, 3, RADIUS);
            DrinkField.Skeleton near = chain(streams.get(1), UP.scale(2 * RADIUS + 0.1).add(EAST.scale(4)), 3, RADIUS);
            DrinkField.Skeleton far = chain(streams.get(2), UP.scale(5), 3, RADIUS);

            ByteBuffer bytes = DrinkUpload.of(List.of(mine, near, far), 0, CAMERA, COAT).bytes();

            assertEquals(3, ringsCount(bytes), "a neighbour nowhere within reach is not copied");
            DrinkField.Skeleton touching = chain(streams.get(1), UP.scale(2 * RADIUS + 0.1).add(EAST), 3, RADIUS);
            ByteBuffer touched = DrinkUpload.of(List.of(mine, touching, far), 0, CAMERA, COAT).bytes();
            assertEquals(6, ringsCount(touched), "the neighbour's whole span of listed rings is copied");
            assertEquals(List.of(DrinkUpload.RUN_START, 1, DrinkUpload.RUN_START | 3), entriesOf(touched, 0),
                    "the neighbour's segment starts its own run after the stream's own");
            assertEquals(1, floatAt(touched, DrinkUpload.RINGS_AT + (PAIR * 3 + 1) * DrinkUpload.VEC4
                    + PAIR * Float.BYTES), DELTA, "a copied ring names the neighbour's frame");
            assertEquals(0, floatAt(touched, DrinkUpload.RINGS_AT + DrinkUpload.VEC4 + PAIR * Float.BYTES), DELTA,
                    "the stream's own rings name its own frame");
        }

        @Test
        void neighboursPastTheFrameCapAreLeftOutRatherThanOverrunningTheBlock() {
            List<DrinkTree.Stream> streams = streams(DrinkUpload.MOST_FRAMES + 2);
            List<DrinkField.Skeleton> skeletons = new ArrayList<>();
            for (int index = 0; index < streams.size(); index++) {
                skeletons.add(chain(streams.get(index), UP.scale(index * 0.05), 3, RADIUS));
            }

            ByteBuffer bytes = DrinkUpload.of(skeletons, 0, CAMERA, COAT).bytes();

            assertEquals(3 * DrinkUpload.MOST_FRAMES, ringsCount(bytes));
            assertEquals(DrinkUpload.MOST_FRAMES, entriesOf(bytes, 0).stream()
                    .filter(entry -> (entry & DrinkUpload.RUN_START) != 0).count(), "one run per frame kept");
        }
    }

    @Test
    void theBlockPacksTheCoatAndTheCameraRelativeRings() {
        DrinkField.Skeleton lone = chain(streams(1).getFirst(), new Vec3(1, 2, 3), 2, RADIUS);

        ByteBuffer bytes = DrinkUpload.of(List.of(lone), 0, CAMERA, COAT).bytes();

        assertEquals(DrinkUpload.BYTES, bytes.capacity());
        assertEquals(BLOCK_LIGHT, floatAt(bytes, DrinkUpload.COAT_AT), DELTA);
        assertEquals(SKY_LIGHT, floatAt(bytes, DrinkUpload.COAT_AT + Float.BYTES), DELTA);
        assertEquals(1, floatAt(bytes, DrinkUpload.COAT_AT + PAIR * Float.BYTES), DELTA, "the zoop flag");
        assertEquals(0x80 / 255f, floatAt(bytes, DrinkUpload.TINT_AT), DELTA);
        assertEquals(0.3f, floatAt(bytes, DrinkUpload.SPRITE_AT + PAIR * Float.BYTES), DELTA);
        assertEquals(1, floatAt(bytes, DrinkUpload.COUNTS_AT + (PAIR + 1) * Float.BYTES), DELTA, "one layer");
        assertEquals(0.75f, floatAt(bytes, DrinkUpload.LAYER_SHARE_AT + Float.BYTES), DELTA);
        assertEquals(3, floatAt(bytes, DrinkUpload.LAYER_SHARE_AT + PAIR * Float.BYTES), DELTA, "the layer's seed");
        assertEquals(1 - CAMERA.x, floatAt(bytes, DrinkUpload.RINGS_AT), DELTA);
        assertEquals(2 - CAMERA.y, floatAt(bytes, DrinkUpload.RINGS_AT + Float.BYTES), DELTA);
        assertEquals(RADIUS, floatAt(bytes, DrinkUpload.RINGS_AT + (PAIR + 1) * Float.BYTES), DELTA);
        assertEquals(1, floatAt(bytes, DrinkUpload.RINGS_AT + DrinkUpload.VEC4), DELTA, "the ring's material");
        Vec3 side = DrinkStream.sideOf(lone.stream().path());
        assertEquals(side.x, floatAt(bytes, DrinkUpload.FRAMES_AT), DELTA);
    }
}
