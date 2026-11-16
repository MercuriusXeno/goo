package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Wisps cell's shed: the wisp heads for dark air in reach no other wisp
 * has claimed, and each wisp spends a layer of the cell by the chance
 * glow_wisps.json names.
 * wisps-goo-sheds-a-wisp-toward-nearby-dark
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
class WispShedTest {

    private static final int THRESHOLD = 8;
    private static final int LAYERS = 5;

    @Nested
    class Destination {

        private final BlockPos lit = new BlockPos(4, 1, 0);
        private final BlockPos claimedDark = new BlockPos(9, 1, 0);
        private final BlockPos freeDark = new BlockPos(12, 1, 0);
        private final Map<BlockPos, Integer> light = Map.of(lit, THRESHOLD, claimedDark, 0, freeDark, 0);

        @Test
        void aWispHeadsForTheFreeDarkCell() {
            Optional<BlockPos> picked = WispShed.destination(List.of(lit, claimedDark, freeDark), light::get,
                    THRESHOLD, Set.of(claimedDark)::contains, RandomSource.create(0L));

            assertEquals(Optional.of(freeDark), picked);
        }
    }

    /** operator ruling 2026-11-16: the reach scales with the fill, n+1, so a full cell seeks farthest. */
    @Nested
    class Reach {

        @Test
        void aOneLayerCellSeeksTwoBlocksOut() {
            assertEquals(2, WispShed.reachOf(1));
        }

        @Test
        void aFullCellSeeksSixteenBlocksOut() {
            assertEquals(16, WispShed.reachOf(15));
        }
    }

    @Nested
    class Spend {

        private final double chance = WispsStep.in(AbilityJson.decode("glow_wisps").behaviors())
                .orElseThrow().shedSpendChance();

        @Test
        void aDrawUnderTheChanceSpendsALayer() {
            assertEquals(LAYERS - 1, WispShed.layersAfterShedding(LAYERS, chance / 2, chance));
        }

        @Test
        void aDrawOverTheChanceLeavesTheCellWhole() {
            assertEquals(LAYERS, WispShed.layersAfterShedding(LAYERS, (1 + chance) / 2, chance));
        }
    }
}
