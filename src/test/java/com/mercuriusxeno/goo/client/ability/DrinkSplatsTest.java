package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sphere of a drink reaches the splat list: each ring's sphere, spheres
 * between rings spaced under the narrower radius, and the block as its
 * rounded cube until it has rounded into a sphere, each in the block's color
 * crossfading to the goo's by its route share (decision
 * unmake-drink-draws-as-a-screen-space-fluid).
 */
class DrinkSplatsTest {

    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final Vec3 PULL = new Vec3(-1, 0, 0);
    private static final long LANDS = 100;
    private static final long END = 1000;
    private static final long PICKED = LANDS - SiphonRule.INJECT_TICKS;
    /** Before the cube has rounded into a sphere: a fifth of the way through the drain. */
    private static final double CUBE_TIME = LANDS + 0.2 * (END - LANDS);
    /** After the cube has rounded into a sphere and the stream has run a while: past half the drain. */
    private static final double SPHERE_TIME = LANDS + 0.6 * (END - LANDS);
    private static final double BEFORE_LANDING = LANDS - 1;
    private static final DrinkTree.Block NEAR = block(new BlockPos(7, 2, 3));
    private static final DrinkTree.Block FAR = block(new BlockPos(7, 3, 4));
    private static final int BLOCK_COLOR = 0xFF806040;
    private static final int GOO_COLOR = 0xFF20A0E0;
    private static final DrinkSplats.Coat COAT = new DrinkSplats.Coat(BLOCK_COLOR, GOO_COLOR, 0, 0);
    private static final double HALF_ROUTE = 0.5;
    private static final double WIDE = 0.5;
    private static final double APART = 1.0;
    private static final double DELTA = 1e-9;
    private static final double STEP = 0.02;

