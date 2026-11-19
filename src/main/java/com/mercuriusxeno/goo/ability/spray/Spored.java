package com.mercuriusxeno.goo.ability.spray;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.UUID;

/**
 * The spores a mob carries until it dies or they fade: the ability whose
 * spray bursts from its corpse, how far the burst reaches, the game time
 * the spores fade at, whether Spores goo gave them, so the death also turns
 * the ground around it to mycelium, and the player the burst credits
 * (decisions mycosis-spore-stream-buds-and-poisons,
 * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium).
 *
 * @param burst         the ability the corpse bursts
 * @param radius        the burst's reach in blocks
 * @param expiresAt     the game time the spores fade at
 * @param feedsMycelium whether Spores goo gave the spores, its death turning the ground to mycelium
 * @param creditor      the player the burst credits, empty for none
 */
public record Spored(Identifier burst, float radius, long expiresAt, boolean feedsMycelium,
                     Optional<UUID> creditor) {

    /** No spores: nothing bursts. */
    public static final Spored NONE = new Spored(Identifier.withDefaultNamespace("air"), 0f, 0L);

    private static final String FIELD_BURST = "burst";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_FEEDS_MYCELIUM = "feeds_mycelium";
    private static final String FIELD_CREDITOR = "creditor";

    /** Saves the spores with the mob. */
    public static final MapCodec<Spored> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_BURST).forGetter(Spored::burst),
            Codec.FLOAT.fieldOf(FIELD_RADIUS).forGetter(Spored::radius),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Spored::expiresAt),
            Codec.BOOL.optionalFieldOf(FIELD_FEEDS_MYCELIUM, false).forGetter(Spored::feedsMycelium),
            UUIDUtil.CODEC.optionalFieldOf(FIELD_CREDITOR).forGetter(Spored::creditor)
    ).apply(inst, Spored::new));

    /**
     * The stream's spores, which feed no mycelium and credit no one.
     *
     * @param burst     the ability the corpse bursts
     * @param radius    the burst's reach in blocks
     * @param expiresAt the game time the spores fade at
     */
    public Spored(Identifier burst, float radius, long expiresAt) {
        this(burst, radius, expiresAt, false, Optional.empty());
    }

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
