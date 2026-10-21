package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ALL VALUES listing prints each value as its exact fraction
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooAuditValuesTest {

    @Test
    void aThirdsValuePrintsAsItsExactFraction() {
        assertEquals("64/3 {leaf: 64/3}", GooAuditValues.describeValue(fraction(GooTypes.LEAF, 64, 3)));
    }
}
