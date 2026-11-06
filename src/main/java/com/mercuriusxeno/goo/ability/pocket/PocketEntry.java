package com.mercuriusxeno.goo.ability.pocket;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One kind of item Pocket holds and how many of it: every stack of the same
 * item and components stored merges into one entry, however many it counts.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 *
 * @param kind  a single item of the kind, never empty
 * @param count how many the pocket holds
 */
public record PocketEntry(ItemStack kind, long count) {

    /** Saves an entry with the pocket's data. */
    public static final Codec<PocketEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ItemStack.CODEC.fieldOf("kind").forGetter(PocketEntry::kind),
            Codec.LONG.fieldOf("count").forGetter(PocketEntry::count)
    ).apply(inst, PocketEntry::new));

    /** Sends an entry to the client showing the pocket. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PocketEntry> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, PocketEntry::kind,
            ByteBufCodecs.VAR_LONG, PocketEntry::count,
            PocketEntry::new);

    /**
     * Whether a stack is of this entry's kind, so storing it merges here.
     *
     * @param stack the stack
     * @return true for the same item with the same components
     */
    public boolean holds(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(kind, stack);
    }

    /**
     * This entry holding a different count.
     *
     * @param newCount the count
     * @return the entry
     */
    public PocketEntry withCount(long newCount) {
        return new PocketEntry(kind, newCount);
    }
}
