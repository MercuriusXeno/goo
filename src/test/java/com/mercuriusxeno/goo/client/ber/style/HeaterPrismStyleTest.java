package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.ability.program.DegradeStep;
import com.mercuriusxeno.goo.ability.program.GlacialStep;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.PrismCrystal;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A charging heater warms into the heater it becomes: its color and its
 * glow rise with the share charged and land on the heater's own look at a
 * full share, so the conversion is one transition.
 * prism-conversion-creeps-over-the-hold
 */
class HeaterPrismStyleTest {

    /** The plain column's color: white at the crystal's translucency. */
    private static final int PLAIN = ARGB.color(170, 0xFFFFFF);
    /** Packed light for block light 3 and sky light 7. */
    private static final int DIM = 3 << 4 | 7 << 20;
    private static final float HALF = 0.5f;

    @Test
    void aFullChargeDrawsTheHeatersColor() {
        CrystalClusterSubmitter.Look plain = new CrystalClusterSubmitter.Look(
                new GooRenderUtil.UvRect(0f, 0f, 1f, 1f), PLAIN);
        assertEquals(HeaterPrismStyle.embered(PLAIN),
                PrismCrystal.decaying(plain, 1f, HeaterPrismStyle.EMBER).color());
    }

    @Test
    void noChargeKeepsTheLight() {
        assertEquals(DIM, HeaterPrismStyle.warmingLight(DIM, 0f));
    }

    @Test
    void aFullChargeGlowsFullBright() {
        assertEquals(LightCoordsUtil.FULL_BRIGHT, HeaterPrismStyle.warmingLight(DIM, 1f));
    }

    @Test
    void aHalfChargeGlowsHalfWay() {
        int half = HeaterPrismStyle.warmingLight(DIM, HALF);
        assertEquals(9, half >> 4 & 15, "block light 3 rises half way to 15");
        assertEquals(11, half >> 20 & 15, "sky light 7 rises half way to 15");
    }

    @Test
    void onlyTheEmberGlowsWhileCharging() {
        assertTrue(HeaterPrismStyle.glowsWhileCharging(HeaterPrismStyle.EMBER));
        assertFalse(HeaterPrismStyle.glowsWhileCharging(GlacialStep.GLACIER_BLUE));
        assertFalse(HeaterPrismStyle.glowsWhileCharging(DegradeStep.NETHER_MAROON));
    }
}
