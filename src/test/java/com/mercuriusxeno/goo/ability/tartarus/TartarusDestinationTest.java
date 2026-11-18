package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where Tartarus' search starts for a player, read from the level they stand in and each level's height.
 */
class TartarusDestinationTest {

    /** The overworld's height: Y -64 to 319. */
    private static final HeightSpan OVERWORLD = HeightSpan.of(-64, 384);
    /** The nether's height under its bedrock roof: Y 0 to 127. */
    private static final HeightSpan NETHER = HeightSpan.of(0, 128);

    private static Optional<TartarusDestination> fromOverworld(int y) {
        return TartarusDestination.from(Level.OVERWORLD, new BlockPos(800, y, -1600), OVERWORLD, NETHER);
    }

    /** decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses */
    @Nested
    class FromTheOverworld {

        @Test
        void theTargetIsTheNether() {
            assertEquals(Optional.of(Level.NETHER), TartarusDestination.targetOf(Level.OVERWORLD));
        }

        @Test
        void theSearchStartsAtAnEighthOfTheXAndZ() {
            BlockPos start = fromOverworld(70).orElseThrow().start();

            assertEquals(100, start.getX());
            assertEquals(-200, start.getZ());
        }

        @Test
        void theTopOfTheOverworldStartsAtTheBottomOfTheNether() {
            assertEquals(Optional.of(new TartarusDestination(Level.NETHER, new BlockPos(100, 0, -200))),
                    fromOverworld(319));
        }

        @Test
        void theBottomOfTheOverworldStartsAtTheTopOfTheNether() {
            assertEquals(Optional.of(new TartarusDestination(Level.NETHER, new BlockPos(100, 127, -200))),
                    fromOverworld(-64));
        }
    }

    /** decision tartarus-in-the-nether-takes-you-to-the-overworld-at-eight-times-your-spot */
    @Nested
    class FromTheNether {

        private Optional<TartarusDestination> fromNether(int y) {
            return TartarusDestination.from(Level.NETHER, new BlockPos(100, y, -200), NETHER, OVERWORLD);
        }

        @Test
        void theTargetIsTheOverworld() {
            assertEquals(Optional.of(Level.OVERWORLD), TartarusDestination.targetOf(Level.NETHER));
        }

        @Test
        void theBottomOfTheNetherStartsAtTheTopOfTheOverworldAtEightTimesTheXAndZ() {
            assertEquals(Optional.of(new TartarusDestination(Level.OVERWORLD, new BlockPos(800, 319, -1600))),
                    fromNether(0));
        }

        @Test
        void theTopOfTheNetherStartsAtTheBottomOfTheOverworld() {
            assertEquals(Optional.of(new TartarusDestination(Level.OVERWORLD, new BlockPos(800, -64, -1600))),
                    fromNether(127));
        }
    }

    @Nested
    class FromTheEnd {

        @Test
        void theGateLeadsNowhere() {
            assertEquals(Optional.empty(), TartarusDestination.targetOf(Level.END));
        }
    }
}
