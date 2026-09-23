package com.mercuriusxeno.goo.ability.program;

/**
 * An expression paired with the kind of host whose variables it reads, so
 * the load check holds an expression a step evaluates on a selected
 * entity to that entity's host.
 *
 * @param expr the expression
 * @param host the kind of host it is evaluated against
 */
public record HostedExpr(Expr expr, HostKind host) {
}
