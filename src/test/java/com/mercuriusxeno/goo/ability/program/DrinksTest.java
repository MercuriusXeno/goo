package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmake's payout credits a drunk block's exact value through the player's ledger,
 * free of the level (decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer).
 */
class DrinksTest {

    @Test
    void aBlockWorthSixtyFourThirdsLandsTwentyOneAndKeepsAThird() {
        RemainderLedger ledger = new RemainderLedger();

        GooContents landed = Drinks.payoutThrough(fraction(GooTypes.LEAF, 64, 3), ledger);

        assertEquals(new GooContents(Map.of(GooTypes.LEAF, 21)), landed);
        assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.LEAF));
    }

    @Test
    void threeBlocksWorthSixtyFourThirdsLandSixtyFourInAll() {
        RemainderLedger ledger = new RemainderLedger();
        int landed = 0;

        for (int block = 0; block < 3; block++) {
            landed += Drinks.payoutThrough(fraction(GooTypes.LEAF, 64, 3), ledger).getVolume(GooTypes.LEAF);
        }

        assertEquals(64, landed);
    }

    /**
     * The point an activation's chunks converge on stands near the blocks it
     * unmakes and stays put as more join
     * (decision unmake-converges-near-the-unmade-blocks-into-a-goo-item).
     */
    @Nested
    class ConvergencePoint {

        private static final BlockPos FIRST = new BlockPos(4, 64, 4);
        private static final BlockPos SECOND = FIRST.east();
        private static final BlockPos THIRD = SECOND.east();

        @Test
        void twoBlocksConvergeWithinABlockOfThem() {
            Vec3 point = Drinks.convergencePoint(null, List.of(FIRST, SECOND));

            AABB near = AABB.encapsulatingFullBlocks(FIRST, SECOND).inflate(1);
            assertTrue(near.contains(point), point.toString());
        }

        @Test
        void aThirdBlockJoiningLeavesThePointPut() {
            Vec3 point = Drinks.convergencePoint(null, List.of(FIRST, SECOND));

            assertEquals(point, Drinks.convergencePoint(point, List.of(FIRST, SECOND, THIRD)));
        }
    }
}
