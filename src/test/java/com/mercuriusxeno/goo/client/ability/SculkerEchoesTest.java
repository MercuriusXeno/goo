package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A heard sound sends three rings out of its source, a quarter second apart,
 * each sweeping eight blocks a second across the faces open to air near it,
 * an ordinary sound's reaching four blocks and the loudest's twelve; a face
 * a ring crosses shows bright as the ring passes and then faintly, fading
 * over about three seconds, six for the loudest, unless a new ring lights it
 * again; a mob that makes a sound outlines for about a second; what is heard
 * draws fainter the farther it stands, down to a fifth at the edge of render
 * distance, and the brew's end forgets it
 * (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerEchoesTest {

    /** Solid ground at and below y = 0, air above it. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() > 0;
    private static final Vec3 SOUND = new Vec3(0.5, 1, 0.5);
    private static final long HEARD_AT = 100;
    private static final SculkerEchoes.Face UNDER_THE_SOUND = new SculkerEchoes.Face(BlockPos.ZERO, Direction.UP);
    private static final SculkerEchoes.Face THREE_AWAY = new SculkerEchoes.Face(new BlockPos(3, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face SIX_AWAY = new SculkerEchoes.Face(new BlockPos(6, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face ELEVEN_AWAY = new SculkerEchoes.Face(new BlockPos(11, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face FOURTEEN_AWAY = new SculkerEchoes.Face(new BlockPos(14, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face BURIED_SIDE = new SculkerEchoes.Face(new BlockPos(1, -1, 0), Direction.WEST);
    private static final double CLOSE = 1e-6;
    private static final float LOUDEST = 4f;
    /** Two seconds, long enough for any ring to have reached its end. */
    private static final long TWO_SECONDS = 40;
    private static final long ONE_SECOND = 20;
    private static final long QUARTER_SECOND = 5;
    private static final double RENDER_REACH = 160;
    private static final Vec3 AT_THE_SOUND = new Vec3(0.5, 1.5, 0.5);

    private static SculkerEchoes heardAndAdvancedTo(long now) {
        return heardAndAdvancedTo(now, 1f);
    }

    private static SculkerEchoes heardAndAdvancedTo(long now, float loudness) {
        SculkerEchoes echoes = new SculkerEchoes();
        echoes.hear(SOUND, HEARD_AT, loudness);
        for (long tick = HEARD_AT; tick <= now; tick++) {
            echoes.advance(tick, FLAT_GROUND);
        }
        return echoes;
    }

    @Nested
    class Rings {

        @Test
        void aSoundSendsThreeRingsAQuarterSecondApart() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, 1f);
            List<Long> starts = echoes.ripples().stream().map(SculkerEchoes.Ripple::startTick).toList();
            assertEquals(List.of(HEARD_AT, HEARD_AT + QUARTER_SECOND, HEARD_AT + 2 * QUARTER_SECOND), starts);
        }

        @Test
        void aRingSweepsEightBlocksASecond() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, LOUDEST);
            SculkerEchoes.Ripple first = echoes.ripples().get(0);
            assertEquals(8.0, first.radiusAt(HEARD_AT + ONE_SECOND), CLOSE);
        }

        @Test
        void aRingCrossesNearFacesBeforeFartherOnesAndOnlyFacesOpenToAir() {
            assertTrue(heardAndAdvancedTo(HEARD_AT + 2).remembered().containsKey(UNDER_THE_SOUND));
            assertFalse(heardAndAdvancedTo(HEARD_AT + 2).remembered().containsKey(THREE_AWAY));
            SculkerEchoes later = heardAndAdvancedTo(HEARD_AT + ONE_SECOND);
            assertTrue(later.remembered().containsKey(THREE_AWAY));
            assertFalse(later.remembered().containsKey(BURIED_SIDE));
        }

        @Test
        void anOrdinarySoundReachesFourBlocks() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + TWO_SECONDS);
            assertTrue(echoes.remembered().containsKey(THREE_AWAY));
            assertFalse(echoes.remembered().containsKey(SIX_AWAY));
        }

        @Test
        void theLoudestSoundReachesTwelveBlocks() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + TWO_SECONDS, LOUDEST);
            assertTrue(echoes.remembered().containsKey(ELEVEN_AWAY));
            assertFalse(echoes.remembered().containsKey(FOURTEEN_AWAY));
        }

        @Test
        void eachRingRelightsTheFacesItCrosses() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 2 * QUARTER_SECOND + 2);
            assertEquals(HEARD_AT + 2 * QUARTER_SECOND + 2, echoes.remembered().get(UNDER_THE_SOUND).litAt());
        }

        @Test
        void theBrewsEndForgetsEveryFaceAndRing() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 2);
            echoes.clear();
            assertTrue(echoes.remembered().isEmpty() && echoes.ripples().isEmpty());
        }
    }

    @Nested
    class Fading {

        @Test
        void anOrdinarySoundsFacesFadeOverThreeSeconds() {
            assertEquals(60, SculkerEchoes.fadeTicksFor(1f));
        }

        @Test
        void theLoudestSoundsFacesFadeOverSixSeconds() {
            assertEquals(120, SculkerEchoes.fadeTicksFor(LOUDEST));
        }

        @Test
        void aFaceShowsBrightAsTheRingCrossesItThenFaintlyInItsWake() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, SculkerEchoes.fadeTicksFor(1f));
            float onTheRing = SculkerEchoes.glowAt(lit, HEARD_AT);
            float inTheWake = SculkerEchoes.glowAt(lit, HEARD_AT + QUARTER_SECOND);
            assertEquals(1f, onTheRing, CLOSE);
            assertTrue(inTheWake > 0 && inTheWake < onTheRing / 2, "the wake should glow faintly, it glows " + inTheWake);
        }

        @Test
        void theWakeIsGoneOnceTheFaceHasFaded() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, SculkerEchoes.fadeTicksFor(1f));
            assertEquals(0f, SculkerEchoes.glowAt(lit, HEARD_AT + SculkerEchoes.fadeTicksFor(1f)), CLOSE);
        }
    }

    @Nested
    class Distance {

        @Test
        void whatIsHeardDrawsFullBesideThePlayer() {
            assertEquals(1f, SculkerEchoes.strengthAt(0, RENDER_REACH), CLOSE);
        }

        @Test
        void whatIsHeardDrawsAFifthAtTheEdgeOfRenderDistance() {
            assertEquals(0.2f, SculkerEchoes.strengthAt(RENDER_REACH, RENDER_REACH), CLOSE);
        }

        @Test
        void whatIsHeardFadesEvenlyBetween() {
            assertEquals(0.6f, SculkerEchoes.strengthAt(RENDER_REACH / 2, RENDER_REACH), CLOSE);
        }

        @Test
        void nothingPastRenderDistanceDraws() {
            assertEquals(0f, SculkerEchoes.strengthAt(RENDER_REACH + 1, RENDER_REACH), CLOSE);
        }

        @Test
        void aFacesStrengthIsItsDistanceTimesItsGlow() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, SculkerEchoes.fadeTicksFor(1f));
            assertEquals(1f, SculkerEchoes.strength(UNDER_THE_SOUND, lit, AT_THE_SOUND, HEARD_AT, RENDER_REACH), 0.01);
        }
    }

    @Nested
    class Outlines {

        @Test
        void anOrdinarySoundOutlinesItsMobForASecond() {
            assertEquals(20, SculkerEchoes.outlineTicksFor(1f));
        }

        @Test
        void aLoudSoundOutlinesItsMobForTwoSecondsAtMost() {
            assertEquals(40, SculkerEchoes.outlineTicksFor(LOUDEST));
        }

        @Test
        void anOutlineFadesFromFullToNothing() {
            int ticks = SculkerEchoes.outlineTicksFor(1f);
            assertEquals(1f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT), CLOSE);
            assertEquals(0.5f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT + ticks / 2), CLOSE);
            assertEquals(0f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT + ticks), CLOSE);
        }
    }
}
