package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the compact format's length across the sub-10-blob range, pinning why
 * the crucible HUD panel resized while a melting item drained (decision
 * diagnose-then-fix-crucible-panel-width): compactSigDigits keeps three
 * significant digits and padAndTrimZeros trims the trailing zeros, so the
 * string runs 4, 3 or 1 characters as a volume crosses 9,990, 9,900 and
 * 9,000 mB, and the panel, sized to its widest row, shrinks and grows back.
 */
class GooFormatTest {

    @Test
    void compactFormatTrimsTrailingZerosUnderTenBlobs() {
        assertEquals(".999", GooFormat.formatFluidDisplayCompact(999));
        assertEquals("9.99", GooFormat.formatFluidDisplayCompact(9_990));
        assertEquals("9.9", GooFormat.formatFluidDisplayCompact(9_900));
        assertEquals("9", GooFormat.formatFluidDisplayCompact(9_000));
    }

    @Test
    void compactLengthMovesAcrossTheSubTenBlobSweep() {
        Set<Integer> lengths = LongStream.of(999, 9_000, 9_900, 9_990)
                .mapToObj(GooFormat::formatFluidDisplayCompact)
                .map(String::length)
                .collect(Collectors.toSet());
        assertEquals(Set.of(1, 3, 4), lengths);
    }

    @Test
    void noSubTenBlobVolumeFormatsLongerThanFourCharacters() {
        assertTrue(LongStream.range(0, 10_000)
                .mapToObj(GooFormat::formatFluidDisplayCompact)
                .allMatch(text -> text.length() <= 4));
    }
}
