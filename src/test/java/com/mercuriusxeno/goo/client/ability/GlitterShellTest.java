package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glitter's band of glitter: its glints scatter at random over the whole
 * sphere at varied depths about the front, flash dim and brief, drift
 * through the prism, and the front grows only while held, fading once the hold ends
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class GlitterShellTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void glintsScatterOverTheWholeSphereAtVariedDepthsAboutTheFront() {
        List<GlitterShell.Glint> glints = GlitterShell.glints(GlitterShell.GLINTS, 7L);

        boolean[] octants = new boolean[8];
        Set<Long> depths = new HashSet<>();
        for (GlitterShell.Glint glint : glints) {
            Vec3 direction = glint.direction();
            assertEquals(1, direction.length(), 1e-6);
            assertTrue(Math.abs(glint.depth() - 1) <= GlitterShell.DEPTH_SCATTER + TOLERANCE,
                    "every glint lies near the front, at " + glint.depth());
            depths.add(Math.round(glint.depth() * 1e4));
            octants[(direction.x > 0 ? 1 : 0) + (direction.y > 0 ? 2 : 0) + (direction.z > 0 ? 4 : 0)] = true;
        }
        for (boolean reached : octants) {
            assertTrue(reached);
        }
        assertTrue(depths.size() > GlitterShell.GLINTS / 2, "glints lie at many depths, not one shell");
    }

    @Test
    void aGlintIsMostlyDarkAndFlashesNoBrighterThanTheBrightest() {
        GlitterShell.Glint glint = new GlitterShell.Glint(new Vec3(0, 1, 0), 1, 0, 0.5, 0.3f, false);

        int dark = 0;
        for (int tick = 0; tick < 100; tick++) {
            float flash = GlitterShell.flashAt(glint, tick);
            assertTrue(flash >= 0f && flash <= GlitterShell.BRIGHTEST + 1e-6f);
            if (flash < GlitterShell.BRIGHTEST / 10) {
                dark++;
            }
        }
        assertTrue(dark > 60, "the glint is dark most of its twinkle, dark " + dark + " of 100");
        assertEquals(GlitterShell.BRIGHTEST, GlitterShell.flashAt(glint, Math.PI), 1e-5f);
    }

    @Test
    void aHuedGlintDriftsThroughThePrismAndAWhiteOneStaysWhite() {
        GlitterShell.Glint hued = new GlitterShell.Glint(new Vec3(0, 1, 0), 1, 0, 0.5, 0.3f, false);
        GlitterShell.Glint white = new GlitterShell.Glint(new Vec3(0, 1, 0), 1, 0, 0.5, 0.3f, true);

        assertNotEquals(GlitterShell.glintColor(hued, 0, 1f), GlitterShell.glintColor(hued, 10, 1f));
        assertEquals(0xFFFFFFFF, GlitterShell.glintColor(white, 10, 1f));
        assertEquals(0, GlitterShell.glintColor(hued, 0, 0f) >>> 24);
    }

    @Test
    void theFrontGrowsOnlyAsHeldTicksReportItAndTheBandFadesOnceTheyStop() {
        assertEquals(10.5, GlitterShell.frontAt(10, 0.5, 1, 64), TOLERANCE);
        assertEquals(11, GlitterShell.frontAt(10, 1, 1, 64), TOLERANCE);
        assertEquals(11, GlitterShell.frontAt(10, 30, 1, 64), TOLERANCE, "the front stops once no tick comes");
        assertEquals(64, GlitterShell.frontAt(64, 1, 1, 64), TOLERANCE);
        assertEquals(1f, GlitterShell.strengthAt(GlitterShell.HELD_GAP_TICKS), 1e-6f);
        assertEquals(0.5f, GlitterShell.strengthAt(GlitterShell.HELD_GAP_TICKS + GlitterShell.FADE_TICKS / 2), 1e-6f);
        assertEquals(0f, GlitterShell.strengthAt(GlitterShell.HELD_GAP_TICKS + GlitterShell.FADE_TICKS), 1e-6f);
    }
}
