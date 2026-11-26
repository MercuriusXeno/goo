package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The glove builds a spray's cone input from the ability's Delivery: the round cone of its cone degrees, out to
 * its range (decision spray-abilities-render-procedurally-bound-to-their-cone).
 */
class SprayConeTest {

    private static final Vec3 APEX = new Vec3(3.3, 65.2, -1.4);
    private static final Vec3 AXIS = new Vec3(0.2, -0.4, 1).normalize();

    @Test
    void theGloveBuildsTheRoundConeOfTheDeliveryOutToItsRange() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, 7.5, 24, 0, false, Optional.empty(), 0);

        SprayCone cone = SprayCone.ofGlove(delivery, APEX, AXIS);

        assertEquals(ConeShape.round(24), cone.shape());
        assertEquals(7.5, cone.reach());
        assertEquals(APEX, cone.apex());
        assertEquals(AXIS, cone.axis());
    }
}
