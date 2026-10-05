package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.GhostTrails.GHOSTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ghost trail packs its ghosts at both ends of the jump and spreads them
 * across the middle, shows them all at once and fades them from the source
 * end first (decision ghost-trail-spans-the-blink).
 */
class GhostTrailsTest {

    private static final long LANDED = 300;
    private static final int LIFE = 20;
    private static final int ENDER = 0x2A9D8F;
    private static final Vec3 SOURCE = new Vec3(0, 64, 0);
    private static final Vec3 DESTINATION = new Vec3(8, 64, 0);
    private static final float EPSILON = 1e-5f;

    private static GhostTrails.GhostTrail<String> blink() {
        return new GhostTrails.GhostTrail<>("player", SOURCE, DESTINATION, ENDER, LANDED, LIFE);
    }

    @Nested
    class Spacing {

        @Test
        void fractionsRiseFromTheSourceToTheDestination() {
            float[] fractions = GhostTrails.fractions(GHOSTS);

            assertEquals(0f, fractions[0], EPSILON);
            assertEquals(1f, fractions[GHOSTS - 1], EPSILON);
            for (int ghost = 1; ghost < GHOSTS; ghost++) {
                assertTrue(fractions[ghost] > fractions[ghost - 1], "not rising at " + ghost);
            }
        }

        @Test
        void ghostsPackAtBothEndsAndSpreadAcrossTheMiddle() {
            float[] fractions = GhostTrails.fractions(GHOSTS);
            float firstGap = fractions[1] - fractions[0];
            float lastGap = fractions[GHOSTS - 1] - fractions[GHOSTS - 2];
            float middleGap = fractions[GHOSTS / 2] - fractions[GHOSTS / 2 - 1];

            assertTrue(firstGap < middleGap, "the source end is not denser than the middle");
            assertTrue(lastGap < middleGap, "the destination end is not denser than the middle");
            for (int ghost = 1; ghost < GHOSTS / 2; ghost++) {
                assertTrue(fractions[ghost] - fractions[ghost - 1] < fractions[ghost + 1] - fractions[ghost],
                        "the gaps do not widen toward the middle at " + ghost);
            }
        }

        @Test
        void theLastGhostStandsOnThePlayer() {
            List<GhostTrails.Ghost> ghosts = blink().ghosts(LANDED);

            assertEquals(DESTINATION, ghosts.getLast().position());
            assertEquals(SOURCE, ghosts.getFirst().position());
        }
    }

    @Nested
    class Fade {

        @Test
        void everyGhostAppearsTheTickTheTrailLands() {
            List<GhostTrails.Ghost> ghosts = blink().ghosts(LANDED);

            assertEquals(GHOSTS, ghosts.size());
            int fresh = Math.round(GhostTrails.FRESH_OPACITY * 0xFF);
            ghosts.forEach(ghost -> assertEquals(fresh, ghost.alpha()));
        }

        @Test
        void theSourceEndFadesFirstAtEveryMidLifeTime() {
            for (int age = 1; age < LIFE; age++) {
                int sourceEnd = GhostTrails.alpha(0f, age, LIFE);
                int destinationEnd = GhostTrails.alpha(1f, age, LIFE);
                assertTrue(sourceEnd < destinationEnd, "the source end is not fainter at age " + age);
            }
        }

        @Test
        void theTrailCollapsesTowardThePlayer() {
            List<GhostTrails.Ghost> ghosts = blink().ghosts(LANDED + LIFE * 0.75f);

            assertTrue(ghosts.size() < GHOSTS, "no ghost has faded by three quarters of the life");
            assertEquals(DESTINATION, ghosts.getLast().position());
            assertTrue(ghosts.getFirst().position().x > SOURCE.x, "the source ghost still shows");
        }

        @Test
        void everyGhostHasFadedAtTheEndOfTheLife() {
            assertTrue(blink().ghosts(LANDED + LIFE).isEmpty());
        }
    }

    @Nested
    class Life {

        @Test
        void aTrailIsDroppedOnceItHasFaded() {
            GhostTrails<String> trails = new GhostTrails<>();
            trails.add("player", SOURCE, DESTINATION, ENDER, LANDED, LIFE);

            assertEquals(1, trails.live(LANDED + LIFE - 1).size());
            assertTrue(trails.live(LANDED + LIFE).isEmpty());
        }

        @Test
        void clearDropsEveryTrail() {
            GhostTrails<String> trails = new GhostTrails<>();
            trails.add("player", SOURCE, DESTINATION, ENDER, LANDED, LIFE);

            trails.clear();

            assertTrue(trails.live(LANDED).isEmpty());
        }
    }

    @Test
    void ghostsDrawThroughTheirOwnShaderPair() {
        assertEquals("core/goo_ghost", GooRenderTypes.GOO_GHOST.getVertexShader().getPath());
        PipelineShaders.assertExist(GooRenderTypes.GOO_GHOST);
    }
}
