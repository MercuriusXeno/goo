package com.mercuriusxeno.goo.ability.quantum;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.UUID;

/**
 * One creature's half of a shared fate: the creature it is linked to, the
 * game time the link fades at, and the health it stood at when the pair
 * last mirrored, from which this tick's change is read. Saved with the
 * creature and synced to clients, which draw the thread between the pair.
 * shared-fate-pools-health-and-status
 *
 * @param partner     the creature it shares its fate with
 * @param expiresAt   the game time the link fades at
 * @param lastHealth  its health when the pair last mirrored
 */
public record SharedFate(UUID partner, long expiresAt, float lastHealth) {

    /** No shared fate. */
    public static final SharedFate NONE = new SharedFate(new UUID(0L, 0L), 0L, 0f);

    private static final String FIELD_PARTNER = "partner";
    private static final String FIELD_EXPIRES_AT = "expires_at";
    private static final String FIELD_LAST_HEALTH = "last_health";

    /** Saves the link with the creature. */
    public static final MapCodec<SharedFate> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_PARTNER).forGetter(SharedFate::partner),
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(SharedFate::expiresAt),
            Codec.FLOAT.fieldOf(FIELD_LAST_HEALTH).forGetter(SharedFate::lastHealth)
    ).apply(inst, SharedFate::new));

    /** Syncs the link to the clients drawing the creature. */
    public static final StreamCodec<ByteBuf, SharedFate> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SharedFate::partner,
            ByteBufCodecs.VAR_LONG, SharedFate::expiresAt,
            ByteBufCodecs.FLOAT, SharedFate::lastHealth,
            SharedFate::new);

    /**
     * Whether the link stands at a game time.
     *
     * @param gameTime the game time
     * @return true before it fades
     */
    public boolean standsAt(long gameTime) {
        return gameTime < expiresAt;
    }

    /**
     * Whether a link was laid at all, the test for saving it.
     *
     * @return true for a laid link
     */
    public boolean laid() {
        return expiresAt > 0L;
    }

    /**
     * The link after the pair mirrored to a health.
     *
     * @param health the creature's health after mirroring
     * @return the link remembering it
     */
    public SharedFate mirroredAt(float health) {
        return new SharedFate(partner, expiresAt, health);
    }

    /**
     * The change each of two linked creatures takes when they mirror: each
     * takes its own change since the last mirror plus its partner's.
     *
     * @param ownChange     the creature's own health change since the last mirror
     * @param partnerChange its partner's
     * @return the change the creature ends the tick with
     */
    public static float mirroredChange(float ownChange, float partnerChange) {
        return ownChange + partnerChange;
    }
}
