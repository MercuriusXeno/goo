package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's field is the sum of soft bodies read per skeleton: a lone capsule's
 * surface sits at its radius, a box's at its faces and rounded corners, two
 * bodies that come near swell into one another, their surfaces meeting in the
 * gap between them like metaballs, and a body of no radius radiates nothing
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkFieldTest {

    private static final double DELTA = 1e-9;
    private static final double RADIUS = 0.2;
    /** A gap between two surfaces facing each other narrower than half the fillet between streams, which fills. */
    private static final double NEAR_GAP = 0.08;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final DrinkTree.Block BLOCK = new DrinkTree.Block(new BlockPos(7, 2, 3), new Vec3(7.5, 2.5, 3.5),
            1, 0, 0, 100);
    /** A block streaming, picked at tick 90 and landing at 100, drained long after the tests look. */
    private static final DrinkTree.Block STREAMING = streaming(new BlockPos(7, 2, 3));
    /** Its neighbour, which joins its stream. */
    private static final DrinkTree.Block BESIDE = streaming(new BlockPos(7, 3, 4));
    /** A time both streams have run most of the way to the glove. */
    private static final double FLOWING = 112;
    private static final int BISECTIONS = 50;
    private static final double NORMAL_STEP = 1e-4;
    /** Blocks between neighbouring steps of a walk along a spine. */
    private static final double WALK_STEP = 0.013;
    /** Blocks along a lone stream's path a walk starts at: the block's near face, past which its box stands no more. */
    private static final double WALK_FROM = DrinkStream.BLOCK_SPAN;
    /** The share it ends at, short of the thread by the palm. */
    private static final double WALK_TO = 0.9;
    /**
     * The most degrees a smooth skin's normal turns between steps a walk step apart: a bend of the snake or a
     * change of the width turns it a degree or two there, a crease a dozen or more.
     */
    private static final double SMOOTH_TURN = 4;
    /** Blocks a bundle may read past its trunk's radius: where two streams' fillets meet off the spine. */
    private static final double SWELL = 0.01;

    private static DrinkTree.Block streaming(BlockPos pos) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, 90, 100, 1000);
    }

    private static DrinkStream.Ring ring(Vec3 center) {
        return ring(center, RADIUS);
    }

    private static DrinkStream.Ring ring(Vec3 center, double radius) {
        return new DrinkStream.Ring(center, EAST, radius, 0, 0, DrinkStream.FLOW);
    }

    private static DrinkTree.Stream stream() {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(BLOCK.pos()), GLOVE);
        return DrinkTree.build(List.of(BLOCK), layout, GLOVE, EAST, 0).getFirst();
    }

    /**
     * @param from the capsule's start
     * @param to   its end
     * @return a skeleton of one capsule and no box
     */
    private static DrinkField.Skeleton capsule(Vec3 from, Vec3 to) {
        return new DrinkField.Skeleton(stream(), List.of(ring(from), ring(to)), null);
    }

    private static boolean inside(List<DrinkField.Skeleton> skeletons, Vec3 point) {
        return DrinkField.valueAt(skeletons, point) >= DrinkField.ISO;
    }

    @Test
    void theFalloffIsWholeInsideTheIsoAtTheSurfaceAndNothingPastTheReach() {
        assertEquals(1, DrinkField.falloff(-DrinkField.DEPTH), DELTA);
        assertEquals(DrinkField.ISO, DrinkField.falloff(0), DELTA);
        assertEquals(0, DrinkField.falloff(DrinkField.REACH), DELTA);
        assertTrue(DrinkField.falloff(DrinkField.REACH / 2) < DrinkField.ISO);
        assertTrue(DrinkField.falloff(-DrinkField.DEPTH / 2) > DrinkField.ISO);
        assertTrue(DrinkField.ISO > 0.5, "a lone body's surface is where its own field is still high");
    }

    @Test
    void aLoneCapsulesSurfaceSitsAtItsRadius() {
        List<DrinkField.Skeleton> alone = List.of(capsule(Vec3.ZERO, new Vec3(2, 0, 0)));

        assertEquals(DrinkField.ISO, DrinkField.valueAt(alone, new Vec3(1, RADIUS, 0)), DELTA);
        assertTrue(inside(alone, new Vec3(1, RADIUS / 2, 0)));
        assertFalse(inside(alone, new Vec3(1, 2 * RADIUS, 0)));
        assertEquals(DrinkField.ISO, DrinkField.valueAt(alone, new Vec3(-RADIUS, 0, 0)), DELTA);
    }

    @Test
    void aChainIsReadAtItsLeastDistanceSoItsJointsDoNotBulge() {
        DrinkField.Skeleton chain = new DrinkField.Skeleton(stream(),
                List.of(ring(Vec3.ZERO), ring(EAST), ring(EAST.scale(2))), null);

        assertEquals(DrinkField.ISO, DrinkField.valueAt(List.of(chain), new Vec3(1, RADIUS, 0)), DELTA,
                "at the joint of two capsules the surface sits at the radius, not swollen by both");
        assertEquals(2, chain.bodies());
    }

    @Test
    void aBoxsSurfaceSitsAtItsFacesAndItsRoundedCornersAreCut() {
        DrinkBody.Box box = new DrinkBody.Box(Vec3.ZERO, 0.5, 0);
        DrinkBody.Box sphere = new DrinkBody.Box(Vec3.ZERO, 0.5, 0.5);
        DrinkField.Skeleton boxed = new DrinkField.Skeleton(stream(), List.of(), box);

        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.1, 0.2)), DELTA);
        assertEquals(-0.5, box.signedDistance(Vec3.ZERO), DELTA);
        assertEquals(0, box.signedDistance(new Vec3(0.5, 0.5, 0.5)), DELTA);
        assertEquals(0, sphere.signedDistance(UP.scale(0.5)), DELTA);
        assertTrue(sphere.signedDistance(new Vec3(0.5, 0.5, 0.5)) > 0, "a sphere's corner is outside it");
        assertEquals(DrinkField.ISO, DrinkField.valueAt(List.of(boxed), new Vec3(0.5, 0.1, 0.2)), DELTA);
        assertEquals(1, boxed.bodies());
    }

    @Test
    void twoStreamsFacingEachOtherAcrossANarrowGapMergeAndFarOnesDoNot() {
        Vec3 near = new Vec3(0, 2 * RADIUS + NEAR_GAP, 0);
        Vec3 far = new Vec3(0, 2 * RADIUS + 2 * DrinkField.REACH, 0);
        List<DrinkField.Skeleton> touching = List.of(capsule(Vec3.ZERO, EAST), capsule(near, near.add(EAST)));
        List<DrinkField.Skeleton> apart = List.of(capsule(Vec3.ZERO, EAST), capsule(far, far.add(EAST)));

        assertTrue(inside(touching, new Vec3(0.5, RADIUS + NEAR_GAP / 2, 0)), "the gap between near bodies fills");
        assertFalse(inside(apart, new Vec3(0.5, RADIUS + DrinkField.REACH, 0)), "far bodies leave the gap empty");
    }

    @Test
    void aBodyOfNoRadiusRadiatesNothingAndAThinOneReachesInProportion() {
        Vec3 beside = UP.scale(DrinkField.REACH / 2);
        List<DrinkField.Skeleton> empty = List.of(
                new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO, 0), ring(EAST, 0)), null),
                new DrinkField.Skeleton(stream(), List.of(ring(beside, 0), ring(beside.add(EAST), 0)), null));
        double thin = DrinkField.FULL_RADIUS / 2;
        List<DrinkField.Skeleton> slender = List.of(
                new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO, thin), ring(EAST, thin)), null));

        assertEquals(0, DrinkField.valueAt(empty, new Vec3(0.5, 0.01, 0)), DELTA);
        assertFalse(inside(empty, beside.scale(0.5).add(0.5, 0, 0)),
                "two empty lines close together leave no blob between them");
        assertEquals(DrinkField.ISO, DrinkField.valueAt(slender, new Vec3(0.5, thin, 0)), DELTA);
        assertTrue(DrinkField.valueAt(slender, new Vec3(0.5, thin + DrinkField.REACH / 4, 0)) > 0);
        assertEquals(0, DrinkField.valueAt(slender, new Vec3(0.5, thin + DrinkField.REACH / 2 + 0.01, 0)), DELTA,
                "a body half a waist thick reaches half as far");
    }

    /**
     * @param skeletons a drink's skeletons
     * @param middle    a point inside the skin
     * @param outward   a unit direction out of it
     * @return where the skin is along the direction, found by bisection
     */
    private static Vec3 skinAlong(List<DrinkField.Skeleton> skeletons, Vec3 middle, Vec3 outward) {
        double in = 0;
        double out = 1.5;
        for (int step = 0; step < BISECTIONS; step++) {
            double mid = (in + out) / 2;
            if (inside(skeletons, middle.add(outward.scale(mid)))) {
                in = mid;
            } else {
                out = mid;
            }
        }
        return middle.add(outward.scale(in));
    }

    /**
     * @param skeletons a drink's skeletons
     * @param point     a point
     * @return the unit direction the field falls fastest there, the skin's normal at a point on it
     */
    private static Vec3 normalAt(List<DrinkField.Skeleton> skeletons, Vec3 point) {
        double e = NORMAL_STEP;
        return new Vec3(
                DrinkField.valueAt(skeletons, point.add(-e, 0, 0)) - DrinkField.valueAt(skeletons, point.add(e, 0, 0)),
                DrinkField.valueAt(skeletons, point.add(0, -e, 0)) - DrinkField.valueAt(skeletons, point.add(0, e, 0)),
                DrinkField.valueAt(skeletons, point.add(0, 0, -e)) - DrinkField.valueAt(skeletons, point.add(0, 0, e)))
                .normalize();
    }

    /**
     * Walks a stream's spine in short steps, finds the skin out from it on
     * the given side, and measures the most the skin's normal turns between
     * neighbouring steps.
     *
     * @param skeletons the drink's skeletons
     * @param walked    the stream walked
     * @param from      the share of its path the walk starts at
     * @param to        the share it ends at
     * @param side      which side: 1 for its frame's side, -1 for the other, 0 for across
     * @return the greatest turn between neighbouring steps and where along the path it is
     */
    private static Turn steepestTurn(List<DrinkField.Skeleton> skeletons, DrinkTree.Stream walked, double from,
                                       double to, int side) {
        double steepest = 0;
        double steepestAt = 0;
        Vec3 last = null;
        double step = WALK_STEP / walked.path().length();
        for (double share = from; share <= to; share += step) {
            Vec3 spine = DrinkStream.pointAt(walked.path(), share, walked.now());
            Vec3 flow = DrinkStream.flowAt(walked.path(), share, walked.now());
            Vec3 sideways = flow.cross(UP).normalize();
            Vec3 outward = side == 0 ? flow.cross(sideways).normalize() : sideways.scale(side);
            Vec3 normal = normalAt(skeletons, skinAlong(skeletons, spine, outward));
            if (last != null) {
                double turn = Math.toDegrees(Math.acos(Math.min(1, last.dot(normal))));
                if (turn > steepest) {
                    steepest = turn;
                    steepestAt = share * walked.path().length();
                }
            }
            last = normal;
        }
        return new Turn(steepest, steepestAt);
    }

    /**
     * The most a skin's normal turned between neighbouring steps of a walk.
     *
     * @param degrees the turn, in degrees
     * @param at      blocks along the walked path it turned at
     */
    private record Turn(double degrees, double at) {
    }

    private static List<DrinkTree.Stream> treeOf(double now, DrinkTree.Block... blocks) {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(blocks).stream().map(DrinkTree.Block::pos).toList(), GLOVE);
        return DrinkTree.build(List.of(blocks), layout, GLOVE, EAST.scale(-1), now);
    }

    /** decision diagnose-then-fix-unmake-seam-and-ribbing */
    @Test
    void aLoneStreamsSkinTurnsSmoothlyAcrossEveryRingWithNoCrease() {
        DrinkTree.Stream lone = treeOf(FLOWING, STREAMING).getFirst();
        List<DrinkField.Skeleton> skeletons = List.of(new DrinkField.Skeleton(lone, DrinkTree.rings(lone), null));

        for (int side : new int[] {1, -1, 0}) {
            Turn steepest = steepestTurn(skeletons, lone, WALK_FROM / lone.path().length(), WALK_TO, side);
            assertTrue(steepest.degrees() < SMOOTH_TURN, "the skin's normal turns " + steepest.degrees()
                    + " degrees between steps " + WALK_STEP + " blocks apart on side " + side + " at "
                    + steepest.at() + " blocks, a crease at a ring");
        }
    }

    /** decision diagnose-then-fix-unmake-seam-and-ribbing */
    @Test
    void aTributarysSkinTurnsSmoothlyIntoItsTrunk() {
        List<DrinkTree.Stream> tree = treeOf(FLOWING, STREAMING, BESIDE);
        DrinkTree.Stream trunk = tree.getFirst();
        DrinkTree.Stream tributary = trunk.tributaries().getFirst();
        List<DrinkField.Skeleton> skeletons = tree.stream()
                .map(stream -> new DrinkField.Skeleton(stream, DrinkTree.rings(stream), null)).toList();
        double join = tributary.joinShare();
        double merge = DrinkTree.MERGE / trunk.path().length();

        for (int side : new int[] {1, -1, 0}) {
            Turn steepest = steepestTurn(skeletons, trunk, Math.max(0, join - merge), Math.min(1, join + merge),
                    side);
            assertTrue(steepest.degrees() < SMOOTH_TURN, "the trunk's normal turns " + steepest.degrees()
                    + " degrees between steps about the join on side " + side);
        }
    }

    /** decision diagnose-then-fix-unmake-seam-and-ribbing */
    @Test
    void streamsLyingOnOneAnotherAreOneStreamsWidthAndABundleNoWiderThanItsTrunk() {
        List<DrinkField.Skeleton> twice = List.of(capsule(Vec3.ZERO, EAST.scale(2)),
                capsule(Vec3.ZERO, EAST.scale(2)));
        List<DrinkTree.Stream> tree = treeOf(FLOWING, STREAMING, BESIDE);
        DrinkTree.Stream trunk = tree.getFirst();
        List<DrinkField.Skeleton> skeletons = tree.stream()
                .map(stream -> new DrinkField.Skeleton(stream, DrinkTree.rings(stream), null)).toList();
        double join = trunk.tributaries().getFirst().joinShare();
        double merge = DrinkTree.MERGE / trunk.path().length();

        assertEquals(RADIUS, skinAlong(twice, EAST, UP).y, SWELL, "two coincident streams swell into neither");
        for (double share = join; share <= Math.min(1, join + merge); share += WALK_STEP / trunk.path().length()) {
            Vec3 spine = DrinkStream.pointAt(trunk.path(), share, trunk.now());
            Vec3 flow = DrinkStream.flowAt(trunk.path(), share, trunk.now());
            Vec3 sideways = flow.cross(UP).normalize();
            double width = DrinkTree.radiusAt(trunk, share);
            for (Vec3 outward : List.of(sideways, sideways.scale(-1), flow.cross(sideways).normalize())) {
                double skin = skinAlong(skeletons, spine, outward).distanceTo(spine);
                assertTrue(skin <= width + SWELL, "past the join the bundle reads " + skin + " wide where its "
                        + "trunk is " + width);
            }
        }
    }

    @Test
    void aBodysBoxBoundsItsOwnSurface() {
        DrinkField.Skeleton capsule = capsule(Vec3.ZERO, EAST);
        DrinkBody.Box box = new DrinkBody.Box(UP.scale(3), 0.5, 0);
        DrinkField.Skeleton boxed = new DrinkField.Skeleton(stream(), List.of(ring(Vec3.ZERO), ring(EAST)), box);

        assertEquals(new Vec3(-RADIUS, -RADIUS, -RADIUS), capsule.lowOf(0));
        assertEquals(new Vec3(1 + RADIUS, RADIUS, RADIUS), capsule.highOf(0));
        assertEquals(1, capsule.bodies());
        assertEquals(2, boxed.bodies());
        assertEquals(new Vec3(-0.5, 2.5, -0.5), boxed.lowOf(1));
        assertEquals(new Vec3(0.5, 3.5, 0.5), boxed.highOf(1));
        assertEquals(0, new DrinkField.Skeleton(stream(), List.of(), null).bodies());
    }
}
