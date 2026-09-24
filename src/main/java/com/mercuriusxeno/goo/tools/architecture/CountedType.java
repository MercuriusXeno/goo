package com.mercuriusxeno.goo.tools.architecture;

/**
 * A type and the count a grader read off it.
 *
 * @param type  the scanned type
 * @param count the count, such as its members or its instanceof checks
 */
public record CountedType(ScannedType type, int count) {
}