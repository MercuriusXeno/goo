package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Click's rings stand square to the wave's line where each was set, and each
 * leaves once it has faded (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
class ClickRingsTest {

    private static final double EPSILON = 1e-5;
    private static final Vec3 ALONG = new Vec3(-22.02, 1.7, 22.09);

    @AfterEach
    void dropTheRings() {
        ClickRings.clear();
    }

    @Test
    void eachCornerOfARingStandsSquareToItsLine() {
        float size = 0.5f;
        Vector3f unit = ALONG.normalize().toVector3f();
        List<Vector3f> corners = ClickRings.corners(ClickRings.facingAlong(ALONG), size);
        assertAll(corners.stream().map(corner -> () -> assertAll(
                () -> assertEquals(0, corner.dot(unit), EPSILON, corner.toString()),
                () -> assertEquals(size * Math.sqrt(2), corner.length(), EPSILON, corner.toString()))));
    }

    /**
     * Each ring is tinted the red Zap's bolt glow and Tick's beat rings
     * draw in, whatever its fade.
     * decision click-draws-in-pulse-red-not-the-shrieker-visual
     */
    @Test
    void eachRingIsTintedPulseRed() {
        int pulseRed = 0xE0301E;
        assertAll(
                () -> assertEquals(pulseRed, ZapBolts.GLOW_RGB),
                () -> assertEquals(pulseRed, BeatRings.RING_RGB),
                () -> assertEquals(pulseRed, ARGB.color(0, ClickRings.ringColor(0f))),
                () -> assertEquals(pulseRed, ARGB.color(0, ClickRings.ringColor(0.5f))));
    }

    @Test
    void aRingStandsUntilItHasFadedThenLeaves() {
        long set = 100;
        ClickRings.set(new ClickRings.Ring(Vec3.ZERO, ALONG, set));
        assertAll(
                () -> assertEquals(1, ClickRings.standingAt(set + ClickRings.LIFETIME_TICKS - 1).size()),
                () -> assertEquals(0, ClickRings.standingAt(set + ClickRings.LIFETIME_TICKS).size()));
    }
}
