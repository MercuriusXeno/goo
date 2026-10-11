package com.mercuriusxeno.goo.ability.xeno;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * The mutations a mob carries, in the order the strikes worked them, saved
 * with the mob and synced to the clients watching it, which draw the
 * writhing overlay while any stands.
 * xeno-blob-mutates-the-struck
 *
 * @param worn the mutations, oldest first
 */
public record Mutations(List<Mutation> worn) {

    /** No mutations. */
    public static final Mutations NONE = new Mutations(List.of());

    private static final String FIELD_WORN = "worn";

    /** Saves the mutations with the mob. */
    public static final MapCodec<Mutations> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Mutation.CODEC.listOf().fieldOf(FIELD_WORN).forGetter(Mutations::worn)
    ).apply(inst, Mutations::new));

    /** Syncs the mutations to the clients watching the mob. */
    public static final StreamCodec<ByteBuf, Mutations> STREAM_CODEC = StreamCodec.composite(
            Mutation.STREAM_CODEC.apply(ByteBufCodecs.list()), Mutations::worn,
            Mutations::new);

    /** @param worn the mutations, copied so the record holds its own list */
    public Mutations {
        worn = List.copyOf(worn);
    }

    /**
     * @param mutation the mutation a strike worked
     * @return these mutations with it added last
     */
    public Mutations with(Mutation mutation) {
        List<Mutation> grown = new ArrayList<>(worn);
        grown.add(mutation);
        return new Mutations(grown);
    }

    /**
     * @param kind a mutation kind
     * @return the summed amount of every mutation of that kind worn
     */
    public double total(Mutation.Kind kind) {
        return worn.stream().filter(mutation -> mutation.kind() == kind).mapToDouble(Mutation::amount).sum();
    }

    /** @return whether no mutation stands */
    public boolean isEmpty() {
        return worn.isEmpty();
    }
}
