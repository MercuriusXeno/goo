package com.mercuriusxeno.goo.ability.tartarus;

import com.mercuriusxeno.goo.ability.end.EndAvailability;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.TartarusStep;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers whether Tartarus stands available or grays out by the level the player stands in.
 * decision tartarus-in-the-nether-takes-you-to-the-overworld-at-eight-times-your-spot
 */
class TartarusAvailabilityTest {

    private static final List<TartarusStep> TARTARUS = List.of(new TartarusStep(Expr.literal(20), 4));

    @Test
    void availableInTheOverworld() {
        assertEquals(EndAvailability.AVAILABLE, TartarusAvailability.of(Level.OVERWORLD));
    }

    @Test
    void availableInTheNether() {
        assertEquals(EndAvailability.AVAILABLE, TartarusAvailability.of(Level.NETHER));
    }

    @Test
    void grayedOutInTheEnd() {
        assertEquals(EndAvailability.GRAYED_OUT, TartarusAvailability.of(Level.END));
    }

    @Test
    void theGrayOutSeamTheRadialAndServerReadGraysTartarusOutInTheEnd() {
        assertTrue(EndAvailability.graysOut(TARTARUS, Level.END, false), "grayed out in the End");
        assertFalse(EndAvailability.graysOut(TARTARUS, Level.NETHER, false), "available in the nether");
    }
}
