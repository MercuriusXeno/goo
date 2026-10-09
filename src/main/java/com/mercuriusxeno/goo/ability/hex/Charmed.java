package com.mercuriusxeno.goo.ability.hex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.UUID;

/**
 * The charm a mob holds until it fades: the player it fights for, and the
 * game time the charm fades at. Saved with the mob and synced to every
 * client drawing it, which floats the charmed heart over its head.
 * Decision charm-glisten-and-icon-over-the-head.
 *
 * @param charmer   the player the mob fights for
 * @param expiresAt the game time the charm fades at
 */
public record Charmed(UUID charmer, long expiresAt) {

    /** No charm: the mob fights for no one. */
    public static final Charmed NONE = new Charmed(new UUID(0L, 0L), 0L);

    private static final String FIELD_CHARMER = "charmer";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the charm with the mob. */
    public static final MapCodec<Charmed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CHARMER).forGetter(Charmed::charmer),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Charmed::expiresAt)
    ).apply(inst, Charmed::new));

    /** Syncs the charm to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Charmed> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Charmed::charmer,
            ByteBufCodecs.VAR_LONG, Charmed::expiresAt,
            Charmed::new);

    /**
     * Whether the charm still stands at a game time.
     *
     * @param gameTime the game time
     * @return true before the charm fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }
}
