package com.mercuriusxeno.goo.data;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The items a player knows what they are made of: every item the crucible
 * has melted from a throw of theirs (decision knowledge-capability-remembers-destroyed-items).
 * Immutable: a learning answers a new value for the holder to store.
 *
 * @param items the ids of the known items
 */
public record KnownItems(Set<Identifier> items) {

    private static final String FIELD_ITEMS = "items";

    /**
     * The empty knowledge a new player starts with.
     */
    public static final KnownItems NONE = new KnownItems(Set.of());

    /**
     * Codec the player attachment saves with.
     */
    public static final MapCodec<KnownItems> CODEC =
            Identifier.CODEC.listOf().fieldOf(FIELD_ITEMS)
                    .xmap(list -> new KnownItems(Set.copyOf(list)), known -> known.items().stream().toList());

    /**
     * Codec the full-set sync carries to the client.
     */
    public static final StreamCodec<ByteBuf, KnownItems> STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new))
                    .map(KnownItems::new, known -> new HashSet<>(known.items()));

    /**
     * Copies the set so a caller's later writes cannot reach the record.
     *
     * @param items the ids of the known items
     */
    public KnownItems {
        items = Set.copyOf(items);
    }

    /**
     * Answers whether the item is known.
     *
     * @param item the item id
     * @return true when the item was learned
     */
    public boolean contains(Identifier item) {
        return items.contains(item);
    }

    /**
     * Answers whether every item named is known.
     *
     * @param required the item ids
     * @return true when none of them is unknown
     */
    public boolean containsAll(Collection<Identifier> required) {
        return items.containsAll(required);
    }

    /**
     * Answers this knowledge with one more item learned.
     *
     * @param item the item id learned
     * @return the knowledge after the learning, this one when the item was already known
     */
    public KnownItems with(Identifier item) {
        if (contains(item)) {
            return this;
        }
        Set<Identifier> next = new HashSet<>(items);
        next.add(item);
        return new KnownItems(next);
    }
}
