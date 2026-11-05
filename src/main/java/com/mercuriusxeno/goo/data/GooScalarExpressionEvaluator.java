package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent evaluator for per-type scalar expressions inside goo value JSON. Handles $constants,
 * dot-notation item references, parentheses, and the four arithmetic operators with standard precedence
 * (* / before + -). Every step is exact, so a division keeps its fraction into the steps after it
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
final class GooScalarExpressionEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String OP_ADD = "+";
    private static final String OP_SUB = "-";
    private static final String OP_MUL = "*";
    private static final String OP_DIV = "/";
    private static final String TOKEN_OPEN = "(";
    private static final String TOKEN_CLOSE = ")";
    private static final String WARN_MISSING_PAREN = "Missing closing parenthesis in expression";
    private static final String CONSTANT_PREFIX = "$";
    private static final char DOT = '.';
    private static final String WARN_UNKNOWN_CONSTANT = "Unknown constant: ${}";
    private static final String WARN_UNKNOWN_OP = "Unknown operator in constant expression: {}";

    private GooScalarExpressionEvaluator() {
    }

    /**
     * Resolves a JSON element to an exact amount using only scalar constants.
     *
     * @param element   the JSON element to resolve
     * @param constants scalar constant symbol table
     * @return the resolved amount
     */
    static GooFraction resolveValue(JsonElement element, Map<String, GooFraction> constants) {
        return resolveValue(element, constants, null, Map.of());
    }

    /**
     * Resolves a JSON element to an exact amount with all constant and item lookup sources.
     *
     * @param element       the JSON element to resolve
     * @param constants     scalar constant symbol table
     * @param baseValues    item values for dot-notation lookups (may be null)
     * @param treeConstants tree constant symbol table for $name.type lookups
     * @return the resolved amount
     */
    static GooFraction resolveValue(JsonElement element, Map<String, GooFraction> constants,
                                    @Nullable Map<Identifier, GooValue> baseValues,
                                    Map<String, GooValue> treeConstants) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return GooFraction.of(element.getAsLong());
        }
        List<String> tokens = ExpressionTokenizer.tokenize(element.getAsString().trim());
        return new Walk(tokens, constants, baseValues, treeConstants).expression();
    }

    /**
     * One evaluation's tokens, its place among them and the tables its operands read.
     */
    private static final class Walk {
        private final List<String> tokens;
        private final Map<String, GooFraction> constants;
        private final @Nullable Map<Identifier, GooValue> baseValues;
        private final Map<String, GooValue> treeConstants;
        private int pos;

        Walk(List<String> tokens, Map<String, GooFraction> constants, @Nullable Map<Identifier, GooValue> baseValues,
             Map<String, GooValue> treeConstants) {
            this.tokens = tokens;
            this.constants = constants;
            this.baseValues = baseValues;
            this.treeConstants = treeConstants;
        }

        // Additive level: + and -, each side a term.
        GooFraction expression() {
            GooFraction result = term();
            while (pos < tokens.size() && (OP_ADD.equals(tokens.get(pos)) || OP_SUB.equals(tokens.get(pos)))) {
                String op = tokens.get(pos++);
                result = apply(result, op, term());
            }
            return result;
        }

        // Multiplicative level: * and /, plus implicit multiplication (e.g. "4 $base").
        private GooFraction term() {
            GooFraction result = atom();
            while (pos < tokens.size() && (isMultiplicativeOp(tokens.get(pos)) || isAtomStart(tokens.get(pos)))) {
                String op = isMultiplicativeOp(tokens.get(pos)) ? tokens.get(pos++) : OP_MUL;
                result = apply(result, op, atom());
            }
            return result;
        }

        // Unary minus, a parenthesized sub-expression, or an operand.
        private GooFraction atom() {
            if (pos < tokens.size() && OP_SUB.equals(tokens.get(pos))) {
                pos++;
                return atom().negated();
            }
            String token = tokens.get(pos++);
            if (TOKEN_OPEN.equals(token)) {
                GooFraction inner = expression();
                if (pos < tokens.size() && TOKEN_CLOSE.equals(tokens.get(pos))) {
                    pos++;
                } else {
                    LOGGER.warn(WARN_MISSING_PAREN);
                }
                return inner;
            }
            return operand(token);
        }

        // A $constant (with an optional .type for tree extraction), a whole literal, or an item.type reference.
        private GooFraction operand(String token) {
            if (token.startsWith(CONSTANT_PREFIX)) {
                return constantOperand(token.substring(1));
            }
            if (Character.isDigit(token.charAt(0)) || baseValues == null) {
                return GooFraction.of(Long.parseLong(token));
            }
            return ItemOperandResolver.resolveItemOperand(token, baseValues);
        }

        private GooFraction constantOperand(String name) {
            int dotIdx = name.lastIndexOf(DOT);
            if (dotIdx > 0 && dotIdx < name.length() - 1) {
                GooFraction extracted = treeDotExtraction(name.substring(0, dotIdx), name.substring(dotIdx + 1));
                if (extracted != null) {
                    return extracted;
                }
            }
            GooFraction value = constants.get(name);
            if (value == null) {
                LOGGER.warn(WARN_UNKNOWN_CONSTANT, name);
                return GooFraction.ZERO;
            }
            return value;
        }

        // A tree constant's one type, exact; null where the name holds no tree or the suffix names no goo type.
        private @Nullable GooFraction treeDotExtraction(String tree, String typeSuffix) {
            GooValue source = treeConstants.get(tree);
            if (source == null) {
                return null;
            }
            try {
                ResourceKey<GooTypeDefinition> type = GooTypes.parseKnown(typeSuffix);
                return source.exact(type);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static boolean isMultiplicativeOp(String op) {
        return OP_MUL.equals(op) || OP_DIV.equals(op);
    }

    // True if the token can start an atom (not an operator or closing paren).
    private static boolean isAtomStart(String token) {
        char c = token.charAt(0);
        return c == '(' || c == '$' || Character.isDigit(c) || Character.isLetter(c);
    }

    /**
     * Applies an arithmetic operator to two exact operands; division by zero answers zero rather than throwing.
     *
     * @param base    the left operand
     * @param op      the operator string (+, -, *, /)
     * @param operand the right operand
     * @return the exact result
     */
    static GooFraction apply(GooFraction base, String op, GooFraction operand) {
        return switch (op) {
            case OP_MUL -> base.times(operand);
            case OP_ADD -> base.plus(operand);
            case OP_SUB -> base.minus(operand);
            case OP_DIV -> operand.isZero() ? GooFraction.ZERO : base.dividedBy(operand);
            default -> {
                LOGGER.warn(WARN_UNKNOWN_OP, op);
                yield base;
            }
        };
    }
}
