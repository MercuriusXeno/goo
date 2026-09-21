package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The damage source a {@link DamageStep} hurts with; the host maps each to
 * the level's damage sources.
 */
public enum DamageKind {
    /**
     * Magic damage, bypassing armor.
     */
    MAGIC,
    /**
     * Freezing damage.
     */
    FREEZE,
    /**
     * Stalagmite damage, the spike trap's source.
     */
    STALAGMITE,
    /**
     * Cactus damage, the sliver cloud's source.
     */
    CACTUS;

    private static final String WHAT = "damage source";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<DamageKind> CODEC = LowerCaseEnumCodec.of(DamageKind.class, WHAT);
}
