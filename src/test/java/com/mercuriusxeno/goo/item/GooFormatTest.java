package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that GooFormat formats a raw amount by magnitude, K, M and B, with no
 * unit word and no division by 1000 (decision amounts-format-by-magnitude-alone).
 */
class GooFormatTest {

    @Test
    void amountUnderAThousandReadsWhole() {
        assertEquals("200", GooFormat.formatAmount(200));
    }

    @Test
    void zeroReadsZero() {
        assertEquals("0", GooFormat.formatAmount(0));
    }

    @Test
    void amountJustUnderAThousandReadsWhole() {
        assertEquals("999", GooFormat.formatAmount(999));
    }

    @Test
    void thousandReadsOneK() {
        assertEquals("1K", GooFormat.formatAmount(1_000));
    }

    @Test
    void twelveHundredReadsOnePointTwoK() {
        assertEquals("1.2K", GooFormat.formatAmount(1_200));
    }

    @Test
    void sixteenThousandReadsSixteenK() {
        assertEquals("16K", GooFormat.formatAmount(16_000));
    }

    @Test
    void thirtyTwoMillionReadsThirtyTwoM() {
        assertEquals("32M", GooFormat.formatAmount(32_000_000));
    }

    @Test
    void billionReadsOneB() {
        assertEquals("1B", GooFormat.formatAmount(1_000_000_000));
    }

    @Test
    void amountKeepsThreeSignificantDigitsTruncated() {
        assertEquals("1.23K", GooFormat.formatAmount(1_239));
        assertEquals("12.3K", GooFormat.formatAmount(12_399));
        assertEquals("123K", GooFormat.formatAmount(123_999));
        assertEquals("999K", GooFormat.formatAmount(999_999));
    }

    @Test
    void fractionTruncatingToZeroReadsWhole() {
        assertEquals("1M", GooFormat.formatAmount(1_001_000));
    }
}
