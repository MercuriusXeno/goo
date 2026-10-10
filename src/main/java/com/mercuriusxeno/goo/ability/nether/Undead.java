package com.mercuriusxeno.goo.ability.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Whether a player counts as undead, and the damage direct daylight deals it
 * each second while it does. Undead's start lays it, and the held effect
 * ending clears it, so it carries no expiry of its own
 * (decision undead-nether-hearts-burn-in-sunlight).
 *
 * @param stands    true while the player counts as undead
 * @param sunDamage the damage each second in direct daylight deals
 */
public record Undead(boolean stands, float sunDamage) {

    /** A player who is not undead. */
    public static final Undead NONE = new Undead(false, 0f);

    private static final String FIELD_STANDS = "stands";
    private static final String FIELD_SUN_DAMAGE = "sun_damage";

    /** Codec for the saved state. */
    public static final MapCodec<Undead> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.BOOL.fieldOf(FIELD_STANDS).forGetter(Undead::stands),
            Codec.FLOAT.fieldOf(FIELD_SUN_DAMAGE).forGetter(Undead::sunDamage)
    ).apply(inst, Undead::new));

    /**
     * A player made undead.
     *
     * @param sunDamage the damage each second in direct daylight deals
     * @return the state
     */
    public static Undead of(float sunDamage) {
        return new Undead(true, sunDamage);
    }
}
