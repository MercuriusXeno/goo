package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slick's ramp scales the horizontal velocity of a thing on it by the factor
 * kinetic_slick.json names, leaves its vertical velocity, and holds it to the
 * JSON's top speed.
 * slick-coats-the-ground-with-kinetic-speed
 */
class SlickRampTest {

    private static final double EXACT = 1e-9;
    private static final Vec3 SLIDING = new Vec3(0.3, -0.2, -0.1);

    private static SlickStep shippedSlick() {
        return AbilityJson.decode("kinetic_slick").behaviors().stream().filter(SlickStep.class::isInstance)
                .map(SlickStep.class::cast).findFirst().orElseThrow();
    }

    @Test
    void scalesHorizontalVelocityByTheJsonFactorAndLeavesVertical() {
        SlickStep slick = shippedSlick();

        Vec3 ramped = SlickRamp.ramped(SLIDING, slick.ramp(), slick.topSpeed());

        assertEquals(SLIDING.x * slick.ramp(), ramped.x, EXACT);
        assertEquals(SLIDING.z * slick.ramp(), ramped.z, EXACT);
        assertEquals(SLIDING.y, ramped.y, EXACT);
    }

    @Test
    void holdsAThingPastTopSpeedToTopSpeedAlongItsHeading() {
        SlickStep slick = shippedSlick();
        Vec3 racing = new Vec3(slick.topSpeed() * 3, 0.1, slick.topSpeed() * 4);

        Vec3 ramped = SlickRamp.ramped(racing, slick.ramp(), slick.topSpeed());

        assertEquals(slick.topSpeed(), ramped.horizontalDistance(), EXACT);
        assertEquals(3.0 / 4.0, ramped.x / ramped.z, EXACT);
        assertEquals(racing.y, ramped.y, EXACT);
    }
}
