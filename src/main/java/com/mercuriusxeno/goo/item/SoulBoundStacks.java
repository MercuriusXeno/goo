package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * The soul-bound stacks a dead player holds until respawn, each with the
 * inventory slot it left (decision exorite-kept-through-death). Soul bound
 * means kept through death and nothing more.
 *
 * @param stacks the kept stacks and their slots
 */
public record SoulBoundStacks(List<SlotStack> stacks) {

    /** The items a player keeps through death: every exorite piece and the exo gauntlet. */
    public static final TagKey<Item> SOUL_BOUND = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Goo.MODID, "soul_bound"));

    public static final SoulBoundStacks NONE = new SoulBoundStacks(List.of());

    public static final MapCodec<SoulBoundStacks> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SlotStack.CODEC.listOf().fieldOf("stacks").forGetter(SoulBoundStacks::stacks)
    ).apply(instance, SoulBoundStacks::new));

    /**
     * One kept stack and the inventory slot it left.
     *
     * @param slot  the inventory slot index
     * @param stack the kept stack
     */
    public record SlotStack(int slot, ItemStack stack) {
        public static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SlotStack::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(SlotStack::stack)
        ).apply(instance, SlotStack::new));
    }
}
