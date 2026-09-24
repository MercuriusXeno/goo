package com.mercuriusxeno.goo.item;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests GooContents holding 2B of each of two types: per-type storage stays
 * int through both codecs, and the total across types is a long
 * (decision diagnose-then-fix-crucible-overflow).
 */
class GooContentsAtTheCapTest {

    private static final int TWO_BILLION = 2_000_000_000;
    private static final GooContents TWO_FULL_TYPES = new GooContents(Map.of(
            GooTypes.ROCK, TWO_BILLION, GooTypes.METAL, TWO_BILLION));

    /** The total across two full types reads past the int range. */
    @Test
    void totalAcrossFullTypesReadsAsLong() {
        assertEquals(4_000_000_000L, TWO_FULL_TYPES.totalVolume());
    }

    /** The save codec reads back each type's 2B. */
    @Test
    void saveCodecRoundTripsFullTypes() {
        JsonElement saved = GooContents.CODEC.encodeStart(JsonOps.INSTANCE, TWO_FULL_TYPES).getOrThrow();
        assertEquals(TWO_FULL_TYPES, GooContents.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    /** The sync codec reads back each type's 2B. */
    @Test
    void syncCodecRoundTripsFullTypes() {
        ByteBuf buf = Unpooled.buffer();
        GooContents.STREAM_CODEC.encode(buf, TWO_FULL_TYPES);
        assertEquals(TWO_FULL_TYPES, GooContents.STREAM_CODEC.decode(buf));
    }

    /** The readout formats the 4B total in the M tier. */
    @Test
    void readoutFormatsLongTotalInMegaTier() {
        assertEquals("4 M", GooFormat.formatFluidDisplay(TWO_FULL_TYPES.totalVolume()));
        assertEquals("4M", GooFormat.formatFluidDisplayCompact(TWO_FULL_TYPES.totalVolume()));
    }
}
