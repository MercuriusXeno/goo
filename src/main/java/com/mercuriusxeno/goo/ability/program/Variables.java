package com.mercuriusxeno.goo.ability.program;

import java.util.OptionalDouble;

/**
 * The runtime values an {@link Expr} may name. A host binds the variables
 * it can answer (stack count, health, distance); the program runtime adds
 * its own (elapsed ticks) in front of the host.
 */
@FunctionalInterface
public interface Variables {

    /**
     * Reads a variable by name.
     *
     * @param name the variable name as written in the expression
     * @return the value, or empty when this scope does not bind the name
     */
    OptionalDouble read(String name);

    /**
     * A scope that binds nothing, for literal-only expressions.
     */
    Variables NONE = name -> OptionalDouble.empty();

    /**
     * Chains this scope in front of another: names this scope leaves
     * empty are read from the fallback.
     *
     * @param fallback the scope consulted when this one is empty
     * @return the layered scope
     */
    default Variables thenRead(Variables fallback) {
        return name -> {
            OptionalDouble mine = read(name);
            return mine.isPresent() ? mine : fallback.read(name);
        };
    }
}
