package com.mercuriusxeno.goo.ability.program;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jspecify.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * A numeric step param: a JSON number, or a string expression over runtime
 * variables evaluated against the host at tick time (decision
 * ability-params-in-datapack). The source text is kept so the codec writes
 * an expression back as it was read.
 *
 * @param source the expression text, or null for a literal written as a number
 * @param root   the parsed tree
 */
public record Expr(@Nullable String source, ExprNode root) {

    /**
     * Codec accepting a number or an expression string. A string that does
     * not parse fails the decode, so a bad expression refuses at load.
     */
    public static final Codec<Expr> CODEC = Codec.either(Codec.DOUBLE, Codec.STRING)
            .comapFlatMap(Expr::fromEither, Expr::toEither);

    /**
     * Builds a literal expression.
     *
     * @param value the constant value
     * @return the expression
     */
    public static Expr literal(double value) {
        return new Expr(null, new ExprNode.Literal(value));
    }

    /**
     * Parses an expression string.
     *
     * @param source the expression text
     * @return the expression, or an error naming what refused
     */
    public static DataResult<Expr> parse(String source) {
        try {
            return DataResult.success(new Expr(source, ExprParser.parse(source)));
        } catch (ExprParseException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Decodes the codec's either: a number is a literal, a string is parsed.
     *
     * @param either the raw value
     * @return the expression or the parse error
     */
    private static DataResult<Expr> fromEither(Either<Double, String> either) {
        return either.map(value -> DataResult.success(literal(value)), Expr::parse);
    }

    /**
     * Encodes for the codec: a literal without source writes as a number.
     *
     * @param expr the expression
     * @return the raw value
     */
    private static Either<Double, String> toEither(Expr expr) {
        if (expr.source == null && expr.root instanceof ExprNode.Literal literal) {
            return Either.left(literal.value());
        }
        return Either.right(expr.source == null ? String.valueOf(expr.evaluate(Variables.NONE)) : expr.source);
    }

    /**
     * Evaluates against the given scope.
     *
     * @param variables the scope answering variable names
     * @return the value
     */
    public double evaluate(Variables variables) {
        return root.evaluate(variables);
    }

    /**
     * Evaluates and narrows to float, the width most game calls take.
     *
     * @param variables the scope answering variable names
     * @return the value as a float
     */
    public float evaluateFloat(Variables variables) {
        return (float) evaluate(variables);
    }

    /**
     * Evaluates and rounds down to an int, for tick counts and sizes.
     *
     * @param variables the scope answering variable names
     * @return the value floored to an int
     */
    public int evaluateInt(Variables variables) {
        return (int) Math.floor(evaluate(variables));
    }

    /**
     * Names every variable the expression reads, for load-time checks of
     * what a host must bind.
     *
     * @return the variable names
     */
    public Set<String> variables() {
        Set<String> names = new HashSet<>();
        root.collectVariables(names);
        return names;
    }
}
