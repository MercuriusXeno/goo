package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a swallowed item easing into its hole: it draws on a continuous path from the place it was
 * swallowed at a dropped item's size to its kind's soup place at the soup's size, or sunk in the pit at
 * no size where the soup shows not its kind; and a kind the swallow stored first draws in the soup only
 * from the transit's end, while a kind already swimming draws throughout.
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
    private static final double SAME = 1e-9;
    private static final long SWALLOWED_TIME = 2000L;
    private static final Identifier STONE = Identifier.withDefaultNamespace("cobblestone");
    private static final Identifier DIAMOND = Identifier.withDefaultNamespace("diamond");

    @Nested
    class Transit {

        @Test
        void aShownKindGlidesFromWhereItWasSwallowedToItsSoupPlace() {
            int index = 3;
            float start = 137F;
            HoleDrift.Placement previous = null;
            HoleDrift.Placement last = null;
            for (float since = 0; since <= HoleSwallows.TRANSIT_TICKS; since += SAMPLE) {
                HoleDrift.Placement end = HoleSwallows.endOf(index, start + since, THREE_WIDE, THREE_WIDE);
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
            Vec3 soupPlace = HoleDrift.offset(index, endTime, THREE_WIDE, THREE_WIDE);
            assertTrue(last.offset().distanceTo(soupPlace) < SAME, "ends at " + last.offset() + ", not " + soupPlace);
            assertEquals(HoleDrift.ITEM_SIZE, last.size(), SAME, "ends at the soup's size");
        }

        @Test
        void aKindPastTheShownTwelveSinksIntoThePitAtNoSize() {
            HoleDrift.Placement previous = null;
            HoleDrift.Placement last = null;
            for (float since = 0; since <= HoleSwallows.TRANSIT_TICKS; since += SAMPLE) {
                HoleDrift.Placement end = HoleSwallows.endOf(HoleSwallows.NOT_SHOWN, since, THREE_WIDE, THREE_WIDE);
                last = HoleSwallows.transitAt(SWALLOWED_AT, DROPPED_SIZE, end, since);
                if (previous != null) {
                    assertSmoothStep(previous, last, since);
                }
                previous = last;
            }
            Vec3 at = last.offset();
            assertTrue(at.y() < 0 && at.y() >= -HoleDrift.PIT_DEPTH, "sinks to depth " + at.y());
            assertTrue(Math.abs(at.x()) < THREE_WIDE && Math.abs(at.z()) < THREE_WIDE, "sinks outside the pit " + at);
            assertEquals(0, last.size(), SAME, "sinks to no size");
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

    private static PortableHolesSyncPayload.Swallowed swallowOf(Identifier item) {
        return new PortableHolesSyncPayload.Swallowed(item, new Vec3(4.5, 65.05, -1.5), SWALLOWED_TIME);
    }

    private static PortableHolesSyncPayload.Mouth mouth(List<Identifier> stored,
                                                        List<PortableHolesSyncPayload.Swallowed> swallowed) {
        return new PortableHolesSyncPayload.Mouth(List.of(new BlockPos(4, 64, -2)), Direction.UP, 1000L, 1200L,
                stored.stream().map(item -> new PortableHolesSyncPayload.Stored(item, 1)).toList(), swallowed);
    }
}
