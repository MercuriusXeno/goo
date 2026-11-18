package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ThrowRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Spire footprint corner stands within Spire's own range of the eye
 * (decision each-ability-names-its-range-and-the-shared-throw-range-retires).
 */
class GooSpireHandlerTest {

    private static final Vec3 EYE = new Vec3(0.5, 64.5, 0.5);

    @Test
    void aCornerSeventeenBlocksOutIsRefused() {
        assertFalse(GooSpireHandler.inRange(EYE, new BlockPos(0, 64, 17), spireRange()));
    }

    @Test
    void aCornerFifteenBlocksOutStands() {
        assertTrue(GooSpireHandler.inRange(EYE, new BlockPos(0, 64, 15), spireRange()));
    }

    private static double spireRange() {
        return ThrowRange.of(AbilityJson.decode("rock_spire"));
    }
}
