package com.mercuriusxeno.goo.ability.program;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.List;
import java.util.Set;

/**
 * One node of a parsed {@link Expr}: a literal, a named runtime variable,
 * an arithmetic operation or a function call. Nodes are records so two
 * parses of the same source compare equal, which is what the codec
 * round-trip relies on.
 */
public sealed interface ExprNode {

    /**
     * Evaluates this node against the given variable scope.
     *
     * @param variables the scope that answers variable names
     * @return the numeric value
     */
    double evaluate(Variables variables);

    /**
     * Adds every variable name this node reads into the set.
     *
     * @param into the set collecting names
     */
    void collectVariables(Set<String> into);

    /**
     * A numeric literal.
     *
     * @param value the number as written
     */
    record Literal(double value) implements ExprNode {

        @Override
        public double evaluate(Variables variables) {
            return value;
        }

        @Override
        public void collectVariables(Set<String> into) {
        }
    }

    /**
     * A named runtime variable. A name the scope does not bind reads as
     * zero with a warning, the same answer the goo value expressions give
     * an unknown constant.
     *
     * @param name the variable name
     */
    record Variable(String name) implements ExprNode {

        private static final Logger LOGGER = LogUtils.getLogger();
        private static final String LOG_UNBOUND = "Step expression names '{}', which this host does not bind; reading 0";

        @Override
        public double evaluate(Variables variables) {
            return variables.read(name).orElseGet(this::warnUnbound);
        }

        /**
         * Logs the unbound name and answers the fallback value.
         *
         * @return zero
         */
        private double warnUnbound() {
            LOGGER.warn(LOG_UNBOUND, name);
            return 0;
        }

        @Override
        public void collectVariables(Set<String> into) {
            into.add(name);
        }
    }

    /**
     * Unary minus.
     *
     * @param inner the negated node
     */
    record Negate(ExprNode inner) implements ExprNode {

        @Override
        public double evaluate(Variables variables) {
            return -inner.evaluate(variables);
        }

        @Override
        public void collectVariables(Set<String> into) {
            inner.collectVariables(into);
        }
    }

    /**
     * A binary arithmetic operation.
     *
     * @param op    the operator
     * @param left  the left operand
     * @param right the right operand
     */
    record Binary(ExprOperator op, ExprNode left, ExprNode right) implements ExprNode {

        @Override
        public double evaluate(Variables variables) {
            return op.apply(left.evaluate(variables), right.evaluate(variables));
        }

        @Override
        public void collectVariables(Set<String> into) {
            left.collectVariables(into);
            right.collectVariables(into);
        }
    }

    /**
     * A call of a named function over its arguments.
     *
     * @param function the function
     * @param args     the argument nodes, as many as the function takes
     */
    record Call(ExprFunction function, List<ExprNode> args) implements ExprNode {

        @Override
        public double evaluate(Variables variables) {
            double[] values = new double[args.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = args.get(i).evaluate(variables);
            }
            return function.apply(values);
        }

        @Override
        public void collectVariables(Set<String> into) {
            for (ExprNode arg : args) {
                arg.collectVariables(into);
            }
        }
    }
}
