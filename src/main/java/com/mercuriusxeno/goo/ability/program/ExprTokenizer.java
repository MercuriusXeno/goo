package com.mercuriusxeno.goo.ability.program;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a step expression into {@link ExprToken}s. Numbers may carry a
 * decimal point, names are letters, digits and underscores starting with
 * a letter or underscore, and the symbols are the four operators, the
 * parentheses and the comma. Any other character refuses the expression.
 *
 * <p>This stands beside {@code data.ExpressionTokenizer} rather than
 * reusing it: that tokenizer treats {@code -} and {@code /} as characters
 * of a namespaced id, which would swallow {@code stacks - 1} whole.
 */
final class ExprTokenizer {

    private static final String SYMBOLS = "+-*/(),";
    private static final String ERR_UNEXPECTED = "Unexpected character '%s' in step expression: %s";

    private ExprTokenizer() {
    }

    /**
     * Tokenizes the expression.
     *
     * @param source the expression text
     * @return the tokens in order
     * @throws ExprParseException on a character outside the grammar
     */
    static List<ExprToken> tokenize(String source) {
        List<ExprToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < source.length()) {
            i = scanToken(source, i, tokens);
        }
        return tokens;
    }

    /**
     * Scans the token starting at the given position, skipping whitespace.
     *
     * @param source the expression text
     * @param start  the position to scan from
     * @param tokens the list receiving the token
     * @return the position after the token
     */
    private static int scanToken(String source, int start, List<ExprToken> tokens) {
        char c = source.charAt(start);
        if (Character.isWhitespace(c)) {
            return start + 1;
        }
        if (SYMBOLS.indexOf(c) >= 0) {
            tokens.add(new ExprToken(ExprToken.Kind.SYMBOL, String.valueOf(c)));
            return start + 1;
        }
        if (isNumberChar(c)) {
            return scanRun(source, start, ExprToken.Kind.NUMBER, tokens);
        }
        if (isNameStart(c)) {
            return scanRun(source, start, ExprToken.Kind.NAME, tokens);
        }
        throw new ExprParseException(String.format(ERR_UNEXPECTED, c, source));
    }

    /**
     * Scans a run of number or name characters into one token.
     *
     * @param source the expression text
     * @param start  the first character of the run
     * @param kind   the token class, which picks the character test
     * @param tokens the list receiving the token
     * @return the position after the run
     */
    private static int scanRun(String source, int start, ExprToken.Kind kind, List<ExprToken> tokens) {
        int end = start + 1;
        while (end < source.length() && continuesRun(source.charAt(end), kind)) {
            end++;
        }
        tokens.add(new ExprToken(kind, source.substring(start, end)));
        return end;
    }

    /**
     * Tells whether the character extends a run of the given token class.
     *
     * @param c    the character
     * @param kind the token class being scanned
     * @return true when the character belongs to the run
     */
    private static boolean continuesRun(char c, ExprToken.Kind kind) {
        return kind == ExprToken.Kind.NUMBER ? isNumberChar(c) : isNameChar(c);
    }

    /**
     * Tells whether the character may appear in a numeric literal.
     *
     * @param c the character
     * @return true for a digit or a decimal point
     */
    private static boolean isNumberChar(char c) {
        return Character.isDigit(c) || c == '.';
    }

    /**
     * Tells whether the character may start a name.
     *
     * @param c the character
     * @return true for a letter or an underscore
     */
    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    /**
     * Tells whether the character may continue a name.
     *
     * @param c the character
     * @return true for a letter, a digit, an underscore or the colon of a counter id
     */
    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':';
    }
}
