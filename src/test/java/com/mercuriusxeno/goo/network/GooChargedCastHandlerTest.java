package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A charged cast reaches as far as its ability's own range, past it by the
 * release slack every range gate gives, measured from where the caster's eye
 * stood as the charge began
 * (decisions each-ability-names-its-range-and-the-shared-throw-range-retires,
 * what-starts-in-range-resolves-out-of-range,
 * diagnose-and-correct-the-indicator-disagreeing-with-the-cast).
 */
class GooChargedCastHandlerTest {

    private static final Vec3 EYE = new Vec3(3, 70.62, -8);
    private static final Vec3 EIGHTEEN_OUT = EYE.add(0, -8, Math.sqrt(18 * 18 - 8 * 8));
    private static final Vec3 FIFTEEN_OUT = EYE.add(0, -9, 12);

    @Test
    void aBlastBegunEighteenBlocksOutIsRefusedWhereverItIsReleased() {
        Vec3 besideTheSpot = EIGHTEEN_OUT.add(0, 1, 0);

        assertFalse(GooChargedCastHandler.reaches(besideTheSpot, Optional.of(EYE), EIGHTEEN_OUT, blast()));
    }

    @Test
    void aBlastBegunSixteenAndAHalfBlocksOutStandsInsideTheSlack() {
        assertTrue(GooChargedCastHandler.reaches(EYE, Optional.of(EYE), EYE.add(0, 0, 16.5), blast()));
    }

    @Test
    void aBlastBegunFifteenBlocksOutStandsReleasedThirtyBlocksOut() {
        Vec3 thirtyOut = FIFTEEN_OUT.add(0, 0, 30);

        assertTrue(GooChargedCastHandler.reaches(thirtyOut, Optional.of(EYE), FIFTEEN_OUT, blast()));
    }

    @Test
    void aBlastRootedNowhereIsMeasuredFromTheEyeAtRelease() {
        assertFalse(GooChargedCastHandler.reaches(EYE, Optional.empty(), EIGHTEEN_OUT, blast()));
    }

    private static AbilityDefinition blast() {
        return AbilityJson.decode("unstable_blast");
    }
}
