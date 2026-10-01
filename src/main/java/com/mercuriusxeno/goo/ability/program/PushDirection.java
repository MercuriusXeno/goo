package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * Which way a {@link PushStep} sends its target.
 */
public enum PushDirection {
    /**
     * Along the thrower's look; on a player host the player is its own
     * thrower, so it is propelled where it looks
     * (decision self-delivery-runs-on-player).
     */
    THROWER_LOOK;

    private static final String WHAT = "push direction";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<PushDirection> CODEC = LowerCaseEnumCodec.of(PushDirection.class, WHAT);
}
