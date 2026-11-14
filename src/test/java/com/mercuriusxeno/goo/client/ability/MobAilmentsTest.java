package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.held.LuxEvents;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.zone.EludeEvents;
import com.mercuriusxeno.goo.ability.zone.ZoneEvents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.MobAilments.FADE_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        void anEndedAilmentFadesOverTheSecondAfterItsEndThenIsGone() {
            MobAilments ailments = hexedAt(LAND_TICK);
            assertEquals(List.of(new MobAilments.Worn(AilmentKind.HEX, 0)),
                    ailments.ailmentsOf(MOB, LAND_TICK + DURATION));
            assertFalse(ailments.ailmentsOf(MOB, LAND_TICK + DURATION).getFirst().stands());
            assertTrue(ailments.ailmentsOf(MOB, LAND_TICK + DURATION + FADE_TICKS).isEmpty());
        }

        @Test
        void aLaterLandingDropsFadedAilmentsOfOtherEntities() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB + 1, AilmentKind.STASIS, LAND_TICK + DURATION + FADE_TICKS, DURATION);

            assertTrue(ailments.ailmentsOf(MOB, LAND_TICK).isEmpty(), "a faded ailment was kept");
        }

        @Test
        void theFadeSpansASecond() {
            assertEquals(20, FADE_TICKS);
        }

        /** decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends: nothing fades before the end. */
        @Test
        void overlayIsWholeUntilItsEndThenFallsToNothingOverTheFade() {
            assertEquals(1f, MobAilments.strength(DURATION));
            assertEquals(1f, MobAilments.strength(0.01f));
            assertEquals(1f, MobAilments.strength(0f));
            assertEquals(0.5f, MobAilments.strength(-FADE_TICKS / 2f));
            assertEquals(0f, MobAilments.strength(-FADE_TICKS));
        }
    }

    /** A refreshed overlay draws whole from one refresh to the next, so no source's overlay dims while it stands (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class Refreshing {

        private static final float LAST_FRAME_BEFORE_THE_TICK = 0.99f;

        /** Each source that keeps an overlay alive by refreshing it, with its longest gap between refreshes. */
        private enum RefreshedSource {
            LUX_GLISTEN(LuxEvents.GAZE_EVERY, LuxEvents.GAZE_GLISTEN_TICKS),
            // a channel tick may land as late as the hold's grace allows
            GRAB_FLAIL(GrabEvents.HOLD_GRACE_TICKS, GrabEvents.FLAIL_OVERLAY_TICKS),
            ELUDE_SHIMMER(EludeEvents.SHIMMER_REFRESH_TICKS, EludeEvents.SHIMMER_TICKS),
            ZONE_SHIMMER(ZoneEvents.SHIMMER_REFRESH_TICKS, ZoneEvents.SHIMMER_TICKS);

            private final int periodTicks;
            private final int durationTicks;

            RefreshedSource(int periodTicks, int durationTicks) {
                this.periodTicks = periodTicks;
                this.durationTicks = durationTicks;
            }
        }

        @Test
        void eachSourcesOverlayStandsWholeUntilItsNextRefresh() {
            for (RefreshedSource source : RefreshedSource.values()) {
                MobAilments ailments = new MobAilments();
                ailments.afflict(MOB, AilmentKind.GLOW, LAND_TICK, source.durationTicks);
                long beforeNext = LAND_TICK + source.periodTicks;
                MobAilments.Worn worn = ailments.ailmentsOf(MOB, beforeNext).getFirst();
                assertEquals(1f, MobAilments.strength(worn.ticksLeft() - LAST_FRAME_BEFORE_THE_TICK), source.name());
            }
        }
    }

    @Test
    void aStandingAilmentStands() {
        assertTrue(hexedAt(LAND_TICK).ailmentsOf(MOB, LAND_TICK).getFirst().stands());
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
        void aClearEndsTheAilmentWhereItLands() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.HEX, CLEARED_AT, 0);

            assertFalse(ailments.ailmentsOf(MOB, CLEARED_AT).getFirst().stands());
        }

        @Test
        void aClearNeverLengthensAnAilmentAlreadyFading() {
            MobAilments ailments = hexedAt(LAND_TICK);
            ailments.afflict(MOB, AilmentKind.HEX, LAND_TICK + DURATION + 5, 0);

            assertTrue(ailments.ailmentsOf(MOB, LAND_TICK + DURATION + FADE_TICKS).isEmpty());
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
