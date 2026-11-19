package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.StitchStep;
import com.mercuriusxeno.goo.ability.program.VinesStep;
import com.mercuriusxeno.goo.ability.root.RootStrike;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the strands Stitch tangles a hostile in: fire on a tangled mob lands
 * at the multiple Vines' root gives, plain on a fire immune one, and fire ends
 * the tangle until the mob has been out of the goo past the linger.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 */
class TangledTest {

    private static final long NOW = 100L;
    private static final float FIRE_HIT = 4f;

    private static float stitchFireFactor() {
        return StitchStep.in(AbilityJson.decode("yarn_stitch").behaviors()).orElseThrow().fireFactor();
    }

    private static float vinesFireFactor() {
        return VinesStep.in(AbilityJson.decode("leaf_vines").behaviors()).orElseThrow().fireFactor();
    }

    private static Tangled tangled() {
        return Tangled.NONE.touched(stitchFireFactor(), NOW);
    }

    @Nested
    class Fire {

        @Test
        void aFireHitOnATangledMobLandsAtTheVinesRootsMultiple() {
            float vinesRoot = RootStrike.of(1, FIRE_HIT, true, vinesFireFactor(), 0).damage();

            assertEquals(vinesRoot, tangled().damageAt(NOW, FIRE_HIT, true, false));
        }

        @Test
        void aFireHitOnAFireImmuneTangledMobLandsPlain() {
            assertEquals(FIRE_HIT, tangled().damageAt(NOW, FIRE_HIT, true, true));
        }

        @Test
        void fireEndsTheTangleWhileTheMobStaysInTheGoo() {
            long later = NOW + Tangled.LINGER_TICKS - 1;

            assertFalse(tangled().burn().touched(stitchFireFactor(), later).holds(later));
        }

        @Test
        void aBurntMobIsTangledAgainOnceOutOfTheGooPastTheLinger() {
            long back = NOW + Tangled.LINGER_TICKS;

            assertTrue(tangled().burn().touched(stitchFireFactor(), back).holds(back));
        }
    }

    @Nested
    class Linger {

        @Test
        void aTouchWithTimeLeftKeepsTheStrandsAsTheyStand() {
            Tangled standing = tangled();

            assertSame(standing, standing.touched(stitchFireFactor(), NOW + 1));
        }

        @Test
        void theStrandsLetGoTheLingerAfterTheLastTouch() {
            assertFalse(tangled().holds(NOW + Tangled.LINGER_TICKS));
        }
    }
}
