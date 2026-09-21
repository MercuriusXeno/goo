package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The volume a selection step scans around its anchor.
 */
public enum SelectionShape {
    /**
     * Everything within the radius by Euclidean distance.
     */
    SPHERE,
    /**
     * Everything inside the axis-aligned box the radius spans.
     */
    CUBE;

    private static final String WHAT = "selection shape";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<SelectionShape> CODEC = LowerCaseEnumCodec.of(SelectionShape.class, WHAT);
}
