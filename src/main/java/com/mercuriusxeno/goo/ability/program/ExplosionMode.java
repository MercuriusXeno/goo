package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * How an {@link ExplodeStep} interacts with blocks.
 */
public enum ExplosionMode {
    /**
     * Breaks and drops blocks like TNT.
     */
    TNT,
    /**
     * Hurts and pushes entities, leaves blocks standing.
     */
    NONE;

    private static final String WHAT = "explosion mode";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<ExplosionMode> CODEC = LowerCaseEnumCodec.of(ExplosionMode.class, WHAT);
}
