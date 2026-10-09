package com.mercuriusxeno.goo.entity;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * The stacks a black hole pulled in and the compression sphere it leaves
 * holds: each stack added tops up the held stacks of the same item and
 * components before it opens a new one, so the hoard keeps whole stacks
 * (decision black-hole-leaves-a-compression-sphere).
 */
public final class CompressedHoard {

    /**
     * Codec writing the held stacks as a list.
     */
    public static final Codec<CompressedHoard> CODEC = ItemStack.CODEC.listOf()
            .xmap(CompressedHoard::new, CompressedHoard::stacks);

    private final List<ItemStack> stacks = new ArrayList<>();

    /**
     * An empty hoard.
     */
    public CompressedHoard() {
        // stacks start empty
    }

    private CompressedHoard(List<ItemStack> held) {
        held.forEach(this::add);
    }

    /**
     * Adds a stack, topping up held stacks of the same item and components
     * first. The hoard keeps copies, so the caller's stack is left as it was.
     *
     * @param stack the stack pulled in
     */
    public void add(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack held : stacks) {
            if (rest.isEmpty()) {
                return;
            }
            if (ItemStack.isSameItemSameComponents(held, rest)) {
                int moved = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                rest.shrink(moved);
            }
        }
        while (!rest.isEmpty()) {
            stacks.add(rest.split(rest.getMaxStackSize()));
        }
    }

    /**
     * Moves every stack another hoard holds into this one, emptying it.
     *
     * @param other the hoard to empty into this one
     */
    public void takeAll(CompressedHoard other) {
        other.stacks.forEach(this::add);
        other.stacks.clear();
    }

    /**
     * @return the held stacks in an unmodifiable list; the stacks themselves are the hoard's own
     */
    public List<ItemStack> stacks() {
        return List.copyOf(stacks);
    }

    /**
     * @return true when the hoard holds nothing
     */
    public boolean isEmpty() {
        return stacks.isEmpty();
    }
}
