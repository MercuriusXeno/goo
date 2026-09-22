package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The point an FX step spawns at. Each anchor is a center: the marker
 * block's center for {@link #HOST} on the marker, the target's body
 * center for {@link #TARGET}; on the struck entity host both are the
 * target, since the target is the host.
 */
public enum FxAnchor {
    /**
     * The host's own center.
     */
    HOST,
    /**
     * The current target's body center.
     */
    TARGET;

    private static final String WHAT = "fx anchor";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<FxAnchor> CODEC = LowerCaseEnumCodec.of(FxAnchor.class, WHAT);
}
