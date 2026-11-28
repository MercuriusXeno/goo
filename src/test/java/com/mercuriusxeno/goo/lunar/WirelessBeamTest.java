package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wireless emitter's scan: it reaches every position within 16 Manhattan of the emitter and no farther, picks the
 * closest block that takes a signal, and breaks ties by one fixed order so the pick never flickers.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
class WirelessBeamTest {

    private static final BlockPos EMITTER = new BlockPos(100, 64, 100);

    @Nested
    class Reach {

        @Test
        void theScanCoversEveryPositionWithinSixteenManhattanButTheEmittersOwn() {
            assertEquals(6016, WirelessBeam.OFFSETS.size());
            assertTrue(WirelessBeam.OFFSETS.stream().allMatch(offset -> {
                int distance = Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ());
                return distance >= 1 && distance <= WirelessBeam.RANGE;
            }));
        }

        @Test
        void aReceiverSeventeenAwayIsOutOfReach() {
            BlockPos far = EMITTER.offset(17, 0, 0);
            assertEquals(Optional.empty(), WirelessBeam.closest(EMITTER, Set.of(far)::contains));
        }
    }

    @Nested
    class Pick {

        @Test
        void theCloserOfTwoReceiversIsPicked() {
            BlockPos near = EMITTER.offset(10, 0, 0);
            BlockPos far = EMITTER.offset(-14, 0, 0);
            assertEquals(Optional.of(near), WirelessBeam.closest(EMITTER, Set.of(near, far)::contains));
        }

        @Test
        void equallyCloseReceiversAlwaysYieldTheSamePick() {
            BlockPos east = EMITTER.offset(5, 0, 0);
            BlockPos west = EMITTER.offset(-5, 0, 0);
            BlockPos up = EMITTER.offset(0, 5, 0);
            Optional<BlockPos> pick = WirelessBeam.closest(EMITTER, Set.of(east, west, up)::contains);
            assertEquals(pick, WirelessBeam.closest(EMITTER, Set.of(up, west, east)::contains));
            assertTrue(pick.isPresent());
        }
    }
}
