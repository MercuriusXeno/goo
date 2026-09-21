package com.mercuriusxeno.goo.ability.program;

/**
 * One token of a step expression: a number, a name, or a single-character
 * symbol (an operator, a parenthesis or a comma).
 *
 * @param kind the token class
 * @param text the characters the token covers
 */
record ExprToken(Kind kind, String text) {

    /**
     * Returns true when this token is the given symbol character.
     *
     * @param symbol the symbol to test
     * @return true for a symbol token spelling that character
     */
    boolean isSymbol(char symbol) {
        return kind == Kind.SYMBOL && text.length() == 1 && text.charAt(0) == symbol;
    }

    /**
     * The token classes.
     */
    enum Kind {
        /**
         * A numeric literal, integer or decimal.
         */
        NUMBER,
        /**
         * A variable or function name.
         */
        NAME,
        /**
         * An operator, parenthesis or comma.
         */
        SYMBOL
    }
}
