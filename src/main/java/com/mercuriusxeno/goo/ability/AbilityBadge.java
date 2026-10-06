package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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

    /** Runs while the thrower holds it. */
    CHANNELED("channeled"),

    /**
     * Acts on the thrower as a brew, eaten before it takes effect.
     * decision self-brew-goos-eat-before-the-effect
     */
    BREW("brew"),

    /**
     * Lands wherever the crosshair points, favoring no mob or block.
     * badge-vocabulary-gains-free-prism-tap-brew
     * free-badge-is-a-crosshair
     */
    FREE("free"),

    /**
     * Refracts through a prism.
     * badge-vocabulary-gains-free-prism-tap-brew
     */
    PRISM("prism"),

    /**
     * Lands with a tap.
     * badge-vocabulary-gains-free-prism-tap-brew
     */
    TAP("tap");

    /** Datapack codec, reading the badge by its word. */
    public static final Codec<AbilityBadge> CODEC = StringRepresentable.fromEnum(AbilityBadge::values);

    /** Network codec, writing the badge as a varint ordinal. */
    public static final StreamCodec<ByteBuf, AbilityBadge> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], AbilityBadge::ordinal);

    /**
     * The badges in the rank the radial fan lists them, kept apart from the
     * declaration order so the network ordinal stays put.
     * fan-sorts-badge-then-order
     */
    private static final List<AbilityBadge> FAN_RANK = List.of(CHANNELED, MOB, WORLD, FREE, PRISM, TAP, SELF, BREW);

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

    /**
     * Whether this badge's abilities aim a point in space rather than favor a
     * mob or a block: they land and resolve at the aimed point itself.
     * aim-point-follows-the-cursor
     *
     * @return true for free, channeled, prism and tap
     */
    public boolean aimsAPoint() {
        return this == FREE || this == CHANNELED || this == PRISM || this == TAP;
    }

    /**
     * Where this badge's abilities stand in the fan, lowest first.
     *
     * @return the badge's fan rank
     */
    public int fanRank() {
        return FAN_RANK.indexOf(this);
    }

    /**
     * Orders abilities as the radial fan lists them: by badge rank, then by
     * the order field inside a badge.
     * fan-sorts-badge-then-order
     *
     * @param badgeOf reads an ability's badge
     * @param orderOf reads an ability's order field
     * @param <A>     the ability's type, server definition or synced client copy
     * @return the fan comparator
     */
    public static <A> Comparator<A> fanOrder(Function<A, AbilityBadge> badgeOf, ToIntFunction<A> orderOf) {
        return Comparator.<A>comparingInt(ability -> badgeOf.apply(ability).fanRank()).thenComparingInt(orderOf);
    }
}
