package com.mercuriusxeno.goo.tools.architecture;

/**
 * A type whose fan-in or fan-out is an outlier by the twentieth rule.
 *
 * @param type   the scanned type
 * @param fanIn  how many graded types reference it
 * @param fanOut how many graded types it references
 */
public record CouplingHub(ScannedType type, int fanIn, int fanOut) {
}