    private static DrinkTree.Block block(BlockPos pos) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, PICKED, LANDS, END);
    }

    private static List<DrinkTree.Stream> treeAt(double now) {
        DrinkLayout layout = new DrinkLayout();
        layout.place(List.of(FAR.pos(), NEAR.pos()), GLOVE);
        return DrinkTree.build(List.of(FAR, NEAR), layout, GLOVE, PULL, now);
    }

    /**
     * @param splats a stream's splats
     * @param center a ring's middle
     * @return the index of the splat standing exactly there, or -1
     */
    private static int indexAt(List<DrinkSplats.Splat> splats, Vec3 center) {
        for (int index = 0; index < splats.size(); index++) {
            if (splats.get(index).center().equals(center)) {
                return index;
            }
        }
        return -1;
    }

    @Nested
    class AlongAStream {

        @Test
        void everyRingWithLiquidIsASplatOfItsOwnRadius() {
            int rings = 0;
            for (DrinkTree.Stream stream : treeAt(SPHERE_TIME)) {
                List<DrinkSplats.Splat> splats = DrinkSplats.of(stream, COAT);
                for (DrinkStream.Ring ring : DrinkTree.rings(stream)) {
                    if (ring.radius() >= DrinkSplats.NARROWEST) {
                        int index = indexAt(splats, ring.center());
                        assertTrue(index >= 0, "the ring at " + ring.center() + " is splatted");
                        assertEquals(ring.radius(), splats.get(index).half(), DELTA);
                        assertTrue(splats.get(index).sphere());
                        rings++;
                    }
                }
            }
            assertTrue(rings > 0, "the tree carries liquid to splat");
        }

        @Test
        void consecutiveSplatsBetweenTwoRingsLieCloserThanTheSmallerOfTheirRadii() {
            int pairs = 0;
            for (DrinkTree.Stream stream : treeAt(SPHERE_TIME)) {
                List<DrinkSplats.Splat> splats = DrinkSplats.of(stream, COAT);
                List<DrinkStream.Ring> rings = DrinkTree.rings(stream);
                for (int ring = 0; ring + 1 < rings.size(); ring++) {
                    if (Math.min(rings.get(ring).radius(), rings.get(ring + 1).radius()) < DrinkSplats.NARROWEST) {
                        continue;
                    }
                    int from = indexAt(splats, rings.get(ring).center());
                    int to = indexAt(splats, rings.get(ring + 1).center());
                    assertTrue(to > from, "the splats run from ring to ring in order");
                    for (int index = from; index < to; index++) {
                        DrinkSplats.Splat a = splats.get(index);
                        DrinkSplats.Splat b = splats.get(index + 1);
                        assertTrue(a.center().distanceTo(b.center()) < Math.min(a.half(), b.half()),
                                "no gap wider than a radius between neighbours");
                        pairs++;
                    }
                }
            }
            assertTrue(pairs > 0, "some segment carries liquid on both its rings");
        }

        @Test
        void theColorAlongTheRouteIsTheBlocksCrossfadedToTheGoosByItsShare() {
            List<DrinkStream.Ring> rings = List.of(
                    new DrinkStream.Ring(Vec3.ZERO, PULL, WIDE, 0, 0, 1),
                    new DrinkStream.Ring(new Vec3(APART, 0, 0), PULL, WIDE, 0, 1, 1));
            List<DrinkSplats.Splat> splats = new ArrayList<>();

            DrinkSplats.along(rings, COAT, splats);

            DrinkSplats.Splat middle = splats.get(indexAt(splats, new Vec3(APART * HALF_ROUTE, 0, 0)));
            assertEquals(DrinkColors.skinAt(BLOCK_COLOR, GOO_COLOR, 0), splats.getFirst().color());
            assertEquals(DrinkColors.skinAt(BLOCK_COLOR, GOO_COLOR, HALF_ROUTE), middle.color());
            assertEquals(DrinkColors.skinAt(BLOCK_COLOR, GOO_COLOR, 1), splats.getLast().color());
            assertEquals(BLOCK_COLOR, splats.getFirst().color());
            assertEquals(GOO_COLOR, splats.getLast().color());
        }
    }

    @Nested
    class TheBlock {

        private DrinkTree.Stream nearAt(double now) {
            return treeAt(now).stream().filter(stream -> stream.block() == NEAR).findFirst().orElseThrow();
        }

        @Test
        void standsAsAShrinkingRoundedCubeAloneUntilItHasRoundedIntoASphere() {
            List<DrinkSplats.Splat> splats = DrinkSplats.of(nearAt(CUBE_TIME), COAT);

            assertEquals(1, splats.size(), "the cube shrinks alone, its stream not yet flowing");
            DrinkSplats.Splat cube = splats.getFirst();
            assertEquals(NEAR.center(), cube.center());
            assertFalse(cube.sphere(), "its edges still rounding");
            assertTrue(cube.half() < DrinkTree.MOUTH, "shrinking from the first frame");
            assertEquals(BLOCK_COLOR, cube.color());
        }

        @Test
        void isASphereOnceRoundedWithItsStreamFlowingFromIt() {
            List<DrinkSplats.Splat> splats = DrinkSplats.of(nearAt(SPHERE_TIME), COAT);

            DrinkSplats.Splat sphere = splats.getFirst();
            assertEquals(NEAR.center(), sphere.center());
            assertTrue(sphere.sphere());
            assertEquals(DrinkTree.MOUTH * DrinkSplats.sizeAt(NEAR.progressAt(SPHERE_TIME)), sphere.half(), DELTA);
            assertTrue(splats.size() > 1, "the stream flows once the block is a sphere");
        }

        @Test
        void landsAsTheWholeHardCubeWhereTheBlockStood() {
            DrinkSplats.Splat cube = DrinkSplats.of(nearAt(LANDS), COAT).getFirst();

            assertEquals(NEAR.center(), cube.center());
            assertEquals(DrinkTree.MOUTH, cube.half(), DELTA);
            assertEquals(0, cube.rounding(), DELTA);
        }

        @Test
        void shrinksSmoothlyFromTheWholeCubeToNothingAndRoundsOverTheFirstPartOfTheDrain() {
            assertEquals(1, DrinkSplats.sizeAt(0), DELTA);
            assertEquals(0, DrinkSplats.sizeAt(1), DELTA);
            assertTrue(DrinkSplats.sizeAt(STEP) > 1 - STEP, "no jump at the start");
            double before = 1;
            for (double progress = 0; progress <= 1; progress += STEP) {
                assertTrue(DrinkSplats.sizeAt(progress) <= before + DELTA, "shrinks only");
                before = DrinkSplats.sizeAt(progress);
            }
            assertEquals(0, DrinkSplats.liquidityAt(0), DELTA);
            assertEquals(1, DrinkSplats.liquidityAt(DrinkTree.LIQUEFY), DELTA);
        }

        @Test
        void isGoneOnceDrained() {
            List<DrinkSplats.Splat> splats = DrinkSplats.of(nearAt(END), COAT);

            assertTrue(splats.stream().noneMatch(splat -> splat.center().equals(NEAR.center())));
        }

        @Test
        void showsNothingWhileItsSquareIsOnItsWay() {
            assertTrue(DrinkSplats.of(nearAt(BEFORE_LANDING), COAT).isEmpty());
        }
    }
}
