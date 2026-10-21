package com.mercuriusxeno.goo.data;

import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for GooIntExpressionEvaluator's division: the truncated quotient and the remainder it reports
 * while a {@link DivisionRemainders} recording runs.
 */
class GooIntExpressionEvaluatorTest {

    private static final String EIGHTH_OF_BASE = "$base / 8";

    @Test
    void divisionReportsItsRemainderBesideTheUnchangedQuotient() {
        int quotient;
        List<DivisionRemainders.Remainder> remainders;
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            quotient = DivisionRemainders.underType(GooTypes.ROCK, () -> GooIntExpressionEvaluator.resolveValue(
                    new JsonPrimitive(EIGHTH_OF_BASE), Map.of("base", 4)));
            remainders = recording.remainders();
        }
        assertEquals(0, quotient);
        assertEquals(List.of(new DivisionRemainders.Remainder(
                DivisionRemainders.NO_OWNER, EIGHTH_OF_BASE, GooTypes.ROCK, 8, 4)), remainders);
    }

    @Test
    void exactDivisionReportsNothing() {
        List<DivisionRemainders.Remainder> remainders;
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            DivisionRemainders.underType(GooTypes.ROCK, () -> GooIntExpressionEvaluator.resolveValue(
                    new JsonPrimitive(EIGHTH_OF_BASE), Map.of("base", 16)));
            remainders = recording.remainders();
        }
        assertEquals(List.of(), remainders);
    }
}
