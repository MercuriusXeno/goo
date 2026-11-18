package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A charged cast reaches as far as its ability's own range
 * (decision each-ability-names-its-range-and-the-shared-throw-range-retires).
 */
class GooChargedCastHandlerTest {

    private static final Vec3 EYE = new Vec3(3, 70.62, -8);

    @Test
    void aBlastReleasedSeventeenBlocksOutIsRefused() {
        assertFalse(GooChargedCastHandler.reaches(EYE, EYE.add(0, -8, 15), blast()));
    }

    @Test
    void aBlastReleasedFifteenBlocksOutStands() {
        assertTrue(GooChargedCastHandler.reaches(EYE, EYE.add(0, -9, 12), blast()));
    }

    private static AbilityDefinition blast() {
        return AbilityJson.decode("unstable_blast");
    }
}
