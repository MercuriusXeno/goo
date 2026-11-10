package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
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
 * lighting each full at the source and weaker the farther it travels, its
 * strength, reach and outline set by its vibration rank alone, rank 5 and
 * below ringing alike and 15 the strongest; sounds that
 * meet on a face add up, once per sound, to twice a full light at most, and
 * a face's light falls at a steady rate, a full light within three
 * seconds; a block glows as bright as the sound was as it reached it and
 * decays from there; a mob
 * that makes a sound outlines for three seconds at rank 10; what is
 * heard draws fainter the farther it stands, down to a fifth at the edge of
 * render distance, and the brew's end forgets it
 * (decision sculker-sight-persists-where-echoes-have-touched).
 */
class SculkerEchoesTest {

    /** Solid ground at and below y = 0, air above it. */
    private static final Predicate<BlockPos> FLAT_GROUND = pos -> pos.getY() > 0;
    private static final Vec3 SOUND = new Vec3(0.5, 1, 0.5);
    private static final long HEARD_AT = 100;
    private static final BlockPos UNDER_THE_SOUND = BlockPos.ZERO;
    private static final BlockPos THREE_AWAY = new BlockPos(3, 0, 0);
    private static final BlockPos ELEVEN_AWAY = new BlockPos(11, 0, 0);
    private static final BlockPos THIRTEEN_AWAY = new BlockPos(13, 0, 0);
    private static final BlockPos FIFTEEN_AWAY = new BlockPos(15, 0, 0);
    private static final BlockPos SEVENTEEN_AWAY = new BlockPos(17, 0, 0);
    private static final BlockPos TWENTY_AWAY = new BlockPos(20, 0, 0);
    /** Long enough for a ring to cross twenty blocks. */
    private static final long FOUR_SECONDS = 80;
    private static final BlockPos BURIED_SIDE = new BlockPos(1, -1, 0);
    private static final double CLOSE = 1e-6;
    private static final int ORDINARY = 10;
    private static final int LOUDEST = 15;
    /** Two seconds, long enough for any ring to have reached its end. */
    private static final long TWO_SECONDS = 40;
    private static final long ONE_SECOND = 20;
    private static final long QUARTER_SECOND = 5;
    private static final double RENDER_REACH = 160;
    /** A memory small enough for a few weak sounds to fill it. */
    private static final int SMALL_MEMORY = 120;
    private static final Vec3 AT_THE_SOUND = new Vec3(0.5, 1.5, 0.5);

