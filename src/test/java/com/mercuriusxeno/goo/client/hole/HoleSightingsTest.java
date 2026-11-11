package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.client.ability.GrowOpen;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how far open the client draws a hole's mouth: growing from nothing when first seen to full
 * size over a span no shorter than the tunnel's, and shrinking from full to nothing over a closing span
 * once the server stops listing it, whether it closed early or by its timer, then drawn no more.
 * decision hole-animates-open-and-closed
 */
class HoleSightingsTest {

    private static final long OPENED_AT = 1000L;
    private static final long CLOSES_AT = OPENED_AT + 240L;
    private static final PortableHolesSyncPayload.Mouth MOUTH = new PortableHolesSyncPayload.Mouth(
            List.of(new BlockPos(4, 64, -2)), Direction.UP, OPENED_AT, CLOSES_AT, List.of());
    private static final float SAMPLE = 0.25F;
    /** The most the share may change between neighbouring samples, a smooth shrink rather than a jump. */
    private static final float MOST_STEP = 0.05F;

    @Nested
    class Opening {

        @Test
        void growsFromNothingThroughPartialSizesToFull() {
            HoleSightings sightings = new HoleSightings();
            sightings.seen(List.of(MOUTH), OPENED_AT);

            assertEquals(0F, sightings.openShare(MOUTH, OPENED_AT));
            for (float age = SAMPLE; age < HoleSightings.OPENING_TICKS; age += SAMPLE) {
                float share = sightings.openShare(MOUTH, OPENED_AT + age);
                assertTrue(share > 0F && share < 1F, "share " + share + " at age " + age);
            }
            for (float age = HoleSightings.OPENING_TICKS; age < HoleSightings.OPENING_TICKS * 4; age += SAMPLE) {
                assertEquals(1F, sightings.openShare(MOUTH, OPENED_AT + age), "share at age " + age);
            }
        }

        @Test
        void opensNoFasterThanTheTunnel() {
            assertTrue(HoleSightings.OPENING_TICKS >= GrowOpen.OPENING_TICKS,
                    HoleSightings.OPENING_TICKS + " ticks against the tunnel's " + GrowOpen.OPENING_TICKS);
        }
    }

    @Nested
    class Closing {

        @Test
        void closingEarlyShrinksFromFullToNothing() {
            assertShrinksShutAfterDismissal(OPENED_AT + 100L);
        }

        @Test
        void closingByItsTimerShrinksFromFullToNothing() {
            assertShrinksShutAfterDismissal(CLOSES_AT);
        }

        private void assertShrinksShutAfterDismissal(long dismissedAt) {
            HoleSightings sightings = new HoleSightings();
            sightings.seen(List.of(MOUTH), OPENED_AT);
            sightings.seen(List.of(), dismissedAt);

            float previous = sightings.openShare(MOUTH, dismissedAt);
            assertEquals(1F, previous);
            assertTrue(sightings.drawn(dismissedAt).contains(MOUTH));
            for (float since = SAMPLE; since <= HoleSightings.CLOSING_TICKS; since += SAMPLE) {
                float share = sightings.openShare(MOUTH, dismissedAt + since);
                assertTrue(share <= previous, "share rises to " + share + " at " + since);
                assertTrue(previous - share < MOST_STEP, "share jumps by " + (previous - share) + " at " + since);
                previous = share;
            }
            assertEquals(0F, sightings.openShare(MOUTH, dismissedAt + HoleSightings.CLOSING_TICKS));
            assertFalse(sightings.drawn(dismissedAt + HoleSightings.CLOSING_TICKS).contains(MOUTH));
        }
    }
}
