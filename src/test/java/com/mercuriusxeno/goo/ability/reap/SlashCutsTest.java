package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which bodies a Reap slash cuts: a body its path crosses, within the
 * blade's half width, and no body off it; and the cadence of its cuts, each
 * slash cutting a body once and a body cut keeping off every slash until its
 * invulnerability lapses.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class SlashCutsTest {

    private static final Vec3 FROM = new Vec3(0, 1.1, 0.5);
    private static final Vec3 TO = new Vec3(6, 1.1, 0.5);
    private static final int TARGET = 7;
    private static final int OTHER = 8;
    private static final int WINDOW = 4;
    private static final long NOW = 100;

    /** A mob-sized body standing with its feet at a point. */
    private static AABB bodyAt(double x, double z) {
        return new AABB(x - 0.3, 0, z - 0.3, x + 0.3, 1.8, z + 0.3);
    }

    @Nested
    class Crossing {

        @Test
        void aBodyOnThePathIsCut() {
            assertTrue(SlashCuts.crosses(bodyAt(3, 0.5), FROM, TO));
        }

        @Test
        void aBodyBesideThePathWithinTheBladeIsCut() {
            assertTrue(SlashCuts.crosses(bodyAt(3, 1.2), FROM, TO));
        }

        @Test
        void aBodyOffThePathIsNotCut() {
            assertFalse(SlashCuts.crosses(bodyAt(3, 3), FROM, TO));
            assertFalse(SlashCuts.crosses(bodyAt(9, 0.5), FROM, TO));
        }

        @Test
        void aBodyTheSlashLaunchesInsideIsCut() {
            assertTrue(SlashCuts.crosses(bodyAt(0, 0.5), FROM, FROM));
        }
    }

    @Nested
    class Cadence {

        private final SlashCuts.Cooldowns cooldowns = new SlashCuts.Cooldowns();

        @Test
        void oneSlashCutsABodyOnceEvenAfterItsWindowLapses() {
            SlashCuts.Strikes slash = new SlashCuts.Strikes();
            assertTrue(slash.claim(TARGET, NOW, WINDOW, cooldowns));
            assertFalse(slash.claim(TARGET, NOW + WINDOW * 3, WINDOW, cooldowns));
        }

        @Test
        void aSecondSlashInsideTheWindowCutsNothing() {
            new SlashCuts.Strikes().claim(TARGET, NOW, WINDOW, cooldowns);
            assertFalse(new SlashCuts.Strikes().claim(TARGET, NOW + WINDOW - 1, WINDOW, cooldowns));
        }

        @Test
        void aSecondSlashOnceTheWindowLapsesCutsAgain() {
            new SlashCuts.Strikes().claim(TARGET, NOW, WINDOW, cooldowns);
            assertTrue(new SlashCuts.Strikes().claim(TARGET, NOW + WINDOW, WINDOW, cooldowns));
        }

        @Test
        void aCutKeepsOnlyItsOwnBodyOff() {
            new SlashCuts.Strikes().claim(TARGET, NOW, WINDOW, cooldowns);
            assertTrue(new SlashCuts.Strikes().claim(OTHER, NOW + 1, WINDOW, cooldowns));
        }
    }
}
