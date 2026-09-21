package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Expr parses a JSON number or an expression string and evaluates against
 * the host's variables at tick time; a string outside the grammar refuses
 * at decode.
 */
class ExprTest {

    private static final double EPSILON = 1e-9;

    private static Expr decode(JsonElement json) {
        return Expr.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static Expr parse(String source) {
        return Expr.parse(source).getOrThrow();
    }

    private static StepHost hostWithStacks(int stacks) {
        StepHost host = mock(StepHost.class);
        when(host.read("stacks")).thenReturn(OptionalDouble.of(stacks));
        return host;
    }

    /**
     * Literal and expression params both decode and evaluate.
     */
    @Nested
    class Decoding {

        @Test
        void literalNumberStillParses() {
            Expr expr = decode(new JsonPrimitive(3.5));
            assertEquals(3.5, expr.evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void expressionOverStackCountEvaluatesAgainstHost() {
            Expr expr = decode(new JsonPrimitive("2 + 1.5 * (stacks - 1)"));
            assertEquals(6.5, expr.evaluate(hostWithStacks(4)), EPSILON);
        }

        @Test
        void sameExpressionFollowsTheHostValue() {
            Expr expr = parse("stacks * 2");
            assertEquals(2, expr.evaluate(hostWithStacks(1)), EPSILON);
            assertEquals(12, expr.evaluate(hostWithStacks(6)), EPSILON);
        }

        @Test
        void literalEncodesAsNumber() {
            JsonElement json = Expr.CODEC.encodeStart(JsonOps.INSTANCE, Expr.literal(4)).getOrThrow();
            assertTrue(json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber());
        }

        @Test
        void expressionEncodesAsItsSource() {
            Expr expr = parse("stacks + 1");
            JsonElement json = Expr.CODEC.encodeStart(JsonOps.INSTANCE, expr).getOrThrow();
            assertEquals("stacks + 1", json.getAsString());
        }

        @Test
        void roundTripPreservesEquality() {
            Expr expr = parse("max(stacks, 2) / 4");
            JsonElement json = Expr.CODEC.encodeStart(JsonOps.INSTANCE, expr).getOrThrow();
            assertEquals(expr, decode(json));
        }

        @Test
        void variablesNamesEveryRead() {
            assertEquals(Set.of("stacks", "tick"), parse("stacks + tick * stacks").variables());
        }
    }

    /**
     * Arithmetic follows the usual precedence and the goo value conventions.
     */
    @Nested
    class Arithmetic {

        @Test
        void multiplicationBindsTighterThanAddition() {
            assertEquals(14, parse("2 + 3 * 4").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void parenthesesGroup() {
            assertEquals(20, parse("(2 + 3) * 4").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void unaryMinusNegates() {
            assertEquals(-5, parse("-(2 + 3)").evaluate(Variables.NONE), EPSILON);
            assertEquals(1, parse("3 + -2").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void subtractionIsLeftAssociative() {
            assertEquals(4, parse("10 - 3 - 3").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void divisionByZeroReadsZero() {
            assertEquals(0, parse("5 / 0").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void functionsApply() {
            assertEquals(8, parse("pow(2, 3)").evaluate(Variables.NONE), EPSILON);
            assertEquals(2, parse("min(2, 7)").evaluate(Variables.NONE), EPSILON);
            assertEquals(7, parse("max(2, 7)").evaluate(Variables.NONE), EPSILON);
            assertEquals(3, parse("sqrt(9)").evaluate(Variables.NONE), EPSILON);
            assertEquals(2, parse("floor(2.9)").evaluate(Variables.NONE), EPSILON);
            assertEquals(3, parse("ceil(2.1)").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void unboundVariableReadsZero() {
            assertEquals(3, parse("3 + nowhere").evaluate(Variables.NONE), EPSILON);
        }

        @Test
        void evaluateIntFloors() {
            assertEquals(2, parse("2.9").evaluateInt(Variables.NONE));
        }
    }

    /**
     * Text outside the grammar refuses at decode rather than at tick time.
     */
    @Nested
    class Refusals {

        private static void assertRefused(String source) {
            DataResult<Expr> result = Expr.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(source));
            assertTrue(result.isError(), source);
        }

        @Test
        void unknownFunctionRefuses() {
            assertRefused("cube(2)");
        }

        @Test
        void wrongArityRefuses() {
            assertRefused("pow(2)");
        }

        @Test
        void strayCharacterRefuses() {
            assertRefused("2 $ 3");
        }

        @Test
        void trailingOperandRefuses() {
            assertRefused("2 3");
        }

        @Test
        void unclosedParenthesisRefuses() {
            assertRefused("(2 + 3");
        }

        @Test
        void emptyStringRefuses() {
            assertRefused("   ");
        }

        @Test
        void danglingOperatorRefuses() {
            assertRefused("2 +");
        }
    }
}
