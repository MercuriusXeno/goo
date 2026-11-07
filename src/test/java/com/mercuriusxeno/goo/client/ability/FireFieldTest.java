package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Held Flames and a heater prism both draw through the fire shader pass's
 * field, its licks and embers poured down the cone from the spout to the
 * reach, cut off at the face the look meets (decision
 * flames-draws-as-particle-style-fire).
 */
class FireFieldTest {

    private static final double RANGE = 6;
    private static final double CONE_DEGREES = 20;
    private static final double DELTA = 1e-9;
    private static final Vec3 CAMERA = new Vec3(10, 64, -4);
    private static final Vec3 HAND = new Vec3(10.4, 63.6, -3.8);
    private static final Vec3 AXIS = new Vec3(1, -0.2, 0.5).normalize();
    private static final FireStream.JetSpan FULL = new FireStream.JetSpan(0, RANGE);
    private static final double TAN = Math.tan(Math.toRadians(CONE_DEGREES / 2));

    @Test
    void heldFlamesDrawsThroughTheFireFieldFromTheGloveAtItsDeliveryReachAndCone() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, RANGE, CONE_DEGREES, 0, false, Optional.empty(), 0);
        FireField field = FireStream.fieldOf(delivery, HAND, AXIS, FULL, null, CAMERA);
        assertSpout(field, HAND.subtract(CAMERA), RANGE);
    }

    @Test
    void aHeaterDrawsFlamesOwnFireFieldFromItsTipAtItsTarget() {
        FireField field = HeaterFlames.fieldOf(new HeaterFlames.Heater(HAND, AXIS, 0, 0, Double.NaN), FULL, null,
                CAMERA);
        assertSpout(field, HAND.subtract(CAMERA), RANGE);
    }

    @Test
    void theFireReachesNoFartherThanTheFaceTheLookMeets() {
        Wall wall = new Wall(3, HAND.add(AXIS.scale(3)), Direction.WEST);
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, RANGE, CONE_DEGREES, 0, false, Optional.empty(), 0);
        assertEquals(3, FireStream.fieldOf(delivery, HAND, AXIS, FULL, wall, CAMERA).reach(), DELTA);
    }

    @Test
    void theBoxHoldsTheConeOutToItsReach() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, RANGE, CONE_DEGREES, 0, false, Optional.empty(), 0);
        FireField field = FireStream.fieldOf(delivery, HAND, AXIS, FULL, null, CAMERA);
        Vec3[] box = field.box();
        Vec3 apex = HAND.subtract(CAMERA);
        Vec3 rim = apex.add(AXIS.scale(RANGE)).add(new Vec3(0, RANGE * TAN, 0));
        for (Vec3 point : new Vec3[] {apex, apex.add(AXIS.scale(RANGE)), rim}) {
            assertTrue(point.x >= box[0].x && point.y >= box[0].y && point.z >= box[0].z, "below the box: " + point);
            assertTrue(point.x <= box[1].x && point.y <= box[1].y && point.z <= box[1].z, "above the box: " + point);
        }
    }

    @Test
    void nothingBurnsOnceTheTailHasCaughtTheFront() {
        Delivery delivery = new Delivery(DeliveryKind.STREAM, 0, RANGE, CONE_DEGREES, 0, false, Optional.empty(), 0);
        FireStream.JetSpan spent = new FireStream.JetSpan(RANGE, RANGE);
        assertTrue(FireStream.fieldOf(delivery, HAND, AXIS, FULL, null, CAMERA).burns());
        assertTrue(!FireStream.fieldOf(delivery, HAND, AXIS, spent, null, CAMERA).burns());
    }

    private static void assertSpout(FireField field, Vec3 apex, double reach) {
        assertEquals(apex, field.apex());
        assertEquals(AXIS, field.axis());
        assertEquals(TAN, field.tan(), DELTA);
        assertEquals(reach, field.reach(), DELTA);
    }
}
