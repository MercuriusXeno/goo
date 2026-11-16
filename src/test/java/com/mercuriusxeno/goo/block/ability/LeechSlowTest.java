package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Leech goo's soul sand slow: a caught thing's horizontal motion
 * scales by the speed factor hex_leech.json ships, its vertical motion kept.
 * leech-goo-slows-the-way-soul-sand-does
 */
class LeechSlowTest {

    private static final double SPEED_FACTOR = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
            .orElseThrow().speedFactor();
    private static final Vec3 MOTION = new Vec3(0.3, -0.08, -0.2);

    @Test
    void horizontalMotionScalesByTheSpeedFactorAndVerticalMotionStays() {
        Vec3 slowed = LeechSlow.slowed(MOTION, SPEED_FACTOR);

        assertEquals(new Vec3(MOTION.x * SPEED_FACTOR, MOTION.y, MOTION.z * SPEED_FACTOR), slowed);
    }
}
