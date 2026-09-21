package com.mercuriusxeno.goo.ability.program;

/**
 * Thrown while tokenizing or parsing a step expression; {@link Expr#parse}
 * turns it into a codec error so a bad expression refuses at load.
 */
final class ExprParseException extends RuntimeException {

    /**
     * Creates the exception with a message naming what was expected and
     * what was read.
     *
     * @param message the reason
     */
    ExprParseException(String message) {
        super(message);
    }

    /**
     * Creates the exception over the failure that caused it.
     *
     * @param message the reason
     * @param cause   the underlying failure
     */
    ExprParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
