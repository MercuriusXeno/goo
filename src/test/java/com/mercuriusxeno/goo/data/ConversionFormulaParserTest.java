package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the target spellings the game's conversion parser reads, the target's ratio holding a multiplier and a
 * divisor the way the other ratios do (decision mutator-sentences-read-in-symbols).
 */
class ConversionFormulaParserTest {

    @Test
    void mixedTargetMultipliesAndDivides() {
        assertEquals(new GooConversion.Formula(GooTypes.METAL, 4, GooTypes.ORNATE, 3, 2),
                ConversionFormulaParser.parseFormula("metal / 4 -> ornate * 2 / 3"));
    }

    @Test
    void multipliedTargetDividesByOne() {
        assertEquals(new GooConversion.Formula(GooTypes.METAL, 4, GooTypes.ORNATE, 1, 2),
                ConversionFormulaParser.parseFormula("metal / 4 -> ornate * 2"));
    }

    @Test
    void dividedTargetMultipliesByOne() {
        assertEquals(new GooConversion.Formula(GooTypes.METAL, 4, GooTypes.ORNATE, 3, 1),
                ConversionFormulaParser.parseFormula("metal/4->ornate/3"));
    }

    @Test
    void nothingTargetKeepsTheSource() {
        assertEquals(new GooConversion.Formula(GooTypes.METAL, 4, GooTypes.METAL, 1, 0),
                ConversionFormulaParser.parseFormula("metal / 4 -> 0"));
    }
}
