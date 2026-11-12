package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.ability.hole.HoleDrift;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a swallowed item easing into its hole: it draws on a continuous path from the place it was
 * swallowed at a dropped item's size to its kind's soup place at the soup's size; a kind the swallow stored
 * first draws in the soup only from the transit's end, while a kind already swimming draws throughout; and
 * the soup draws every kind stored, past the twelve it once showed at most.
 * decision swallowed-item-transitions-into-the-hole-smoothly
 */
class HoleSwallowsTest {

    private static final float SAMPLE = 0.25F;
    /** A 3x3 mouth's pit rim half span each way across its face. */
    private static final double THREE_WIDE = 1.45;
    /** Where the swallowed item stood, in the mouth's frame: just over the face, off its middle. */
    private static final Vec3 SWALLOWED_AT = new Vec3(0.6, 0.3, -0.4);
    /** A dropped item's drawn size, its ground model's largest span unscaled. */
    private static final double DROPPED_SIZE = 0.5;
    /** The most the place may move between neighbouring samples, a glide rather than a jump. */
    private static final double MOST_PLACE_STEP = 0.1;
    /** The most the size may change between neighbouring samples. */
    private static final double MOST_SIZE_STEP = 0.03;
    /** The most a swimming item may move between neighbouring samples, HoleDriftTest's smooth swim. */
    private static final double MOST_SOUP_STEP = 0.01;
    /** The count of kinds the soup once showed at most. */
    private static final int FIRST_PASS_KINDS = 12;
    private static final double SAME = 1e-9;
    private static final long SWALLOWED_TIME = 2000L;
    private static final Identifier STONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond");

    @Nested
    class Transit {

        @Test
        void aShownKindGlidesFromWhereItWasSwallowedToItsSoupPlace() {
            int index = 3;
            int kinds = 5;
            float start = 137F;
            HoleDrift.Placement previous = null;
            HoleDrift.Placement last = null;
            for (float since = 0; since <= HoleSwallows.TRANSIT_TICKS; since += SAMPLE) {
                HoleDrift.Placement end = HoleSwallows.endOf(index, kinds, start + since, THREE_WIDE, THREE_WIDE);
                last = HoleSwallows.transitAt(SWALLOWED_AT, DROPPED_SIZE, end, since);
                if (previous == null) {
                    assertTrue(last.offset().distanceTo(SWALLOWED_AT) < SAME, "starts at " + last.offset());
                    assertEquals(DROPPED_SIZE, last.size(), SAME, "starts at the dropped size");
                } else {
                    assertSmoothStep(previous, last, since);
                }
                previous = last;
            }
            float endTime = start + HoleSwallows.TRANSIT_TICKS;
            Vec3 soupPlace = HoleDrift.offset(index, kinds, endTime, THREE_WIDE, THREE_WIDE);
            assertTrue(last.offset().distanceTo(soupPlace) < SAME, "ends at " + last.offset() + ", not " + soupPlace);
            assertEquals(HoleDrift.ITEM_SIZE, last.size(), SAME, "ends at the soup's size");
        }

        private static void assertSmoothStep(HoleDrift.Placement previous, HoleDrift.Placement next, float since) {
            double moved = previous.offset().distanceTo(next.offset());
            double resized = Math.abs(previous.size() - next.size());
            assertTrue(moved < MOST_PLACE_STEP, "jumps " + moved + " at " + since);
            assertTrue(resized < MOST_SIZE_STEP, "resizes " + resized + " at " + since);
        }
    }

    @Nested
    class HoldBack {

        @Test
        void aKindFirstStoredBySwallowArrivesInTheSoupOnceItsTransitEnds() {
            HoleSwallows swallows = new HoleSwallows();
            swallows.heard(List.of(mouth(List.of(STONE), List.of())), SWALLOWED_TIME - 50);
            PortableHolesSyncPayload.Mouth fed = mouth(List.of(STONE, DIAMOND), List.of(swallowOf(DIAMOND)));
            swallows.heard(List.of(fed), SWALLOWED_TIME);

            for (float since = 0; since < HoleSwallows.TRANSIT_TICKS; since += SAMPLE) {
                assertEquals(List.of(0), swallows.soupDrawn(fed, SWALLOWED_TIME + since), "soup at " + since);
                assertEquals(1, swallows.runningIn(fed, SWALLOWED_TIME + since).size(), "transits at " + since);
            }
            assertEquals(List.of(0, 1), swallows.soupDrawn(fed, SWALLOWED_TIME + HoleSwallows.TRANSIT_TICKS));
            assertEquals(List.of(), swallows.runningIn(fed, SWALLOWED_TIME + HoleSwallows.TRANSIT_TICKS));
        }

        @Test
        void aKindAlreadySwimmingKeepsDrawingThroughATransitOfMoreOfIt() {
            HoleSwallows swallows = new HoleSwallows();
            swallows.heard(List.of(mouth(List.of(STONE, DIAMOND), List.of())), SWALLOWED_TIME - 50);
            PortableHolesSyncPayload.Mouth fed = mouth(List.of(STONE, DIAMOND), List.of(swallowOf(STONE)));
            swallows.heard(List.of(fed), SWALLOWED_TIME);

            for (float since = 0; since <= HoleSwallows.TRANSIT_TICKS; since += SAMPLE) {
                assertEquals(List.of(0, 1), swallows.soupDrawn(fed, SWALLOWED_TIME + since), "soup at " + since);
            }
            assertEquals(1, swallows.runningIn(fed, SWALLOWED_TIME + 1).size());
        }

