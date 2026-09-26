package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;

/**
 * The wide-surface types and the threshold that graded them.
 *
 * @param threshold the member count all but a twentieth of the set hold at or under
 * @param types     the types above it, widest first
 */
public record WideSurface(int threshold, List<CountedType> types) {
}