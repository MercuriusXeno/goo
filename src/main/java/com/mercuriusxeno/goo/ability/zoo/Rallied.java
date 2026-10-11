package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;

/**
 * The rally a peaceful mob holds until it fades: the caster it fights for,
 * the game time it fades at, and the damage each of its strikes deals.
 * Saved with the mob.
 * zoo-rally-arms-the-peaceful
 *
 * @param caster    the entity the mob fights for
 * @param expiresAt the game time the rally fades at
 * @param damage    the damage one strike deals
 */
public record Rallied(UUID caster, long expiresAt, float damage) {

    /** No rally: the mob fights for no one. */
    public static final Rallied NONE = new Rallied(new UUID(0L, 0L), 0L, 0f);

    private static final String FIELD_CASTER = "caster";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_DAMAGE = "damage";

    /** Saves the rally with the mob. */
    public static final MapCodec<Rallied> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CASTER).forGetter(Rallied::caster),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Rallied::expiresAt),
            Codec.FLOAT.fieldOf(FIELD_DAMAGE).forGetter(Rallied::damage)
    ).apply(inst, Rallied::new));

    /**
     * Whether the rally still stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the rally fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }
}
