package com.mercuriusxeno.goo.ability.hex;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * The charm a mob holds: the player it fights for. It holds with no expiry
 * until that player hurts the mob. Saved with the mob and synced to every
 * client drawing it, which floats the charmed heart over its head and
 * draws the hex glisten on its model.
 * charm-glisten-and-icon-over-the-head
 * charm-holds-until-struck
 *
 * @param charmer the player the mob fights for
 */
public record Charmed(UUID charmer) {

    /** No charm: the mob fights for no one. */
    public static final Charmed NONE = new Charmed(new UUID(0L, 0L));

    private static final String FIELD_CHARMER = "charmer";

    /** Saves the charm with the mob; a save's older expires_at field is ignored on load. */
    public static final MapCodec<Charmed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            UUIDUtil.CODEC.fieldOf(FIELD_CHARMER).forGetter(Charmed::charmer)
    ).apply(inst, Charmed::new));

    /** Syncs the charm to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Charmed> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Charmed::charmer,
            Charmed::new);

    /**
     * Whether a hit from an attacker breaks the charm: only the charmer's does.
     * charm-holds-until-struck
     *
     * @param attacker the uuid of the entity behind the damage, or null for none
     * @return true when the charmer dealt the damage
     */
    public boolean brokenBy(@Nullable UUID attacker) {
        return charmer.equals(attacker);
    }
}
