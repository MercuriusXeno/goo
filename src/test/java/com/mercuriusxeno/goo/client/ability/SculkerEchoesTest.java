package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A heard sound starts a ripple whose growing front lights each block face
 * open to air that it crosses, tick by tick; a lit face fades out over
 * about five seconds unless a new echo lights it again, draws weaker the
 * farther the player stands from it and not at all past the sight's reach,
 * and the brew's end forgets it
 * (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerEchoesTest {

    /** Solid ground at and below y = 0, air above it. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() > 0;
    private static final Vec3 SOUND = new Vec3(0.5, 1, 0.5);
    private static final long HEARD_AT = 100;
    private static final SculkerEchoes.Face UNDER_THE_SOUND = new SculkerEchoes.Face(BlockPos.ZERO, Direction.UP);
    private static final SculkerEchoes.Face TEN_AWAY = new SculkerEchoes.Face(new BlockPos(10, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face BURIED_SIDE = new SculkerEchoes.Face(new BlockPos(1, -1, 0), Direction.WEST);
    private static final double CLOSE = 1e-6;

    private static SculkerEchoes heardAndAdvancedTo(long now) {
        SculkerEchoes echoes = new SculkerEchoes();
        echoes.hear(SOUND, HEARD_AT);
        for (long tick = HEARD_AT; tick <= now; tick++) {
            echoes.advance(tick, FLAT_GROUND);
        }
        return echoes;
    }

    @Nested
    class Rippling {

        @Test
        void theFirstTickLightsTheFaceUnderTheSound() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 1);
            assertTrue(echoes.remembered().containsKey(UNDER_THE_SOUND));
            assertFalse(echoes.remembered().containsKey(TEN_AWAY));
        }

        @Test
        void theGrowingFrontReachesFartherFacesOnLaterTicksAndOnlyFacesOpenToAir() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 8);
            assertTrue(echoes.remembered().containsKey(TEN_AWAY));
            assertFalse(echoes.remembered().containsKey(BURIED_SIDE));
        }

        @Test
        void aLitFaceIsGoneFiveSecondsAfterItsLastEcho() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 1 + SculkerEchoes.FADE_TICKS + 1);
            assertFalse(echoes.remembered().containsKey(UNDER_THE_SOUND));
        }

        @Test
        void aNewEchoRelightsAFadingFace() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + SculkerEchoes.FADE_TICKS / 2);
            long again = HEARD_AT + SculkerEchoes.FADE_TICKS / 2 + 1;
            echoes.hear(SOUND, again);
            for (long tick = again; tick <= again + SculkerEchoes.FADE_TICKS / 2 + 2; tick++) {
                echoes.advance(tick, FLAT_GROUND);
            }
            assertTrue(echoes.remembered().containsKey(UNDER_THE_SOUND));
            assertEquals(again + 1, echoes.remembered().get(UNDER_THE_SOUND));
        }

        @Test
        void theBrewsEndForgetsEveryFace() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 8);
            assertFalse(echoes.remembered().isEmpty());
            echoes.clear();
            assertTrue(echoes.remembered().isEmpty());
        }
    }

    @Nested
    class Strength {

        private static final Vec3 NEAR = new Vec3(0.5, 1, 0.5);

        @Test
        void fallsAsThePlayerStandsFarther() {
            Vec3 far = new Vec3(20.5, 1, 0.5);
            assertTrue(SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, NEAR, HEARD_AT)
                    > SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, far, HEARD_AT));
        }

        @Test
        void isZeroPastTheSightsReach() {
            Vec3 pastReach = new Vec3(0.5 + SculkerEchoes.SIGHT_REACH + 1, 0.5, 0.5);
            assertEquals(0f, SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, pastReach, HEARD_AT), CLOSE);
        }

        @Test
        void fallsAsTheFaceAgesAndIsZeroOnceFaded() {
            float fresh = SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, NEAR, HEARD_AT);
            float halfway = SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, NEAR, HEARD_AT + SculkerEchoes.FADE_TICKS / 2);
            float faded = SculkerEchoes.strength(UNDER_THE_SOUND, HEARD_AT, NEAR, HEARD_AT + SculkerEchoes.FADE_TICKS);
            assertTrue(fresh > halfway && halfway > 0, "strength should fall from " + fresh + " to " + halfway);
            assertEquals(0f, faded, CLOSE);
        }
    }
}
