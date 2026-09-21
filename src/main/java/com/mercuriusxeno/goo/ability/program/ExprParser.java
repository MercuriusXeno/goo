package com.mercuriusxeno.goo.ability.program;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser over {@link ExprToken}s producing an
 * {@link ExprNode} tree. Grammar, loosest first: additive over
 * multiplicative over unary minus over a primary, where a primary is a
 * number, a variable name, a function call or a parenthesized expression.
 */
final class ExprParser {

    private static final char OPEN_PAREN = '(';
    private static final char CLOSE_PAREN = ')';
    private static final char COMMA = ',';
    private static final char MINUS = '-';
    private static final String ERR_EMPTY = "Step expression is empty";
    private static final String ERR_TRAILING = "Unexpected '%s' after the end of step expression: %s";
    private static final String ERR_EXPECTED = "Expected %s in step expression: %s";
    private static final String ERR_NUMBER = "Bad number '%s' in step expression: %s";
    private static final String ERR_UNKNOWN_FUNCTION = "Unknown function '%s' in step expression: %s";
    private static final String ERR_ARITY = "Function '%s' takes %d arguments in step expression: %s";
    private static final String WHAT_OPERAND = "an operand";
    private static final String WHAT_CLOSE = "')'";

    private final String source;
    private final List<ExprToken> tokens;
    private int position;

    private ExprParser(String source, List<ExprToken> tokens) {
        this.source = source;
        this.tokens = tokens;
    }

    /**
     * Parses the whole expression.
     *
     * @param source the expression text
     * @return the root node
     * @throws ExprParseException when the text is not an expression
     */
    static ExprNode parse(String source) {
        List<ExprToken> tokens = ExprTokenizer.tokenize(source);
        if (tokens.isEmpty()) {
            throw new ExprParseException(ERR_EMPTY);
        }
        ExprParser parser = new ExprParser(source, tokens);
        ExprNode root = parser.parseAdditive();
        ExprToken trailing = parser.peek();
        if (trailing != null) {
            throw new ExprParseException(String.format(ERR_TRAILING, trailing.text(), source));
        }
        return root;
    }

    /**
     * Parses a chain of additive operations, left associative.
     *
     * @return the node
     */
    private ExprNode parseAdditive() {
        ExprNode left = parseMultiplicative();
        ExprOperator op = peekOperator(false);
        while (op != null) {
            position++;
            left = new ExprNode.Binary(op, left, parseMultiplicative());
            op = peekOperator(false);
        }
        return left;
    }

    /**
     * Parses a chain of multiplicative operations, left associative.
     *
     * @return the node
     */
    private ExprNode parseMultiplicative() {
        ExprNode left = parseUnary();
        ExprOperator op = peekOperator(true);
        while (op != null) {
            position++;
            left = new ExprNode.Binary(op, left, parseUnary());
            op = peekOperator(true);
        }
        return left;
    }

    /**
     * Parses an optional unary minus over a primary.
     *
     * @return the node
     */
    private ExprNode parseUnary() {
        ExprToken token = peek();
        if (token != null && token.isSymbol(MINUS)) {
            position++;
            return new ExprNode.Negate(parseUnary());
        }
        return parsePrimary();
    }

    /**
     * Parses a number, a name (variable or call) or a parenthesized expression.
     *
     * @return the node
     */
    private ExprNode parsePrimary() {
        ExprToken token = peek();
        if (token == null) {
            throw expected(WHAT_OPERAND);
        }
        position++;
        return switch (token.kind()) {
            case NUMBER -> parseNumber(token.text());
            case NAME -> parseNameOrCall(token.text());
            case SYMBOL -> parseParenthesized(token);
        };
    }

    /**
     * Turns a number token into a literal.
     *
     * @param text the token text
     * @return the literal node
     */
    private ExprNode parseNumber(String text) {
        try {
            return new ExprNode.Literal(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            throw new ExprParseException(String.format(ERR_NUMBER, text, source), e);
        }
    }

    /**
     * Turns a name token into a variable, or a call when a parenthesis follows.
     *
     * @param name the token text
     * @return the variable or call node
     */
    private ExprNode parseNameOrCall(String name) {
        ExprToken next = peek();
        if (next == null || !next.isSymbol(OPEN_PAREN)) {
            return new ExprNode.Variable(name);
        }
        position++;
        ExprFunction function = ExprFunction.byName(name);
        if (function == null) {
            throw new ExprParseException(String.format(ERR_UNKNOWN_FUNCTION, name, source));
        }
        List<ExprNode> args = parseArguments();
        if (args.size() != function.arity()) {
            throw new ExprParseException(String.format(ERR_ARITY, name, function.arity(), source));
        }
        return new ExprNode.Call(function, args);
    }

    /**
     * Parses comma-separated arguments up to and including the closing parenthesis.
     *
     * @return the argument nodes
     */
    private List<ExprNode> parseArguments() {
        List<ExprNode> args = new ArrayList<>();
        args.add(parseAdditive());
        ExprToken token = peek();
        while (token != null && token.isSymbol(COMMA)) {
            position++;
            args.add(parseAdditive());
            token = peek();
        }
        expectClose();
        return args;
    }

    /**
     * Parses the expression inside parentheses; any other symbol here is an error.
     *
     * @param open the symbol token already consumed
     * @return the inner node
     */
    private ExprNode parseParenthesized(ExprToken open) {
        if (!open.isSymbol(OPEN_PAREN)) {
            throw expected(WHAT_OPERAND);
        }
        ExprNode inner = parseAdditive();
        expectClose();
        return inner;
    }

    /**
     * Consumes a closing parenthesis or fails.
     */
    private void expectClose() {
        ExprToken token = peek();
        if (token == null || !token.isSymbol(CLOSE_PAREN)) {
            throw expected(WHAT_CLOSE);
        }
        position++;
    }

    /**
     * Peeks the operator at the cursor when it has the asked precedence class.
     *
     * @param multiplicative true to accept only multiply and divide, false for add and subtract
     * @return the operator, or null when the cursor holds something else
     */
    private @Nullable ExprOperator peekOperator(boolean multiplicative) {
        ExprToken token = peek();
        if (token == null || token.kind() != ExprToken.Kind.SYMBOL) {
            return null;
        }
        ExprOperator op = ExprOperator.bySymbol(token.text().charAt(0));
        if (op == null || op.isMultiplicative() != multiplicative) {
            return null;
        }
        return op;
    }

    /**
     * Returns the token at the cursor without consuming it.
     *
     * @return the token, or null at the end
     */
    private @Nullable ExprToken peek() {
        return position < tokens.size() ? tokens.get(position) : null;
    }

    /**
     * Builds the error for a missing construct.
     *
     * @param what the construct expected
     * @return the exception to throw
     */
    private ExprParseException expected(String what) {
        return new ExprParseException(String.format(ERR_EXPECTED, what, source));
    }
}
