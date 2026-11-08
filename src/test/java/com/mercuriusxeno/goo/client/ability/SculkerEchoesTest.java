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
 * A heard sound sends two rings out of its source, a quarter second apart,
 * each sweeping eight blocks a second across the faces open to air near it,
 * lighting each full at the source and weaker the farther it travels, gone
 * at four blocks for an ordinary sound and six for the loudest; sounds that
 * meet on a face add up, once per sound, to twice a full light at most, and
 * a face's light falls at a steady rate, one ordinary sound's within three
 * seconds; a face shows bright as a ring crosses it and faintly after; a mob
 * that makes a sound outlines for three seconds, six at the loudest; what is
 * heard draws fainter the farther it stands, down to a fifth at the edge of
 * render distance, and the brew's end forgets it
 * (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerEchoesTest {

    /** Solid ground at and below y = 0, air above it. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() > 0;
    private static final Vec3 SOUND = new Vec3(0.5, 1, 0.5);
    private static final long HEARD_AT = 100;
    private static final SculkerEchoes.Face UNDER_THE_SOUND = new SculkerEchoes.Face(BlockPos.ZERO, Direction.UP);
    private static final SculkerEchoes.Face THREE_AWAY = new SculkerEchoes.Face(new BlockPos(3, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face FIVE_AWAY = new SculkerEchoes.Face(new BlockPos(5, 0, 0), Direction.UP);
    private static final SculkerEchoes.Face SEVEN_AWAY = new SculkerEchoes.Face(new BlockPos(7, 0, 0), Direction.UP);
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
        void aSoundSendsTwoRingsAQuarterSecondApart() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, 1f);
            List<Long> starts = echoes.ripples().stream().map(SculkerEchoes.Ripple::startTick).toList();
            assertEquals(List.of(HEARD_AT, HEARD_AT + QUARTER_SECOND), starts);
        }

        @Test
        void aRingSweepsEightBlocksASecond() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, LOUDEST);
            SculkerEchoes.Ripple first = echoes.ripples().get(0);
            assertEquals(4.0, first.radiusAt(HEARD_AT + ONE_SECOND / 2), CLOSE);
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
            assertTrue(everLit(THREE_AWAY, 1f));
            assertFalse(everLit(FIVE_AWAY, 1f));
        }

        @Test
        void theLoudestSoundReachesSixBlocks() {
            assertTrue(everLit(FIVE_AWAY, LOUDEST));
            assertFalse(everLit(SEVEN_AWAY, LOUDEST));
        }

        private boolean everLit(SculkerEchoes.Face face, float loudness) {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, loudness);
            for (long tick = HEARD_AT; tick <= HEARD_AT + TWO_SECONDS; tick++) {
                echoes.advance(tick, FLAT_GROUND);
                if (echoes.remembered().containsKey(face)) {
                    return true;
                }
            }
            return false;
        }

        @Test
        void aFaceNearTheSourceLightsMoreThanOneFartherOut() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + ONE_SECOND);
            assertTrue(echoes.remembered().get(UNDER_THE_SOUND).light() > echoes.remembered().get(THREE_AWAY).light());
        }

        @Test
        void eachRingRelightsTheFacesItCrosses() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + QUARTER_SECOND + 2);
            assertEquals(HEARD_AT + QUARTER_SECOND + 2, echoes.remembered().get(UNDER_THE_SOUND).litAt());
        }

        @Test
        void theBrewsEndForgetsEveryFaceAndRing() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + 2);
            echoes.clear();
            assertTrue(echoes.remembered().isEmpty() && echoes.ripples().isEmpty());
        }
    }

    @Nested
    class Stacking {

        private SculkerEchoes heardTimes(int sounds, float loudness) {
            SculkerEchoes echoes = new SculkerEchoes();
            for (int sound = 0; sound < sounds; sound++) {
                echoes.hear(SOUND, HEARD_AT, loudness);
            }
            for (long tick = HEARD_AT; tick <= HEARD_AT + 2; tick++) {
                echoes.advance(tick, FLAT_GROUND);
            }
            return echoes;
        }

        @Test
        void oneSoundsRingsLightAFaceOnce() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + QUARTER_SECOND + 2);
            float once = heardTimes(1, 1f).remembered().get(UNDER_THE_SOUND).light();
            assertTrue(echoes.remembered().get(UNDER_THE_SOUND).light() <= once);
        }

        @Test
        void twoSoundsMeetingOnAFaceAddUp() {
            float once = heardTimes(1, 1f).remembered().get(UNDER_THE_SOUND).light();
            float twice = heardTimes(2, 1f).remembered().get(UNDER_THE_SOUND).light();
            assertEquals(2 * once, twice, CLOSE);
        }

        @Test
        void aFacesLightIsCappedAtTwiceAFullLight() {
            assertEquals(2f, heardTimes(5, 1f).remembered().get(UNDER_THE_SOUND).light(), CLOSE);
        }

        @Test
        void aLouderSoundLightsAFaceLonger() {
            float ordinary = heardTimes(1, 1f).remembered().get(UNDER_THE_SOUND).light();
            float loudest = heardTimes(1, LOUDEST).remembered().get(UNDER_THE_SOUND).light();
            assertTrue(loudest > ordinary, "the loudest lit " + loudest + ", an ordinary sound " + ordinary);
        }
    }

    @Nested
    class Fading {

        @Test
        void aFullLightFadesOutOverThreeSeconds() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f, 1f);
            assertEquals(0.5f, lit.levelAt(HEARD_AT + 30), CLOSE);
            assertEquals(0f, lit.levelAt(HEARD_AT + 60), CLOSE);
        }

        @Test
        void aDoubledLightLastsTwiceAsLong() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 2f, 1f);
            assertEquals(1f, lit.levelAt(HEARD_AT + 60), CLOSE);
            assertEquals(0f, lit.levelAt(HEARD_AT + 120), CLOSE);
        }

        @Test
        void aFaceShowsBrightAsTheRingCrossesItThenFaintlyInItsWake() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f, 1f);
            float onTheRing = SculkerEchoes.glowAt(lit, HEARD_AT);
            float inTheWake = SculkerEchoes.glowAt(lit, HEARD_AT + QUARTER_SECOND);
            assertEquals(1f, onTheRing, CLOSE);
            assertTrue(inTheWake > 0 && inTheWake < onTheRing / 2, "the wake should glow faintly, it glows " + inTheWake);
        }

        @Test
        void theWakeIsGoneOnceTheFaceHasFaded() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f, 1f);
            assertEquals(0f, SculkerEchoes.glowAt(lit, HEARD_AT + 60), CLOSE);
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
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f, 1f);
            assertEquals(1f, SculkerEchoes.strength(UNDER_THE_SOUND, lit, AT_THE_SOUND, HEARD_AT, RENDER_REACH), 0.01);
        }
    }

    @Nested
    class Outlines {

        @Test
        void anOrdinarySoundOutlinesItsMobForThreeSeconds() {
            assertEquals(60, SculkerEchoes.outlineTicksFor(1f));
        }

        @Test
        void aLoudSoundOutlinesItsMobForSixSecondsAtMost() {
            assertEquals(120, SculkerEchoes.outlineTicksFor(LOUDEST));
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
