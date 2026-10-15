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
     * Breaks and drops blocks like TNT, and lights fire in about one in three
     * of the cleared cells standing on a solid block, as vanilla's fiery
     * explosions do (decision ignite-fire-and-explosive-combo).
     */
    FIRE,
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
