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
    CACTUS,
    /**
     * Blunt force, rock crush's source (decision crush-blob-breaks-along-its-strike).
     */
    FORCE,
    /**
     * The thrower's own attack, a player's where a player threw it, so a
     * kill drops its experience and loot as the thrower's kill; generic
     * where no living thrower stands (decision bore-vortex-with-a-worldspace-shake).
     */
    ATTACK,
    /**
     * Expire's goo:expire, bypassing armor, resistance, effects and
     * enchantments, so the struck mob dies whatever it wears.
     * expire-kills-the-struck-mob
     */
    EXPIRE;

    private static final String WHAT = "damage source";

    /**
     * Codec reading the lower-case name.
     */
    public static final Codec<DamageKind> CODEC = LowerCaseEnumCodec.of(DamageKind.class, WHAT);
}
