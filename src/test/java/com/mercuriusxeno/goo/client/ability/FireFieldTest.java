package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ability.FireField.Wall;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Held Flames draws through the fire shader pass's
 * field, its licks and embers poured down the cone from the spout to the
 * reach, cut off at the face the look meets (decision
 * flames-draws-as-particle-style-fire), the held fire sized by the cone it is
 * handed (decision spray-abilities-render-procedurally-bound-to-their-cone).
 */
class FireFieldTest {

    private static final double RANGE = 6;
    private static final double CONE_DEGREES = 20;
    private static final double DELTA = 1e-9;
    private static final float FLOAT_DELTA = 1e-6f;
    private static final Vec3 CAMERA = new Vec3(10, 64, -4);
    private static final Vec3 HAND = new Vec3(10.4, 63.6, -3.8);
    private static final Vec3 AXIS = new Vec3(1, -0.2, 0.5).normalize();
    private static final FireStream.JetSpan FULL = new FireStream.JetSpan(0, RANGE);
    private static final double TAN = Math.tan(Math.toRadians(CONE_DEGREES / 2));
    private static final SprayCone ROUND = new SprayCone(HAND, AXIS, ConeShape.round(CONE_DEGREES), RANGE);

    @Test
    void heldFlamesDrawsThroughTheFireFieldFromTheConesApexAtItsReachAndCone() {
        FireField field = FireStream.fieldOf(ROUND, FULL, null, CAMERA);
        assertSpout(field, HAND.subtract(CAMERA), RANGE);
    }

    @Test
    void theFireReachesNoFartherThanTheFaceTheLookMeets() {
        Wall wall = new Wall(3, HAND.add(AXIS.scale(3)), Direction.WEST);
        assertEquals(3, FireStream.fieldOf(ROUND, FULL, wall, CAMERA).reach(), DELTA);
    }

    @Test
    void nothingBurnsOnceTheTailHasCaughtTheFront() {
        FireStream.JetSpan spent = new FireStream.JetSpan(RANGE, RANGE);
        assertTrue(FireStream.fieldOf(ROUND, FULL, null, CAMERA).burns());
        assertTrue(!FireStream.fieldOf(ROUND, spent, null, CAMERA).burns());
    }

    /**
     * A cone wider across than up and down sizes the fire by each of its widths
     * (decision spray-abilities-render-procedurally-bound-to-their-cone).
     */
    @Nested
    class OfAFlatCone {

        private static final double ACROSS_DEGREES = 50;
        private static final double UP_DOWN_DEGREES = 10;
        private static final double REACH = 8;
        private static final SprayCone FLAT = new SprayCone(HAND, AXIS,
                new ConeShape(ACROSS_DEGREES, UP_DOWN_DEGREES, false), REACH);
        private static final FireStream.JetSpan SPAN = new FireStream.JetSpan(0, REACH);
        /** Floats into the uniform block of ApexSideTan's w, AxisReach's w and BoxHighLiftTan's w. */
        private static final int SIDE_TAN_FLOAT = 3;
        private static final int REACH_FLOAT = 7;
        private static final int LIFT_TAN_FLOAT = 27;

        private final FireField field = FireStream.fieldOf(FLAT, SPAN, null, CAMERA);

        @Test
        void theUniformBlockPacksTheTangentOfHalfOfEachWidthAndTheReach() {
            ByteBuffer packed = ByteBuffer.allocate(FireField.BYTES).order(ByteOrder.nativeOrder());
            field.pack(packed, CAMERA, true);

            assertEquals((float) Math.tan(Math.toRadians(ACROSS_DEGREES / 2)),
                    packed.getFloat(SIDE_TAN_FLOAT * Float.BYTES), FLOAT_DELTA);
            assertEquals((float) Math.tan(Math.toRadians(UP_DOWN_DEGREES / 2)),
                    packed.getFloat(LIFT_TAN_FLOAT * Float.BYTES), FLOAT_DELTA);
            assertEquals((float) REACH, packed.getFloat(REACH_FLOAT * Float.BYTES), FLOAT_DELTA);
        }

        @Test
        void theBoxHoldsTheConesRimAcrossAndUpAndDownOutToItsReach() {
            Vec3 apex = HAND.subtract(CAMERA);
            Vec3 side = FireField.sideOf(AXIS);
            Vec3 lift = side.cross(AXIS);
            Vec3 end = apex.add(AXIS.scale(REACH));
            Vec3 across = side.scale(REACH * Math.tan(Math.toRadians(ACROSS_DEGREES / 2)));
            Vec3 upDown = lift.scale(REACH * Math.tan(Math.toRadians(UP_DOWN_DEGREES / 2)));
            assertInBox(field.box(), apex, end, end.add(across), end.subtract(across), end.add(upDown),
                    end.subtract(upDown));
        }
    }

    @Test
    void theBoxHoldsTheConeOutToItsReach() {
        Vec3 apex = HAND.subtract(CAMERA);
        Vec3 rim = apex.add(AXIS.scale(RANGE)).add(new Vec3(0, RANGE * TAN, 0));
        assertInBox(FireStream.fieldOf(ROUND, FULL, null, CAMERA).box(), apex, apex.add(AXIS.scale(RANGE)), rim);
    }

    private static void assertInBox(Vec3[] box, Vec3... points) {
        for (Vec3 point : points) {
            assertTrue(point.x >= box[0].x && point.y >= box[0].y && point.z >= box[0].z, "below the box: " + point);
            assertTrue(point.x <= box[1].x && point.y <= box[1].y && point.z <= box[1].z, "above the box: " + point);
        }
    }

    private static void assertSpout(FireField field, Vec3 apex, double reach) {
        assertEquals(apex, field.apex());
        assertEquals(AXIS, field.axis());
        assertEquals(TAN, field.sideTan(), DELTA);
        assertEquals(TAN, field.liftTan(), DELTA);
        assertEquals(reach, field.reach(), DELTA);
    }
}
