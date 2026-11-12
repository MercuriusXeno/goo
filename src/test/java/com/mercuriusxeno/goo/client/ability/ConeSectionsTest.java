package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cone section's frame turns with the look's yaw alone, so the shader's
 * pattern holds steady as the look pitches up or down, all the way to
 * straight up and straight down (decision growth-breeze-ticks-plants).
 */
class ConeSectionsTest {

    private static final float YAW = 37f;
    /** Minecraft turns a rotation into a look in floats. */
    private static final double EPSILON = 1e-6;

    private static Vec3 look(float pitchDegrees, float yawDegrees) {
        return Vec3.directionFromRotation(pitchDegrees, yawDegrees);
    }

    @ParameterizedTest
    @ValueSource(floats = {-90f, -89.9f, -85f, -81f, 0f, 45f, 81f, 85f, 89.9f, 90f})
    void theSideStaysWhereTheLevelLookPutsItAtEveryPitch(float pitch) {
        Vec3 level = ConeSections.sectionSide(look(0f, YAW), YAW);
        Vec3 pitched = ConeSections.sectionSide(look(pitch, YAW), YAW);
        assertEquals(0, pitched.distanceTo(level), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(floats = {-90f, -60f, 0f, 60f, 90f})
    void theSideLiesSquareToTheLook(float pitch) {
        Vec3 axis = look(pitch, YAW);
        assertEquals(0, ConeSections.sectionSide(axis, YAW).dot(axis), EPSILON);
    }
}