        @Test
        void aMouthClosingMidTransitDropsItsTransits() {
            HoleSwallows swallows = new HoleSwallows();
            swallows.heard(List.of(mouth(List.of(), List.of())), SWALLOWED_TIME - 50);
            PortableHolesSyncPayload.Mouth fed = mouth(List.of(DIAMOND), List.of(swallowOf(DIAMOND)));
            swallows.heard(List.of(fed), SWALLOWED_TIME);
            swallows.heard(List.of(), SWALLOWED_TIME + 2);

            assertEquals(List.of(), swallows.runningIn(fed, SWALLOWED_TIME + 3));
        }
    }

    /**
     * A mouth storing more kinds than the twelve it once showed at most draws every one, and an item of a
     * kind past the twelfth eases to that kind's own place.
     * decision hole-shows-every-kind-stored
     */
    @Nested
    class EveryKind {

        private static final int MANY_KINDS = 30;
        /** A kind past the twelfth on the shelf. */
        private static final int PAST_TWELVE = 20;

        @Test
        void theSoupDrawsEveryStoredKind() {
            HoleSwallows swallows = new HoleSwallows();
            PortableHolesSyncPayload.Mouth many = mouth(kindsUpTo(MANY_KINDS), List.of());
            swallows.heard(List.of(many), SWALLOWED_TIME);

            assertEquals(IntStream.range(0, MANY_KINDS).boxed().toList(), swallows.soupDrawn(many, SWALLOWED_TIME));
        }

        @Test
        void anItemOfAKindPastTheTwelfthEasesToItsKindsOwnPlace() {
            List<Identifier> kinds = kindsUpTo(MANY_KINDS);
            PortableHolesSyncPayload.Mouth many = mouth(kinds, List.of());
            int index = HoleSwallows.shownIndexOf(many, kinds.get(PAST_TWELVE));
            float moment = 137.5F;

            assertEquals(PAST_TWELVE, index, "the shown index of the kind past the twelfth");
            assertEquals(HoleDrift.placementAt(PAST_TWELVE, MANY_KINDS, moment, THREE_WIDE, THREE_WIDE, 1F),
                    HoleSwallows.endOf(index, MANY_KINDS, moment, THREE_WIDE, THREE_WIDE));
        }

        /**
         * A kind stored first moves every kind already swimming to a place for the larger count, and each
         * glides there over the transit's span rather than jumping.
         */
        @Test
        void aNewKindMovesTheKindsSwimmingWithoutAJump() {
            HoleSwallows swallows = new HoleSwallows();
            List<Identifier> before = kindsUpTo(FIRST_PASS_KINDS);
            swallows.heard(List.of(mouth(before, List.of())), SWALLOWED_TIME - 50);
            List<Identifier> after = kindsUpTo(FIRST_PASS_KINDS + 1);
            PortableHolesSyncPayload.Mouth fed = mouth(after, List.of(swallowOf(after.getLast())));
            swallows.heard(List.of(fed), SWALLOWED_TIME);

            for (int index = 0; index < FIRST_PASS_KINDS; index++) {
                for (float time = SWALLOWED_TIME - 1F; time <= SWALLOWED_TIME + HoleSwallows.TRANSIT_TICKS + 1F;
                     time += SAMPLE) {
                    Vec3 now = HoleDrift.offset(index, drawnKinds(swallows, fed, time), time, THREE_WIDE, THREE_WIDE);
                    Vec3 next = HoleDrift.offset(index, drawnKinds(swallows, fed, time + SAMPLE), time + SAMPLE,
                            THREE_WIDE, THREE_WIDE);
                    double moved = now.distanceTo(next);
                    assertTrue(moved < MOST_SOUP_STEP, "index " + index + " jumps " + moved + " at " + time);
                }
            }
            assertEquals(FIRST_PASS_KINDS + 1, swallows.kindsShownAt(fed,
                    SWALLOWED_TIME + HoleSwallows.TRANSIT_TICKS), SAME, "the kinds shown once the transit ends");
        }

        /** The count of kinds the client draws with: the twelve it heard of until it hears the swallow. */
        private static float drawnKinds(HoleSwallows swallows, PortableHolesSyncPayload.Mouth fed, float time) {
            return time < SWALLOWED_TIME ? FIRST_PASS_KINDS : swallows.kindsShownAt(fed, time);
        }

        private static List<Identifier> kindsUpTo(int count) {
            return IntStream.range(0, count).mapToObj(kind -> Identifier.fromNamespaceAndPath("goo", "kind_" + kind))
                    .toList();
        }
    }

    private static PortableHolesSyncPayload.Swallowed swallowOf(Identifier item) {
        return new PortableHolesSyncPayload.Swallowed(item, new Vec3(4.5, 65.05, -1.5), SWALLOWED_TIME);
    }

    private static PortableHolesSyncPayload.Mouth mouth(List<Identifier> stored,
                                                        List<PortableHolesSyncPayload.Swallowed> swallowed) {
        return new PortableHolesSyncPayload.Mouth(List.of(new BlockPos(4, 64, -2)), Direction.UP, 1000L, 1200L,
                stored.stream().map(item -> new PortableHolesSyncPayload.Stored(item, 1)).toList(), swallowed);
    }
}
