package com.mercuriusxeno.goo.tools.architecture;

/**
 * A constructor or method whose parameter list runs past the long-parameter-list bound.
 *
 * @param type       the declaring type
 * @param member     the member's name, the type's simple name for a constructor
 * @param parameters how many parameters it takes
 */
public record LongSignature(ScannedType type, String member, int parameters) {
}