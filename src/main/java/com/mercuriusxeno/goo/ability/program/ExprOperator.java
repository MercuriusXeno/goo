package com.mercuriusxeno.goo.ability.program;

import org.jspecify.annotations.Nullable;

/**
 * The binary arithmetic operators a step expression may use. Division by
 * zero answers zero, the answer the goo value expressions give.
 */
public enum ExprOperator {
    /**
     * Addition.
     */
    ADD('+', 1),
    /**
     * Subtraction.
     */
    SUBTRACT('-', 1),
    /**
     * Multiplication.
     */
    MULTIPLY('*', 2),
    /**
     * Division, zero divisor answering zero.
     */
    DIVIDE('/', 2);

    private final char symbol;
    private final int precedence;

    ExprOperator(char symbol, int precedence) {
        this.symbol = symbol;
        this.precedence = precedence;
    }

    /**
     * Finds the operator written with the given symbol.
     *
     * @param symbol the operator character
     * @return the operator, or null when no operator uses the symbol
     */
    public static @Nullable ExprOperator bySymbol(char symbol) {
        for (ExprOperator op : values()) {
            if (op.symbol == symbol) {
                return op;
            }
        }
        return null;
    }

    /**
     * Returns the character that writes this operator.
     *
     * @return the symbol
     */
    public char symbol() {
        return symbol;
    }

    /**
     * Returns true for the multiplicative operators, which bind tighter
     * than the additive ones.
     *
     * @return true for multiply and divide
     */
    public boolean isMultiplicative() {
        return precedence > 1;
    }

    /**
     * Applies the operator.
     *
     * @param left  the left operand
     * @param right the right operand
     * @return the result
     */
    public double apply(double left, double right) {
        return switch (this) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> right == 0 ? 0 : left / right;
        };
    }
}
