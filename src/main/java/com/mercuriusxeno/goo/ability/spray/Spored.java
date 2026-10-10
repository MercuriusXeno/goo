package com.mercuriusxeno.goo.ability.spray;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * The spores a mob carries until it dies or they fade: the ability whose
 * spray bursts from its corpse, how far the burst reaches, and the game time
 * the spores fade at (decision mycosis-spore-stream-buds-and-poisons).
 *
 * @param burst     the ability the corpse bursts
 * @param radius    the burst's reach in blocks
 * @param expiresAt the game time the spores fade at
 */
public record Spored(Identifier burst, float radius, long expiresAt) {

    /** No spores: nothing bursts. */
    public static final Spored NONE = new Spored(Identifier.withDefaultNamespace("air"), 0f, 0L);

    private static final String FIELD_BURST = "burst";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the spores with the mob. */
    public static final MapCodec<Spored> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_BURST).forGetter(Spored::burst),
            Codec.FLOAT.fieldOf(FIELD_RADIUS).forGetter(Spored::radius),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Spored::expiresAt)
    ).apply(inst, Spored::new));

    /**
     * Whether the spores still stand at a game time.
     *
     * @param gameTime the game time
     * @return true before the spores fade
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }
}
