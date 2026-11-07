package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.AilmentKind;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.MobAilments.FADE_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's ailments hold for their duration, run out on its last tick,
 * fade over the last FADE_TICKS and clear on a disconnect
 * (decision ailment-overlay-shader-per-ailment).
 */
class MobAilmentsTest {

    private static final int MOB = 7;
    private static final long LAND_TICK = 1000;
    private static final int DURATION = 60;

    private static MobAilments hexedAt(long tick) {
        MobAilments ailments = new MobAilments();
        ailments.afflict(MOB, AilmentKind.HEX, tick, DURATION);
        return ailments;
    }

    @Nested
    class Holding {

        @Test
        void ailmentHoldsThroughItsDuration() {
            MobAilments ailments = hexedAt(LAND_TICK);

            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, DURATION)), ailments.ailmentsOf(MOB, LAND_TICK));
            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, 1)),
                    ailments.ailmentsOf(MOB, LAND_TICK + DURATION - 1));
        }

        @Test
        void eachAilmentAnEntityWearsDrawsOnItsOwn() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.STASIS, LAND_TICK, DURATION);

            assertEquals(List.of(AilmentKind.STASIS, AilmentKind.HEX),
                    ailments.ailmentsOf(MOB, LAND_TICK).stream().map(MobAilments.Worn::kind).toList());
            assertTrue(ailments.ailmentsOf(MOB + 1, LAND_TICK).isEmpty());
        }

        @Test
        void aFreshLandingStretchesTheAilmentAndAShorterOneDoesNotCutIt() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.HEX, LAND_TICK + 30, DURATION);
            ailments.afflict(MOB, AilmentKind.HEX, LAND_TICK + 31, 1);

            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, DURATION)),
                    ailments.ailmentsOf(MOB, LAND_TICK + 30));
        }
    }

    @Nested
    class Expiry {

        @Test
        void ailmentIsGoneOnTheTickItEnds() {
            assertTrue(hexedAt(LAND_TICK).ailmentsOf(MOB, LAND_TICK + DURATION).isEmpty());
        }

        @Test
        void aLaterLandingDropsEndedAilmentsOfOtherEntities() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB + 1, AilmentKind.STASIS, LAND_TICK + DURATION, DURATION);

            assertTrue(ailments.ailmentsOf(MOB, LAND_TICK).isEmpty(), "an ended ailment was kept");
        }

        @Test
        void theFadeSpansASecond() {
            assertEquals(20, FADE_TICKS);
        }

        @Test
        void overlayIsWholeUntilTheFadeThenFallsToNothing() {
            assertEquals(1f, MobAilments.strength(DURATION));
            assertEquals(1f, MobAilments.strength(FADE_TICKS));
            assertEquals(0.5f, MobAilments.strength(FADE_TICKS / 2f));
            assertEquals(0f, MobAilments.strength(0f));
            assertEquals(0f, MobAilments.strength(-1f));
        }
    }

    /** A clear fades the overlay out over the second after it lands (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class Clearing {

        private static final float NEAR = 0.051f;
        private static final long CLEARED_AT = LAND_TICK + 5;

        private float strengthAt(MobAilments ailments, long tick) {
            return MobAilments.strength(ailments.ailmentsOf(MOB, tick).getFirst().ticksLeft());
        }

        @Test
        void aClearOnAWornAilmentFadesItOverTheSecondAfter() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.HEX, CLEARED_AT, 0);

            assertEquals(1f, strengthAt(ailments, CLEARED_AT + 1), NEAR);
            assertEquals(0.5f, strengthAt(ailments, CLEARED_AT + FADE_TICKS / 2), NEAR);
            assertTrue(ailments.ailmentsOf(MOB, CLEARED_AT + FADE_TICKS).isEmpty());
        }

        @Test
        void aClearFadesOnlyTheAilmentItNames() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.STASIS, LAND_TICK, DURATION);
            ailments.afflict(MOB, AilmentKind.STASIS, CLEARED_AT, 0);

            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, DURATION - FADE_TICKS - 5)),
                    ailments.ailmentsOf(MOB, CLEARED_AT + FADE_TICKS));
        }

        @Test
        void aClearNeverLengthensAnAilmentAlreadyFading() {
            MobAilments ailments = new MobAilments();
            ailments.afflict(MOB, AilmentKind.HEX, LAND_TICK, 4);
            ailments.afflict(MOB, AilmentKind.HEX, LAND_TICK + 1, 0);

            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, 3)), ailments.ailmentsOf(MOB, LAND_TICK + 1));
        }

        @Test
        void aClearOnNothingWornLeavesNothingWorn() {
            MobAilments ailments = new MobAilments();
            ailments.afflict(MOB, AilmentKind.SCALES, LAND_TICK, 0);

            assertTrue(ailments.ailmentsOf(MOB, LAND_TICK).isEmpty());
        }
    }

    @Test
    void clearDropsEveryAilment() {
        MobAilments ailments = hexedAt(LAND_TICK);
        ailments.afflict(MOB + 1, AilmentKind.STASIS, LAND_TICK, DURATION);

        ailments.clear();

        assertTrue(ailments.ailmentsOf(MOB, LAND_TICK).isEmpty());
        assertTrue(ailments.ailmentsOf(MOB + 1, LAND_TICK).isEmpty());
    }
}
