package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A full stack hovers over the hand at full size: every knife of a full
 * pyramid and every javelin of a full triangle draws at its thrown size, its
 * butt or tail on its slot and its length running forward at the crosshair's
 * point, all of it in front of the eye. Every javelin and every knife's point
 * stand inside a 16:9 view at vanilla's default 70 degree field of view; the
 * top row's outer knife butts, a blade's width apart over a palm at the right
 * of the view, may run past its right edge.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
class StackInViewTest {

    private static final Vec3 EYE = new Vec3(4.5, 66.62, -1.5);
    private static final float PITCH = 10f;
    private static final float YAW = -40f;
    /** Blocks along the look the crosshair rests at. */
    private static final double CROSSHAIR_AHEAD = 20;
    private static final int CASTER = 3;
    /** Vanilla's near clip plane, in blocks ahead of the eye. */
    private static final double NEAR = 0.05;
    private static final double HALF_FOV_TANGENT = Math.tan(Math.toRadians(35));
    private static final double ASPECT = 16.0 / 9.0;
    private static final double SETTLED = HoveringKnives.EASE_TICKS;
    private static final double WITHIN = 1e-6;

    @AfterEach
    void clearTheStacks() {
        HoveringKnives.clear();
        HeldJavelins.clear();
    }

    private static Vec3 crosshair() {
        return EYE.add(Vec3.directionFromRotation(PITCH, YAW).scale(CROSSHAIR_AHEAD));
    }

    private static void assertInFront(Vec3 point, String what) {
        double depth = point.subtract(EYE).dot(Vec3.directionFromRotation(PITCH, YAW));
        assertTrue(depth > NEAR, what + " stands " + depth + " ahead of the eye, behind the near plane");
    }

    private static void assertInView(Vec3 point, String what) {
        Vec3 ahead = Vec3.directionFromRotation(PITCH, YAW);
        Vec3 up = Vec3.directionFromRotation(PITCH - 90f, YAW);
        Vec3 right = ahead.cross(up);
        Vec3 from = point.subtract(EYE);
        double depth = from.dot(ahead);
        assertTrue(depth > NEAR, what + " stands " + depth + " ahead of the eye, behind the near plane");
        double across = from.dot(right) / (depth * HALF_FOV_TANGENT * ASPECT);
        double upward = from.dot(up) / (depth * HALF_FOV_TANGENT);
        assertTrue(Math.abs(across) <= 1 && Math.abs(upward) <= 1,
                what + " projects to (" + across + ", " + upward + "), off the screen");
    }

    @Test
    void everyKnifeOfAFullPyramidHoversAtFullSizeItsPointInViewAndItsButtInFront() {
        Vec3 cube = StackHover.cubeCenterInView(EYE, PITCH, YAW, true, false);
        HoveringKnives.formed(CASTER, StackHover.KNIVES, 0);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);
        for (int index = 0; index < StackHover.KNIVES; index++) {
            Vec3 butt = HoveringKnives.knifeAt(fan, index, cube, PITCH, YAW, SETTLED);
            Vec3 heading = ThrownKnife.headingFrom(butt, crosshair());
            float scale = HoveringKnives.knifeDrawnScaleAt(fan, index, SETTLED);
            Vec3 point = ThrownKnife.drawnFromOf(butt, heading, false, scale);
            assertEquals(1f, scale, WITHIN, "knife " + index + " should hover at its thrown size");
            assertEquals(0, point.subtract(butt).subtract(heading.scale(StackHover.KNIFE_LENGTH)).length(), WITHIN,
                    "knife " + index + "'s point should stand its length ahead of its butt, at the crosshair");
            assertInFront(butt, "knife " + index + "'s butt");
            assertInView(point, "knife " + index + "'s point");
        }
    }

    @Test
    void everyJavelinOfAFullTriangleHoversWholeAtFullSizePointingAtTheCrosshair() {
        Vec3 cube = StackHover.cubeCenterInView(EYE, PITCH, YAW, true, true);
        HeldJavelins.formed(CASTER, StackHover.JAVELINS, 0);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);
        for (int index = 0; index < StackHover.JAVELINS; index++) {
            Vec3 tail = HeldJavelins.javelinAt(stack, index, cube, PITCH, YAW, SETTLED);
            Vec3 heading = ThrownKnife.headingFrom(tail, crosshair());
            float scale = HeldJavelins.javelinDrawnScaleAt(stack, index, SETTLED);
            Vec3 middle = ThrownKnife.drawnFromOf(tail, heading, true, scale);
            assertEquals(1f, scale, WITHIN, "javelin " + index + " should hover at its thrown size");
            assertEquals(0, middle.subtract(tail).subtract(heading.scale(TravelForm.DART.fins().sweep())).length(),
                    WITHIN, "javelin " + index + "'s fins should end at its slot, the javelin running forward");
            assertInView(tail, "javelin " + index + "'s tail");
            assertInView(middle.add(heading.scale(TravelForm.DART.frontLength())), "javelin " + index + "'s point");
        }
    }

    @Test
    void theKnifeTheClientDrawsIsAsLongAsTheStackMeasuresIt() {
        assertEquals(StackHover.KNIFE_LENGTH, GlassKunai.WHOLE_LENGTH, WITHIN);
    }
}
