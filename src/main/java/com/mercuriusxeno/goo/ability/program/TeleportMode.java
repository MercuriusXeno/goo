package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * How a {@link TeleportStep} picks the target's destination. The set
 * grows one mode per migration that needs it; the vocabulary doc lists
 * the target set.
 */
public enum TeleportMode {
    /**
     * A random horizontal offset of up to half the range either way on
     * each axis, at the same height.
     */
    RANDOM_OFFSET,
    /**
     * A jump of the range toward the thrower along the level line between
     * them; no thrower, no move.
     */
    TOWARD_THROWER,
    /**
     * A jump of the range away from the thrower along the level line
     * between them; no thrower, no move.
     */
    AWAY_FROM_THROWER;

    private static final String WHAT = "teleport mode";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<TeleportMode> CODEC = LowerCaseEnumCodec.of(TeleportMode.class, WHAT);
}
