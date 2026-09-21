package com.mercuriusxeno.goo.ability.program;

import org.jspecify.annotations.Nullable;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The functions a step expression may call, each with a fixed arity the
 * parser checks at load.
 */
public enum ExprFunction {
    /**
     * {@code pow(base, exponent)}.
     */
    POW(2, args -> Math.pow(args[0], args[1])),
    /**
     * {@code min(a, b)}.
     */
    MIN(2, args -> Math.min(args[0], args[1])),
    /**
     * {@code max(a, b)}.
     */
    MAX(2, args -> Math.max(args[0], args[1])),
    /**
     * {@code sqrt(a)}.
     */
    SQRT(1, args -> Math.sqrt(args[0])),
    /**
     * {@code floor(a)}.
     */
    FLOOR(1, args -> Math.floor(args[0])),
    /**
     * {@code ceil(a)}.
     */
    CEIL(1, args -> Math.ceil(args[0]));

    private final int arity;
    private final ToDoubleFunction<double[]> body;

    ExprFunction(int arity, ToDoubleFunction<double[]> body) {
        this.arity = arity;
        this.body = body;
    }

    /**
     * Finds the function written with the given lower-case name.
     *
     * @param name the name as written in the expression
     * @return the function, or null when no function carries the name
     */
    public static @Nullable ExprFunction byName(String name) {
        for (ExprFunction fn : values()) {
            if (fn.key().equals(name)) {
                return fn;
            }
        }
        return null;
    }

    /**
     * Returns the name this function is written with.
     *
     * @return the lower-case name
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns how many arguments the function takes.
     *
     * @return the arity
     */
    public int arity() {
        return arity;
    }

    /**
     * Applies the function to evaluated arguments.
     *
     * @param args the argument values, as many as {@link #arity()}
     * @return the result
     */
    public double apply(double[] args) {
        return body.applyAsDouble(args);
    }
}
