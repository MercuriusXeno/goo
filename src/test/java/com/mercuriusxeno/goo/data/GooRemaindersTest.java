package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A remainder set holds each goo type's part under 1 mB, normalizing away any whole
 * part, and survives its persistent and network codecs
 * (decision goo-is-an-int-and-only-the-remainder-is-a-fraction).
 */
class GooRemaindersTest {

    @Test
    void aRemainderPastOneNormalizesToItsPartUnderOne() {
        GooRemainders remainders = new GooRemainders(Map.of(GooTypes.LEAF, GooFraction.of(5, 3)));

        assertEquals(GooFraction.of(2, 3), remainders.of(GooTypes.LEAF));
    }

    @Test
    void aWholeRemainderLeavesNothing() {
        assertEquals(GooRemainders.EMPTY, GooRemainders.EMPTY.with(GooTypes.LEAF, GooFraction.of(2)));
    }

    /**
     * A save holding a yore remainder loads it as opulent's, added to the opulent part it held.
     * decision yore-leaves-and-rewind-moves-to-xeno-as-revert
     */
    @Test
    void savedYoreRemainderLoadsAsOpulent() {
        GooRemainders loaded = GooRemainders.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"yore\": {\"numerator\": 1, \"denominator\": 4},"
                        + " \"opulent\": {\"numerator\": 1, \"denominator\": 3}}")).getOrThrow();

        assertEquals(Map.of(GooTypes.OPULENT, GooFraction.of(7, 12)), loaded.parts());
    }

    @Test
    void remaindersSurviveThePersistentCodec() {
        GooRemainders remainders = GooRemainders.EMPTY
                .with(GooTypes.LEAF, GooFraction.of(1, 6))
                .with(GooTypes.ROCK, GooFraction.of(3, 4));

        GooRemainders loaded = GooRemainders.CODEC.parse(JsonOps.INSTANCE,
                GooRemainders.CODEC.encodeStart(JsonOps.INSTANCE, remainders).getOrThrow()).getOrThrow();

        assertEquals(remainders, loaded);
    }

    @Test
    void remaindersSurviveTheNetworkCodec() {
        GooRemainders remainders = GooRemainders.EMPTY
                .with(GooTypes.LEAF, GooFraction.of(1, 6))
                .with(GooTypes.ROCK, GooFraction.of(3, 4));
        ByteBuf buf = Unpooled.buffer();

        GooRemainders.STREAM_CODEC.encode(buf, remainders);

        assertEquals(remainders, GooRemainders.STREAM_CODEC.decode(buf));
    }
}
