package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;

/**
 * The kind of heart overlay a brew lays over the player's health bar, which
 * names the overlay's rules and its sprites (decision
 * overlay-hearts-are-an-elemental-overshield).
 */
public enum HeartKind {
    /**
     * Blaze Kindle: ember hearts shield the bar and break to ash (decision
     * kindle-ember-hearts-ash-and-retaliate).
     */
    KINDLE;

    /**
     * Codec for the kind, written as its lower-case name.
     */
    public static final Codec<HeartKind> CODEC = LowerCaseEnumCodec.of(HeartKind.class, "heart kind");
}
