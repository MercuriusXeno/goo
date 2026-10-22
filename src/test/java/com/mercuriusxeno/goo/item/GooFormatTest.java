package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that GooFormat formats a raw amount by magnitude, K, M and B, with no
 * unit word and no division by 1000 (decision amounts-format-by-magnitude-alone).
 */
class GooFormatTest {

    /**
     * An exact amount reads as a mixed number.
     * decision tooltip-and-plexer-hud-show-a-mixed-number
     */
    @Test
    void anExactAmountReadsAsAMixedNumber() {
        assertEquals("21¹⁄₃", GooFormat.formatMixed(GooFraction.of(64, 3)));
        assertEquals("¹⁄₃", GooFormat.formatMixed(GooFraction.of(1, 3)));
        assertEquals("22", GooFormat.formatMixed(GooFraction.of(22)));
        assertEquals("1.2K", GooFormat.formatMixed(GooFraction.of(1_200)));
    }

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

    /**
     * A duration reads in its largest unit of at least 1, y, d, h, m or s, to 3 significant
     * digits truncated, with whole seconds below a minute (decision counts-read-three-significant-digits).
     */
    @Nested
    class Duration {

        private static final long TICKS_PER_SECOND = 20;
        private static final long SECONDS_PER_DAY = 86_400;

        private static String seconds(long seconds) {
            return GooFormat.formatDuration(seconds * TICKS_PER_SECOND);
        }

        @Test
        void overADayReadsDays() {
            assertEquals("24.8d", seconds(2_147_687));
        }

        @Test
        void ninetySecondsReadsOneAndAHalfMinutes() {
            assertEquals("1.5m", seconds(90));
        }

        @Test
        void underAMinuteReadsWholeSeconds() {
            assertEquals("59s", seconds(59));
            assertEquals("12s", seconds(12));
        }

        @Test
        void anHourReadsOneHour() {
            assertEquals("1h", seconds(3600));
        }

        @Test
        void overAYearReadsYears() {
            assertEquals("3.4y", seconds(1243 * SECONDS_PER_DAY));
        }

        @Test
        void nothingReadsZeroSeconds() {
            assertEquals("0s", seconds(0));
        }

        @Test
        void fractionOfASecondTruncatesToWholeSeconds() {
            assertEquals("12s", GooFormat.formatDuration(250));
        }
    }
}
