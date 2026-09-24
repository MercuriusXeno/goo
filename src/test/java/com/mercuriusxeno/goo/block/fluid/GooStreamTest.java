package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that one landing of a blob's volume holds the stream at that volume for
 * the hold's whole stretch and answers no stream past it
 * (decision diagnose-then-fix-vat-stream-flash).
 */
class GooStreamTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final long LANDED_AT = 1_000L;
    private static final int BLOB_VOLUME = 1_000;
    private static final int SECOND_VOLUME = 250;

    static LongStream ticksInsideTheHold() {
        return LongStream.rangeClosed(0, GooStream.HOLD_TICKS);
    }

    /**
     * Every tick from the landing through the hold answers the blob's type at the blob's volume.
     *
     * @param ticksAfter ticks since the landing
     */
    @ParameterizedTest
    @MethodSource("ticksInsideTheHold")
    void rateAt_holdsTheLandedVolume(long ticksAfter) {
        GooStream stream = landedBlob();

        assertEquals(ROCK, stream.typeAt(LANDED_AT + ticksAfter));
        assertEquals(BLOB_VOLUME, stream.rateAt(LANDED_AT + ticksAfter));
    }

    /**
     * The first tick past the hold answers no stream.
     */
    @Test
    void rateAt_answersNoStreamPastTheHold() {
        GooStream stream = landedBlob();
        long pastTheHold = LANDED_AT + GooStream.HOLD_TICKS + 1;

        assertNull(stream.typeAt(pastTheHold));
        assertEquals(0, stream.rateAt(pastTheHold));
    }

    /**
     * Volumes landing in one tick add up; a later tick's landing starts the rate
     * over and restarts the hold from that tick.
     */
    @Test
    void record_sumsATickAndRestartsOnTheNext() {
        GooStream stream = landedBlob();
        stream.record(NETHER, SECOND_VOLUME, LANDED_AT);
        assertEquals(BLOB_VOLUME + SECOND_VOLUME, stream.rateAt(LANDED_AT));

        long nextLanding = LANDED_AT + GooStream.HOLD_TICKS;
        stream.record(ROCK, SECOND_VOLUME, nextLanding);
        assertEquals(SECOND_VOLUME, stream.rateAt(nextLanding + GooStream.HOLD_TICKS));
        assertEquals(nextLanding, stream.lastTick());
    }

    private static GooStream landedBlob() {
        GooStream stream = new GooStream();
        stream.record(ROCK, BLOB_VOLUME, LANDED_AT);
        return stream;
    }
}
