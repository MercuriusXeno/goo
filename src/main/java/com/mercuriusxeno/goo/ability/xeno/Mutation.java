package com.mercuriusxeno.goo.ability.xeno;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * One mutation a Xeno blob can work on a mob: its body grows or shrinks,
 * it quickens or slows, it gains health, or what it drops turns to another
 * item. The ability JSON names the table a strike draws from.
 * xeno-blob-mutates-the-struck
 *
 * @param kind   what the mutation changes
 * @param amount how far it changes it: a share of size or speed, or hit points of health; unused by a drop
 * @param item   the item a drop mutation turns the mob's drops to; empty for the other kinds
 */
public record Mutation(Kind kind, double amount, Optional<Identifier> item) {

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_ITEM = "item";
    private static final double NO_AMOUNT = 0;

    /** What a mutation changes. */
    public enum Kind {
        /** The mob's scale, by a share of its size. */
        SIZE,
        /** The mob's movement speed, by a share of its speed. */
        SPEED,
        /** The mob's maximum health, by hit points. */
        HEALTH,
        /** The item the mob's drops turn to. */
        DROP;

        /** Codec for the kind as an ability JSON writes it, in lower case. */
        public static final Codec<Kind> CODEC = LowerCaseEnumCodec.of(Kind.class, "mutation kind");

        /** Codec for the kind on the wire. */
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC =
                ByteBufCodecs.idMapper(ordinal -> values()[ordinal], Kind::ordinal);
    }

    /** Codec for a mutation as the ability JSON's table and the mob's save both write it. */
    public static final Codec<Mutation> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Kind.CODEC.fieldOf(FIELD_KIND).forGetter(Mutation::kind),
            Codec.DOUBLE.optionalFieldOf(FIELD_AMOUNT, NO_AMOUNT).forGetter(Mutation::amount),
            Identifier.CODEC.optionalFieldOf(FIELD_ITEM).forGetter(Mutation::item)
    ).apply(inst, Mutation::new));

    /** Codec for a mutation on the wire. */
    public static final StreamCodec<ByteBuf, Mutation> STREAM_CODEC = StreamCodec.composite(
            Kind.STREAM_CODEC, Mutation::kind,
            ByteBufCodecs.DOUBLE, Mutation::amount,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), Mutation::item,
            Mutation::new);
}
