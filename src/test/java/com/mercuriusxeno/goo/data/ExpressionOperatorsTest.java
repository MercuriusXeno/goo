package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.data.GooValueExpression.ExprVal;
import com.mercuriusxeno.goo.data.GooValueExpression.GooVal;
import com.mercuriusxeno.goo.data.GooValueExpression.ScalarVal;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for ExpressionOperators' division: the truncated quotient and the remainder it reports, with
 * the type it fell in, while a {@link DivisionRemainders} recording runs.
 */
class ExpressionOperatorsTest {

    private static final String EIGHTH_OF_BASE = "$base / 8";

    @Test
    void scalarDivisionReportsItsRemainderInTheContextType() {
        ExprVal quotient;
        List<DivisionRemainders.Remainder> remainders;
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            quotient = DivisionRemainders.underExpression(EIGHTH_OF_BASE, () -> DivisionRemainders.underType(
                    GooTypes.ROCK, () -> ExpressionOperators.applyOp(
                            new ScalarVal(4), ExpressionOperators.OP_DIVIDE, new ScalarVal(8))));
            remainders = recording.remainders();
        }
        assertEquals(new ScalarVal(0), quotient);
        assertEquals(List.of(new DivisionRemainders.Remainder(
                DivisionRemainders.NO_OWNER, EIGHTH_OF_BASE, GooTypes.ROCK, 8, 4)), remainders);
    }

    @Test
    void gooValueDivisionReportsEachTypeItLosesIn() {
        ExprVal quotient;
        List<DivisionRemainders.Remainder> remainders;
        try (DivisionRemainders.Recording recording = DivisionRemainders.record()) {
            quotient = DivisionRemainders.underExpression(EIGHTH_OF_BASE, () -> ExpressionOperators.applyOp(
                    new GooVal(goo(GooTypes.ROCK, 4, GooTypes.LEAF, 16)), ExpressionOperators.OP_DIVIDE,
                    new ScalarVal(8)));
            remainders = recording.remainders();
        }
        assertEquals(goo(GooTypes.LEAF, 2).getAll(), quotient.toGooValue().getAll());
        assertEquals(List.of(new DivisionRemainders.Remainder(
                DivisionRemainders.NO_OWNER, EIGHTH_OF_BASE, GooTypes.ROCK, 8, 4)), remainders);
    }
}
