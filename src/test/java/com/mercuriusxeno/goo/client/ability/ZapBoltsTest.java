package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zap's bolt runs from the hand to the strike point, kinked between,
 * crackling to new kinks every few frames and fading over its short life
 * (decision zap-ticks-the-device-and-stuns).
 */
class ZapBoltsTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 HAND = new Vec3(0, 1.5, 0);
    private static final Vec3 STRIKE = new Vec3(6, 1, 0);

    @Nested
    class Shape {

        @Test
        void aBoltIsPinnedAtTheHandAndTheStrike() {
            Vec3[] bolt = ZapBolts.boltPoints(HAND, STRIKE, ZapBolts.SEGMENTS, ZapBolts.JITTER, new Random(7));
            assertEquals(HAND, bolt[0]);
            assertEquals(STRIKE, bolt[bolt.length - 1]);
            assertEquals(ZapBolts.SEGMENTS + 1, bolt.length);
        }

        @Test
        void eachKinkStaysWithinTheJitterOfTheStraightLine() {
            Vec3[] bolt = ZapBolts.boltPoints(HAND, STRIKE, ZapBolts.SEGMENTS, ZapBolts.JITTER, new Random(7));
            Vec3 axis = STRIKE.subtract(HAND).normalize();
            for (Vec3 kink : bolt) {
                Vec3 offset = kink.subtract(HAND);
                Vec3 sideways = offset.subtract(axis.scale(offset.dot(axis)));
                assertTrue(sideways.length() <= ZapBolts.JITTER * Math.sqrt(2) + EPSILON,
                        () -> "kink strays " + sideways.length());
            }
        }

        @Test
        void aBoltIsKinked() {
            Vec3[] bolt = ZapBolts.boltPoints(HAND, STRIKE, ZapBolts.SEGMENTS, ZapBolts.JITTER, new Random(7));
            Vec3 straightMiddle = HAND.add(STRIKE).scale(0.5);
            assertNotEquals(straightMiddle, bolt[ZapBolts.SEGMENTS / 2]);
        }
    }

    @Nested
    class Animation {

        @Test
        void aBoltFadesFromFullAtTheStrikeToGoneAtItsEnd() {
            assertEquals(1f, ZapBolts.opacity(0));
            assertTrue(ZapBolts.opacity(ZapBolts.LIFETIME_SECONDS / 2) < 1f);
            assertEquals(0f, ZapBolts.opacity(ZapBolts.LIFETIME_SECONDS));
        }

        @Test
        void aBoltIsVeryQuick() {
            assertTrue(ZapBolts.LIFETIME_SECONDS <= 0.5);
        }

        @Test
        void theKinksJumpEachCrackle() {
            assertEquals(ZapBolts.crackleFrame(0), ZapBolts.crackleFrame(ZapBolts.CRACKLE_SECONDS * 0.5));
            assertEquals(ZapBolts.crackleFrame(0) + 1, ZapBolts.crackleFrame(ZapBolts.CRACKLE_SECONDS * 1.5));
        }
    }

    @Nested
    class Trigger {

        @Test
        void aPulseBeamStrikesAsLightning() {
            assertTrue(ZapBolts.strikesAsLightning(GooTypes.PULSE, Delivery.of(DeliveryKind.BEAM)));
        }

        @Test
        void aGlowBeamAndAPulseArcFlyAsBefore() {
            assertFalse(ZapBolts.strikesAsLightning(GooTypes.GLOW, Delivery.of(DeliveryKind.BEAM)));
            assertFalse(ZapBolts.strikesAsLightning(GooTypes.PULSE, Delivery.of(DeliveryKind.ARC)));
        }
    }
}