    private static SculkerEchoes heardAndAdvancedTo(long now) {
        SculkerEchoes echoes = new SculkerEchoes();
        echoes.hear(SOUND, HEARD_AT, ORDINARY);
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
            echoes.hear(SOUND, HEARD_AT, ORDINARY);
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
        void aRingTravelsItsSoundsWholeRange() {
            assertTrue(everLit(ELEVEN_AWAY, 12));
            assertFalse(everLit(THIRTEEN_AWAY, 12));
        }

        @Test
        void aRankTenSoundTravelsSixteenBlocks() {
            assertEquals(16.0, SculkerEchoes.reachFor(ORDINARY), CLOSE);
            assertTrue(everLit(FIFTEEN_AWAY, SculkerEchoes.reachFor(ORDINARY)));
            assertFalse(everLit(SEVENTEEN_AWAY, SculkerEchoes.reachFor(ORDINARY)));
        }

        @Test
        void aHigherRankTravelsFarther() {
            assertTrue(everLit(TWENTY_AWAY, SculkerEchoes.reachFor(LOUDEST)));
        }

        private boolean everLit(BlockPos face, double range) {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.ring(SOUND, HEARD_AT, 1f, range);
            for (long tick = HEARD_AT; tick <= HEARD_AT + FOUR_SECONDS; tick++) {
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
        void partialBlocksLetSoundThroughAndOnlyBlocksWithCollisionLight() {
            BlockPos sign = new BlockPos(2, 1, 0);
            BlockPos fence = new BlockPos(4, 1, 0);
            Predicate<BlockPos> lights = pos -> pos.getY() <= 0 || pos.equals(fence);
            Predicate<BlockPos> passesSound = pos -> pos.getY() > 0;
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.ring(new Vec3(3.5, 2.5, 0.5), HEARD_AT, 1f, 12);
            for (long tick = HEARD_AT; tick <= HEARD_AT + ONE_SECOND; tick++) {
                echoes.advance(tick, lights, passesSound);
            }
            assertTrue(echoes.remembered().containsKey(sign.below()), "the block under the sign should light");
            assertTrue(echoes.remembered().containsKey(fence.below()), "the block under the fence should light");
            assertTrue(echoes.remembered().containsKey(fence), "the fence should light");
            assertFalse(echoes.remembered().containsKey(sign), "the sign, with no collision, should not light");
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

        private SculkerEchoes heardTimes(int sounds, int rank) {
            SculkerEchoes echoes = new SculkerEchoes();
            for (int sound = 0; sound < sounds; sound++) {
                echoes.hear(SOUND, HEARD_AT, rank);
            }
            for (long tick = HEARD_AT; tick <= HEARD_AT + 2; tick++) {
                echoes.advance(tick, FLAT_GROUND);
            }
            return echoes;
        }

        @Test
        void oneSoundsRingsLightAFaceOnce() {
            SculkerEchoes echoes = heardAndAdvancedTo(HEARD_AT + QUARTER_SECOND + 2);
            float once = heardTimes(1, ORDINARY).remembered().get(UNDER_THE_SOUND).light();
            assertTrue(echoes.remembered().get(UNDER_THE_SOUND).light() <= once);
        }

        @Test
        void twoSoundsMeetingOnAFaceAddUp() {
            float once = heardTimes(1, ORDINARY).remembered().get(UNDER_THE_SOUND).light();
            float twice = heardTimes(2, ORDINARY).remembered().get(UNDER_THE_SOUND).light();
            assertEquals(2 * once, twice, CLOSE);
        }

        @Test
        void aFacesLightIsCappedAtTwiceAFullLight() {
            assertEquals(2f, heardTimes(5, ORDINARY).remembered().get(UNDER_THE_SOUND).light(), CLOSE);
        }

        @Test
        void aWeakSoundCrossingAHeavilyLitFaceNeverShortensItsLight() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.ring(SOUND, HEARD_AT, 2f, 64);
            long weakAt = HEARD_AT + 20;
            echoes.ring(new Vec3(8.5, 1, 0.5), weakAt, 0.25f, 12);
            float before = Float.MAX_VALUE;
            long fadedAt = 0;
            for (long tick = HEARD_AT; tick <= HEARD_AT + 200; tick++) {
                echoes.advance(tick, FLAT_GROUND);
                SculkerEchoes.Lit lit = echoes.remembered().get(UNDER_THE_SOUND);
                if (tick > HEARD_AT + 2 && lit != null) {
                    float level = lit.levelAt(tick);
                    float expected = before - 1f / 60;
                    assertTrue(before == Float.MAX_VALUE || level >= expected - 1e-4f,
                            "the weak sound shortened the face's light at tick " + tick + ": " + before + " -> " + level);
                    before = level;
                    fadedAt = lit.fadedAt();
                }
            }
            assertTrue(fadedAt >= HEARD_AT + 2 + 120, "the point-blank light should last its full six seconds, it ends at "
                    + (fadedAt - HEARD_AT));
        }

        @Test
        void manyWeakSoundsNeverPushOutAFaceAStrongSoundLit() {
            SculkerEchoes echoes = new SculkerEchoes(SMALL_MEMORY);
            echoes.ring(SOUND, HEARD_AT, 2f, SculkerEchoes.reachFor(ORDINARY));
            for (long tick = HEARD_AT; tick <= HEARD_AT + 3; tick++) {
                echoes.advance(tick, FLAT_GROUND);
            }
            long weakAt = HEARD_AT + 4;
            for (int sound = 0; sound < 12; sound++) {
                echoes.ring(new Vec3(30.5 + sound * 9, 1, 0.5), weakAt, 0.25f, 4);
            }
            for (long tick = weakAt; tick <= weakAt + 20; tick++) {
                echoes.advance(tick, FLAT_GROUND);
            }
            assertTrue(echoes.remembered().size() <= SMALL_MEMORY, "the memory outgrew its most");
            assertTrue(echoes.remembered().containsKey(UNDER_THE_SOUND),
                    "weak sounds pushed out the face a strong sound lit point-blank");
        }

        @Test
        void aHigherRankLightsAFaceLonger() {
            float ordinary = heardTimes(1, ORDINARY).remembered().get(UNDER_THE_SOUND).light();
            float loudest = heardTimes(1, LOUDEST).remembered().get(UNDER_THE_SOUND).light();
            assertTrue(loudest > ordinary, "the loudest lit " + loudest + ", an ordinary sound " + ordinary);
        }
    }

    /**
     * A ring's strength, reach and outline follow its sound's rank, never
     * its volume, with nothing ringing below rank 5 (decision
     * sculker-echo-intensity-follows-vibration-rank).
     */
    @Nested
    class Ranks {

        private static final int FOOTSTEP = 1;
        private static final int FLOOR = 5;
        private static final int MOB_NOISE = 9;

        @Test
        void aRankOneRingIsDimmerAndShorterThanARankFifteenRing() {
            assertTrue(SculkerEchoes.strengthFor(FOOTSTEP) < SculkerEchoes.strengthFor(LOUDEST));
            assertTrue(SculkerEchoes.reachFor(FOOTSTEP) < SculkerEchoes.reachFor(LOUDEST));
        }

        @Test
        void nothingRingsBelowRankFive() {
            assertEquals(SculkerEchoes.strengthFor(FLOOR), SculkerEchoes.strengthFor(FOOTSTEP), CLOSE);
            assertEquals(SculkerEchoes.reachFor(FLOOR), SculkerEchoes.reachFor(FOOTSTEP), CLOSE);
            assertTrue(SculkerEchoes.strengthFor(FLOOR) < SculkerEchoes.strengthFor(MOB_NOISE));
        }

        @Test
        void twoSoundsOfOneRankLightAFaceAlike() {
            SculkerEchoes groan = new SculkerEchoes();
            SculkerEchoes cry = new SculkerEchoes();
            groan.hear(SOUND, HEARD_AT, MOB_NOISE);
            cry.hear(SOUND, HEARD_AT, MOB_NOISE);
            for (long tick = HEARD_AT; tick <= HEARD_AT + ONE_SECOND; tick++) {
                groan.advance(tick, FLAT_GROUND);
                cry.advance(tick, FLAT_GROUND);
            }
            assertEquals(groan.remembered(), cry.remembered());
        }

        @Test
        void aRingFirstGrownLateStillLightsItsSource() {
            SculkerEchoes echoes = new SculkerEchoes();
            echoes.hear(SOUND, HEARD_AT, ORDINARY);
            echoes.advance(HEARD_AT + 4, FLAT_GROUND);
            assertTrue(echoes.remembered().containsKey(UNDER_THE_SOUND),
                    "a ring first grown several ticks after it set out should still light its source");
        }
    }

    @Nested
    class Fading {

        @Test
        void aFullLightFadesOutOverThreeSeconds() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f);
            assertEquals(0.5f, lit.levelAt(HEARD_AT + 30), CLOSE);
            assertEquals(0f, lit.levelAt(HEARD_AT + 60), CLOSE);
        }

        @Test
        void aDoubledLightLastsTwiceAsLong() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 2f);
            assertEquals(1f, lit.levelAt(HEARD_AT + 60), CLOSE);
            assertEquals(0f, lit.levelAt(HEARD_AT + 120), CLOSE);
        }

        @Test
        void aBlockGlowsAsBrightAsTheSoundHitItThenDecaysFromThere() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 0.6f);
            assertEquals(0.6f, SculkerEchoes.glowAt(lit, HEARD_AT), CLOSE);
            assertEquals(0.3f, SculkerEchoes.glowAt(lit, HEARD_AT + 18), 1e-5);
            assertEquals(0f, SculkerEchoes.glowAt(lit, HEARD_AT + 36), CLOSE);
        }

        @Test
        void theWakeIsGoneOnceTheFaceHasFaded() {
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f);
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
            SculkerEchoes.Lit lit = new SculkerEchoes.Lit(HEARD_AT, 1f);
            assertEquals(1f, SculkerEchoes.strength(UNDER_THE_SOUND, lit, AT_THE_SOUND, HEARD_AT, RENDER_REACH), 0.01);
        }
    }

    @Nested
    class Outlines {

        @Test
        void aRankTenSoundOutlinesItsMobForThreeSeconds() {
            assertEquals(60, SculkerEchoes.outlineTicksFor(ORDINARY));
        }

        @Test
        void aRankFifteenSoundOutlinesItsMobForFourAndAHalfSeconds() {
            assertEquals(90, SculkerEchoes.outlineTicksFor(LOUDEST));
        }

        @Test
        void anOutlineFadesFromFullToNothing() {
            int ticks = SculkerEchoes.outlineTicksFor(ORDINARY);
            assertEquals(1f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT), CLOSE);
            assertEquals(0.5f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT + ticks / 2), CLOSE);
            assertEquals(0f, SculkerEchoes.outlineLeftAt(HEARD_AT, ticks, HEARD_AT + ticks), CLOSE);
        }
    }
}
