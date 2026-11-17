package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Casts instantly, a one-shot that fires once on release.
     * self-badge-becomes-instant-with-a-noita-trigger-star
     */
    SELF("instant"),

    /** Runs while the thrower holds it. */
    CHANNELED("channeled"),

    /**
     * Acts on the thrower for as long as it is held, from the release of its charge.
     * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
     * decision clock-badge-marks-durative-abilities
     */
    DURATIVE("durative"),

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
     * Prepares on right click and throws on left click, running while held. Declared last so the
     * network ordinal of every badge before it stays put.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     */
    HELD("held"),

    /**
     * Sprays a goo in world for as long as right click is held, leaving it standing as a residue.
     * Declared last so the network ordinal of every badge before it stays put.
     * decision residual-badge-marks-the-four-goo-sprays
     * decision rime-wears-the-residual-badge
     */
    RESIDUAL("residual");

    /** Datapack codec, reading the badge by its word. */
    public static final Codec<AbilityBadge> CODEC = StringRepresentable.fromEnum(AbilityBadge::values);

    /** Network codec, writing the badge as a varint ordinal. */
    public static final StreamCodec<ByteBuf, AbilityBadge> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], AbilityBadge::ordinal);

    /**
     * The badges in the rank the radial fan lists them, kept apart from the
     * declaration order so the network ordinal stays put.
     * fan-sorts-badge-then-order
     * held-badge-marks-abilities-that-prepare-and-throw
     * residual-tier-leads-the-fan
     */
    private static final List<AbilityBadge> FAN_RANK =
            List.of(RESIDUAL, CHANNELED, HELD, MOB, WORLD, FREE, PRISM, SELF, DURATIVE);

    private static final Set<AbilityBadge> AIMS_A_POINT = EnumSet.of(FREE, CHANNELED, HELD, PRISM, RESIDUAL);

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
     * held-badge-marks-abilities-that-prepare-and-throw
     * residual-badge-marks-the-four-goo-sprays
     *
     * @return true for free, channeled, held, prism and residual
     */
    public boolean aimsAPoint() {
        return AIMS_A_POINT.contains(this);
    }

    /**
     * The badge an ability of a delivery kind aims as: a held arc, Dart's
     * javelin formed on right click and thrown on left, aims as a mob
     * ability, favoring the aimed mob and sparing the caster's minions; every
     * other ability aims as its own badge.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param kind the ability's delivery kind
     * @return the badge its aim, its spare and its throw read
     */
    public AbilityBadge aimsAs(DeliveryKind kind) {
        return this == HELD && kind == DeliveryKind.ARC ? MOB : this;
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
