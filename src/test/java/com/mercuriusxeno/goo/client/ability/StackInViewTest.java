package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A full stack hovers whole in the view over the hand: every knife of a full
 * pyramid and every javelin of a full triangle, drawn end to end at the size
 * it hovers at, stands in front of the eye and inside a 16:9 view at vanilla's
 * default 70 degree field of view, so none runs back past the camera or off the screen.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
class StackInViewTest {

    private static final Vec3 EYE = new Vec3(4.5, 66.62, -1.5);
    private static final float PITCH = 10f;
    private static final float YAW = -40f;
    private static final int CASTER = 3;
    /** Vanilla's near clip plane, in blocks ahead of the eye. */
    private static final double NEAR = 0.05;
    private static final double HALF_FOV_TANGENT = Math.tan(Math.toRadians(35));
    private static final double ASPECT = 16.0 / 9.0;
    private static final double SETTLED = HoveringKnives.EASE_TICKS;

    @AfterEach
    void clearTheStacks() {
        HoveringKnives.clear();
        HeldJavelins.clear();
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
    void everyKnifeOfAFullPyramidHoversWholeInTheView() {
        Vec3 cube = StackHover.cubeCenterInView(EYE, PITCH, YAW, true);
        Vec3 heading = Vec3.directionFromRotation(PITCH, YAW);
        HoveringKnives.formed(CASTER, StackHover.KNIVES, 0);
        HoveringKnives.Fan fan = HoveringKnives.fanOf(CASTER);
        for (int index = 0; index < StackHover.KNIVES; index++) {
            Vec3 point = HoveringKnives.knifePointAt(fan, index, cube, PITCH, YAW, SETTLED);
            double length = GlassKunai.WHOLE_LENGTH * HoveringKnives.knifeDrawnScaleAt(fan, index, SETTLED);
            assertInView(point, "knife " + index + "'s point");
            assertInView(point.subtract(heading.scale(length)), "knife " + index + "'s butt");
        }
    }

    @Test
    void everyJavelinOfAFullTriangleHoversWholeInTheView() {
        Vec3 cube = StackHover.cubeCenterInView(EYE, PITCH, YAW, true);
        Vec3 heading = HeldJavelins.headingOf(PITCH, YAW);
        HeldJavelins.formed(CASTER, StackHover.JAVELINS, 0);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);
        for (int index = 0; index < StackHover.JAVELINS; index++) {
            Vec3 middle = HeldJavelins.javelinAt(stack, index, cube, PITCH, YAW, SETTLED);
            double half = TravelForm.DART.frontLength() * HeldJavelins.javelinDrawnScaleAt(stack, index, SETTLED) / 2;
            assertInView(middle.add(heading.scale(half)), "javelin " + index + "'s point");
            assertInView(middle.subtract(heading.scale(half)), "javelin " + index + "'s tail");
        }
    }
}
