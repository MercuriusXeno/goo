package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbilityImpact's landing rules: where a landing's program anchors, and
 * which landings turn into their own block.
 */
class AbilityImpactTest {

    private static final BlockPos CELL = new BlockPos(3, 64, -1);

    /** A free ability's program anchors at the aimed point, not the cell's center. */
    // decision aim-point-follows-the-cursor
    @Test
    void anAimedPointAnchorsTheProgramThere() {
        Vec3 point = new Vec3(3.2, 64.9, -0.9);

        assertEquals(point, AbilityImpact.anchor(CELL, point));
    }

    // decision aim-point-follows-the-cursor
    @Test
    void noAimedPointAnchorsTheProgramAtTheCellsCenter() {
        assertEquals(new Vec3(3.5, 64.5, -0.5), AbilityImpact.anchor(CELL, null));
    }

    /**
     * Ignite's blob turns into the fire it lights, the fire standing in for its burnout.
     */
    // idea ignite-plays-no-burnout
    @Test
    void ignitesBlobTurnsIntoItsFire() {
        assertTrue(AbilityImpact.lightsItsFire(AbilityJson.decode("blaze_ignite")));
    }
}
