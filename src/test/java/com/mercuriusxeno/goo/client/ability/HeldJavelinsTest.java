package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dart's stacked javelins on the client: each forming javelin spawns at the
 * cube's top and rises to its slot, the javelins hover side by side at one
 * height pointing along the look, and a javelin formed before the change
 * stands at its slot (decision dart-javelins-hover-over-the-shrinking-blob-cube).
 */
class HeldJavelinsTest {

    private static final int CASTER = 9;
    /** The lifted blob cube's center. */
    private static final Vec3 CUBE = new Vec3(1.2, 70.4, -3.1);
    private static final float PITCH = -15f;
    private static final float YAW = 120f;
    private static final double WITHIN = 1e-9;
    /** Slack for the float rounding a view's directions carry. */
    private static final double FLOAT_WITHIN = 1e-6;

    @AfterEach
    void clearTheStacks() {
        HeldJavelins.clear();
    }

    @Test
    void aFormingJavelinStartsAtTheCubesTopAndRisesToItsSlot() {
        HeldJavelins.formed(CASTER, 1, 0);
        HeldJavelins.formed(CASTER, 2, 10);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);

        Vec3 top = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.spawnSlot(1, StackHover.JAVELINS));
        assertEquals(0, top.distanceTo(HeldJavelins.javelinAt(stack, 1, CUBE, PITCH, YAW, 0)), WITHIN);
        Vec3 slot = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(1));
        assertEquals(0, slot.distanceTo(HeldJavelins.javelinAt(stack, 1, CUBE, PITCH, YAW,
                HeldJavelins.EASE_TICKS)), WITHIN);
        Vec3 first = StackHover.pointOf(CUBE, PITCH, YAW, StackHover.javelinSlot(0));
        assertEquals(0, first.distanceTo(HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW, 0)), WITHIN,
                "a javelin formed before the change stands at its slot");
    }

    @Test
    void theJavelinsHoverSideBySideAtOneHeightPointingAlongTheLook() {
        HeldJavelins.formed(CASTER, 3, 0);
        HeldJavelins.Stack stack = HeldJavelins.stackOf(CASTER);
        Vec3 look = Vec3.directionFromRotation(PITCH, YAW);
        Vec3 up = Vec3.directionFromRotation(PITCH - 90f, YAW);

        double height = HeldJavelins.javelinAt(stack, 0, CUBE, PITCH, YAW, HeldJavelins.EASE_TICKS)
                .subtract(CUBE).dot(up);
        for (int javelin = 0; javelin < StackHover.JAVELINS; javelin++) {
            Vec3 at = HeldJavelins.javelinAt(stack, javelin, CUBE, PITCH, YAW, HeldJavelins.EASE_TICKS);
            assertEquals(height, at.subtract(CUBE).dot(up), FLOAT_WITHIN, "the javelins share one height");
            assertTrue(height > StackHover.cubeTopAt(0, StackHover.JAVELINS), "above the cube's top");
            assertEquals(0, look.distanceTo(HeldJavelins.headingOf(PITCH, YAW)), FLOAT_WITHIN);
        }
    }
}
