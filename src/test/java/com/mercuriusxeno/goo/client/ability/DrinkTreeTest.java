package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink's streams union on a tree: the nearest runs to the glove, a farther
 * block joins the nearer stream toward the hand, a tributary's route runs its
 * own path then the trunk's remainder, and the trunk carries the owner alone
 * before a join and the area sum after it, swelling in over the merge length
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class DrinkTreeTest {

    private static final double DELTA = 1e-9;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    private static final long START = 100;
    /** A drain long enough that no tail has left the block while the tests look. */
    private static final long END = 1000;
    private static final double NOW = 100;
    private static final DrinkTree.Block NEAR = block(new BlockPos(7, 2, 3), 1);
    private static final DrinkTree.Block FAR = block(new BlockPos(7, 3, 4), 1);
    /** Blocks past the join and its merge at which both liquids are read on the trunk. */
    private static final double PAST_THE_MERGE = 0.3;
    private static final long THOUSAND = 1000;
    private static final long QUARTER = 250;
    private static final long FOUR_THOUSAND = 4000;

    private static DrinkTree.Block block(BlockPos pos, double scale) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), scale, START, END);
    }

    private static List<DrinkTree.Stream> tree() {
        return DrinkTree.build(List.of(FAR, NEAR), GLOVE, NOW);
    }

    /**
     * @param trunk the trunk
     * @param blocksPastJoin blocks past the tributary's join along the trunk
     * @return the share of the trunk's path there
     */
    private static double pastJoin(DrinkTree.Stream trunk, double blocksPastJoin) {
        return trunk.tributaries().getFirst().joinShare() + blocksPastJoin / trunk.path().length();
    }

    @Nested
    class Shape {

        @Test
        void aLoneStreamRunsFromItsBlocksFarSideToTheGlove() {
            List<DrinkTree.Stream> streams = DrinkTree.build(List.of(NEAR), GLOVE, NOW);

            assertEquals(1, streams.size());
            DrinkTree.Stream stream = streams.getFirst();
            assertNull(stream.trunk());
            assertEquals(GLOVE, stream.path().to());
            assertEquals(DrinkStream.BLOCK_SPAN / 2, stream.path().from().distanceTo(NEAR.center()), DELTA);
        }

        @Test
        void aFartherBlockJoinsTheNearerStreamOnItsCentrelineTowardTheHand() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double nearest = trunk.path().nearestShare(FAR.center());

            assertSame(NEAR, trunk.block());
            assertSame(trunk, tributary.trunk());
            assertEquals(List.of(tributary), trunk.tributaries());
            assertEquals(nearest + DrinkTree.LEAD / trunk.path().length(), tributary.joinShare(), DELTA);
            assertEquals(0, tributary.path().to().distanceTo(DrinkStream.pointAt(trunk.path(), tributary.joinShare(),
                    NOW)), DELTA);
        }

        @Test
        void aTributarysRouteRunsItsPathThenTheTrunksRemainder() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);

            assertEquals(trunk.path().length(), trunk.routeLength(), DELTA);
            assertEquals(tributary.path().length() + (1 - tributary.joinShare()) * trunk.path().length(),
                    tributary.routeLength(), DELTA);
        }

        @Test
        void theScaleIsTheSquareRootOfTheVolumeOverAThousand() {
            assertEquals(1, DrinkTree.scaleOf(THOUSAND), DELTA);
            assertEquals(0.5, DrinkTree.scaleOf(QUARTER), DELTA);
            assertEquals(2, DrinkTree.scaleOf(FOUR_THOUSAND), DELTA);
        }
    }

    @Nested
    class Width {

        @Test
        void theTrunkCarriesTheOwnerAloneBeforeTheJoinAndTheAreaSumAfterIt() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double before = tributary.joinShare() / 2;
            double after = pastJoin(trunk, DrinkTree.MERGE + PAST_THE_MERGE);
            double later = NOW + tributary.routeLength() / DrinkStream.FLOW;

            assertEquals(0, DrinkTree.contribution(tributary, trunk, before, later), DELTA);
            assertEquals(DrinkTree.contribution(trunk, trunk, before, later), DrinkTree.ring(trunk, before, later)
                    .radius(), DELTA);
            double owner = DrinkTree.contribution(trunk, trunk, after, later);
            double joined = DrinkTree.contribution(tributary, trunk, after, later);
            assertTrue(joined > 0, "the tributary's liquid is on the trunk");
            assertEquals(owner * owner + joined * joined, Math.pow(DrinkTree.ring(trunk, after, later).radius(), 2),
                    DELTA);
        }

        @Test
        void theTrunkSwellsIntoAJoinOverTheMergeLength() {
            assertEquals(0, DrinkTree.mergeRamp(0), DELTA);
            assertEquals(0.5, DrinkTree.mergeRamp(DrinkTree.MERGE / 2), DELTA);
            assertEquals(1, DrinkTree.mergeRamp(DrinkTree.MERGE), DELTA);
            assertEquals(1, DrinkTree.mergeRamp(DrinkTree.MERGE * 2), DELTA);
        }

        @Test
        void aTributarysLiquidReachesTheTrunkOnlyAfterFlowingItsOwnPath() {
            List<DrinkTree.Stream> streams = tree();
            DrinkTree.Stream trunk = streams.getFirst();
            DrinkTree.Stream tributary = streams.get(1);
            double share = pastJoin(trunk, DrinkTree.MERGE);
            double ownPath = tributary.path().length();
            double onTrunk = ownPath + DrinkTree.MERGE + DrinkStream.TIP;

            assertEquals(0, DrinkTree.contribution(tributary, trunk, share, START + ownPath / DrinkStream.FLOW),
                    DELTA);
            assertTrue(DrinkTree.contribution(tributary, trunk, share, START + onTrunk / DrinkStream.FLOW) > 0);
        }

        @Test
        void aRingCarriesTheOwnersLiquidAndItsShareOfTheRoute() {
            DrinkTree.Stream stream = DrinkTree.build(List.of(NEAR), GLOVE, NOW).getFirst();
            DrinkStream.Ring ring = DrinkTree.ring(stream, 0.5, NOW);

            assertEquals(0.5, ring.share(), DELTA);
            assertEquals(DrinkStream.materialAt(0.5 * stream.path().length(), NOW), ring.material(), DELTA);
            List<DrinkStream.Ring> rings = DrinkTree.rings(stream, 0.5, NOW);
            assertEquals(0.5, rings.getFirst().share(), DELTA);
            assertEquals(1, rings.getLast().share(), DELTA);
            assertTrue(rings.size() > DrinkStream.RINGS_PER_BLOCK, "ten rings to the block");
        }
    }
}
