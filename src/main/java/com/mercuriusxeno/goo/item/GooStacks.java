package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * Pure utility for goo stack math. Centralizes volume calculations
 * and the machine output rule so all producers and consumers share one code path.
 */
public final class GooStacks {

    /**
     * A round thousand of goo, the unit throws, tests and creative stacks count in.
     */
    public static final int THOUSAND = 1000;

    private GooStacks() {
    }

    /**
     * Returns the volume of the given item stack: the goo's
     * GOO_VOLUME, or 0 for any other item.
     *
     * @param stack the item stack to measure
     * @return volume
     */
    public static int volumeOf(ItemStack stack) {
        if (stack.getItem() instanceof GooItem) {
            return GooItem.getVolume(stack);
        }
        return 0;
    }

    /**
     * Returns the goo type key of the given item stack, or null if not a goo.
     *
     * @param stack the item stack to inspect
     * @return the goo type key, or null
     */
    public static @Nullable ResourceKey<GooTypeDefinition> keyOf(ItemStack stack) {
        return stack.getItem() instanceof GooItem ? stack.get(GooDataComponents.GOO_TYPE.get()) : null;
    }

    /**
     * Whether two stacks are gooStacks of the same goo type.
     *
     * @param a one stack
     * @param b another stack
     * @return true when both carry one type
     */
    public static boolean sameType(ItemStack a, ItemStack b) {
        ResourceKey<GooTypeDefinition> key = keyOf(a);
        return key != null && Objects.equals(key, keyOf(b));
    }

    /**
     * Creates the goo carrying the given volume, the one goo item at every
     * volume (decision one-goo-item-at-every-amount).
     *
     * @param key      the goo type's registry key
     * @param volumeMb volume
     * @return the goo, or EMPTY for a volume of zero or less
     */
    public static ItemStack createForOutput(ResourceKey<GooTypeDefinition> key, int volumeMb) {
        if (volumeMb <= 0) {
            return ItemStack.EMPTY;
        }
        return GooItem.createWithVolume(key, volumeMb);
    }

    /**
     * Depletes a goo by the accepted volume, consuming the stack if empty.
     *
     * @param stack    the goo item stack to deplete
     * @param accepted the volume that was accepted
     * @param player   the player holding the stack (for consume callback)
     */
    public static void deplete(ItemStack stack, int accepted, Player player) {
        if (stack.getItem() instanceof GooItem) {
            depleteGoo(stack, accepted, player);
        }
    }

    /**
     * Deducts volume from a goo, consuming the stack if empty.
     *
     * @param stack    the goo stack
     * @param accepted the accepted volume
     * @param player   the player holding the stack
     */
    private static void depleteGoo(ItemStack stack, int accepted, Player player) {
        int remaining = GooItem.getVolume(stack) - accepted;
        if (remaining <= 0) {
            stack.consume(1, player);
        } else {
            GooItem.setVolume(stack, remaining);
        }
    }

    /**
     * Pure math: the new goo sink volume after absorbing a source volume.
     * Extracted so the arithmetic is covered by tests without bootstrapping Minecraft.
     *
     * @param sourceVolumeMb   volume being absorbed (>=0)
     * @param gooVolumeMb current goo sink volume (>=0)
     * @return the combined volume
     */
    public static int absorbedVolume(int sourceVolumeMb, int gooVolumeMb) {
        return gooVolumeMb + sourceVolumeMb;
    }

    /**
     * Dumps the entire volume of {@code source} (a goo of any goo type)
     * into the goo sitting in {@code gooStack}. Clears {@code source} by setting
     * its count to 0. Caller is responsible for {@code slot.setChanged()} if the sink
     * lives in a container slot.
     *
     * <p>No type check is performed here - the caller must verify that the two stacks
     * share a goo type before calling.</p>
     *
     * @param source        the source goo stack; count becomes 0 on return
     * @param gooStack the sink goo stack; volume is grown in place
     */
    public static void absorbIntoGooSlot(ItemStack source, ItemStack gooStack) {
        int total = absorbedVolume(volumeOf(source), GooItem.getVolume(gooStack));
        GooItem.setVolume(gooStack, total);
        source.setCount(0);
    }

    /**
     * Drops all goo in a {@link GooContents} as gooStacks at the given position, one per goo type.
     *
     * @param contents the goo contents to drop
     * @param level    the world
     * @param pos      the position to drop items at
     */
    public static void dropAll(GooContents contents, Level level, BlockPos pos) {
        if (contents.isEmpty()) {
            return;
        }
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : contents.getAll().entrySet()) {
            Block.popResource(level, pos, createForOutput(entry.getKey(), entry.getValue()));
        }
    }
}
