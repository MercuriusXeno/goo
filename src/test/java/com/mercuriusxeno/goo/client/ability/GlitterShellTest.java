package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glitter's sparkle shell: its glints lie on the front's radius spread over
 * the whole sphere, the front grows to its reach and the shell fades after,
 * and its glints mix diamond-white with rainbow hues
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class GlitterShellTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void glintsLieOnTheFrontsRadiusAndReachEveryOctant() {
        List<Vec3> directions = GlitterShell.glintDirections(GlitterShell.GLINTS, 7L);
        double radius = GlitterShell.frontRadius(10, 1.5, 24);

        assertEquals(15, radius, TOLERANCE);
        boolean[] octants = new boolean[8];
        for (Vec3 direction : directions) {
            assertEquals(radius, direction.scale(radius).length(), TOLERANCE);
            octants[(direction.x > 0 ? 1 : 0) + (direction.y > 0 ? 2 : 0) + (direction.z > 0 ? 4 : 0)] = true;
        }
        for (boolean reached : octants) {
            assertTrue(reached);
        }
    }

    @Test
    void theFrontStopsAtItsReachAndTheShellFadesAfter() {
        assertEquals(24, GlitterShell.frontRadius(40, 1, 24), TOLERANCE);
        assertEquals(1f, GlitterShell.strength(24, 1, 24), 1e-6f);
        assertEquals(0.5f, GlitterShell.strength(24 + GlitterShell.FADE_TICKS / 2, 1, 24), 1e-6f);
        assertEquals(0f, GlitterShell.strength(24 + GlitterShell.FADE_TICKS, 1, 24), 1e-6f);
    }

    @Test
    void everyThirdGlintIsWhiteAndTheRestTakeHues() {
        assertEquals(0xFFFFFFFF, GlitterShell.glintColor(0, 1f));
        assertNotEquals(GlitterShell.glintColor(1, 1f), GlitterShell.glintColor(2, 1f));
        assertNotEquals(0xFFFFFFFF, GlitterShell.glintColor(1, 1f));
        assertEquals(0, GlitterShell.glintColor(3, 0f) >>> 24);
    }
}
