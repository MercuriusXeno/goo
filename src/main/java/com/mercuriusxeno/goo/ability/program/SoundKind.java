package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The sound category a {@link SoundStep} plays under; the host maps each
 * to the level's sound source.
 */
public enum SoundKind {
    /**
     * Block sounds.
     */
    BLOCKS,
    /**
     * Hostile creature sounds.
     */
    HOSTILE,
    /**
     * Player sounds.
     */
    PLAYERS;

    private static final String WHAT = "sound source";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<SoundKind> CODEC = LowerCaseEnumCodec.of(SoundKind.class, WHAT);
}
