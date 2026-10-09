package com.mercuriusxeno.goo.ability.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which oculus a blink snaps to, and what the blink to it costs and leaves
 * in its charge (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusNodesTest {

    private static final Vec3 EYE = new Vec3(0.5, 65.5, 0.5);
    private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);
    private static final double RANGE = 48;
    private static final double CONE = 10;
    /** Dead ahead, forty blocks south. */
    private static final BlockPos AHEAD = new BlockPos(0, 65, 40);
    /** Forty blocks south and three east, about four degrees off the look. */
    private static final BlockPos SLIGHTLY_OFF = new BlockPos(3, 65, 40);
    /** Ten blocks south and five east, about twenty-seven degrees off the look. */
    private static final BlockPos WIDE = new BlockPos(5, 65, 10);
    /** Sixty blocks south, past the range. */
    private static final BlockPos TOO_FAR = new BlockPos(0, 65, 60);

    @Nested
    class Pick {

        @Test
        void theOculusNearestTheLookWins() {
            assertEquals(Optional.of(AHEAD), OculusNodes.pick(List.of(SLIGHTLY_OFF, AHEAD), EYE, LOOK_SOUTH, RANGE, CONE));
        }

        @Test
        void anOculusInsideTheConeIsTaken() {
            assertEquals(Optional.of(SLIGHTLY_OFF), OculusNodes.pick(List.of(SLIGHTLY_OFF), EYE, LOOK_SOUTH, RANGE, CONE));
        }

        @Test
        void anOculusOutsideTheConeIsNot() {
            assertEquals(Optional.empty(), OculusNodes.pick(List.of(WIDE), EYE, LOOK_SOUTH, RANGE, CONE));
        }

        @Test
        void anOculusPastTheRangeIsNot() {
            assertEquals(Optional.empty(), OculusNodes.pick(List.of(TOO_FAR), EYE, LOOK_SOUTH, RANGE, CONE));
        }
    }

    @Nested
    class Charge {

        private static final int PRICE = 2500;

        @Test
        void aChargeCoveringThePriceMakesTheBlinkFree() {
            assertEquals(0, OculusCharge.costOf(PRICE, PRICE));
            assertEquals(0, OculusCharge.chargeAfter(PRICE, PRICE));
        }

        @Test
        void aShortChargeLeavesThePriceToPayAndTakesItIn() {
            assertEquals(PRICE, OculusCharge.costOf(PRICE, PRICE - 1));
            assertEquals(2 * PRICE - 1, OculusCharge.chargeAfter(PRICE, PRICE - 1));
        }
    }
}
