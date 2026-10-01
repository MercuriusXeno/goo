package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The kind of target an ability declares, which the radial marks with a badge
 * sprite at a corner of the ability's icon. The vocabulary is fixed in code, and
 * an ability JSON naming any other word fails to load.
 * badge-marks-the-target-kind
 */
public enum AbilityBadge implements StringRepresentable {

    /** Lands on blocks in the world. */
    WORLD("world"),

    /** Lands on a mob. */
    MOB("mob"),

    /** Acts on the thrower. */
    SELF("self"),

    /** Lands with a punch. */
    PUNCH("punch"),

    /** Runs while the thrower holds it. */
    CHANNELED("channeled");

    /** Datapack codec, reading the badge by its word. */
    public static final Codec<AbilityBadge> CODEC = StringRepresentable.fromEnum(AbilityBadge::values);

    /** Network codec, writing the badge as a varint ordinal. */
    public static final StreamCodec<ByteBuf, AbilityBadge> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], AbilityBadge::ordinal);

    private final String serializedName;

    AbilityBadge(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * The word an ability JSON spells this badge with, and its sprite's file name.
     *
     * @return the badge's word
     */
    @Override
    public @NonNull String getSerializedName() {
        return serializedName;
    }
}
