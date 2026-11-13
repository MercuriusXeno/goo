package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a Mutators row reads Valid or Invalid with its mark, and that the builder labels a multiplier Nx and a
 * divisor /n (decisions mutators-read-valid-or-invalid-with-a-mark,
 * multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after).
 */
class MutatorsReadTest {

    /**
     * Valid reads a green check and Invalid a red x.
     */
    @Test
    void verdictReadsItsMark() {
        ProcessesPane.VerdictMark valid = ProcessesPane.verdictMark(new ProcessesTab.ProcessRow("rust", "formula",
                "metal / 4 -> yore / 9", true, "Valid", ""));
        assertEquals("✔", valid.glyph());
        assertEquals(Palette.GOOD, valid.color());
        ProcessesPane.VerdictMark invalid = ProcessesPane.verdictMark(new ProcessesTab.ProcessRow("broken",
                "formula", "metal ->", false, "Invalid", "Formula lacks a right side"));
        assertEquals("✘", invalid.glyph());
        assertEquals(Palette.DANGER, invalid.color());
    }

    /**
     * The builder labels times 3 as 3x and divided by 4 as /4.
     */
    @Test
    void builderLabelsAMultiplierNxAndADivisorSlashN() {
        // Decision multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after: the builder reads a multiplier
        // as Nx before the term and a divisor as /n after it, in symbols.
        assertEquals("3x", ExpressionBuilderPane.sideLabel(ExpressionBuilder.of("3 $waxed").terms().getFirst(),
                ExpressionBuilder.Op.MULTIPLY));
        assertEquals("/4", ExpressionBuilderPane.sideLabel(ExpressionBuilder.of("$waxed / 4").terms().getFirst(),
                ExpressionBuilder.Op.DIVIDE));
    }
}
