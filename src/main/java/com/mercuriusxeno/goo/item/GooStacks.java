package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
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
     * Creates the goo carrying an exact value, whole mB and the part under 1 mB, so a
     * goo of 0 whole mB and a part above 0 is a live stack.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param key   the goo type's registry key
     * @param exact the exact value
     * @return the goo, or EMPTY for a value of zero or less
     */
    public static ItemStack createForOutput(ResourceKey<GooTypeDefinition> key, GooFraction exact) {
        if (!exact.isPositive()) {
            return ItemStack.EMPTY;
        }
        return GooItem.createWithExactValue(key, exact);
    }

    /**
     * Returns the exact value of the given item stack: the goo's whole mB plus its
     * part under 1 mB, or zero for any other item.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param stack the item stack to measure
     * @return the exact value
     */
    public static GooFraction exactValueOf(ItemStack stack) {
        return mixedVolumeOf(stack).exact();
    }

    /**
     * Returns the exact value of the given item stack as it stores it, whole mB and the
     * part under 1 mB, or zero for any other item.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param stack the item stack to measure
     * @return the mixed volume
     */
    public static MixedVolume mixedVolumeOf(ItemStack stack) {
        if (stack.getItem() instanceof GooItem) {
            return new MixedVolume(GooItem.getVolume(stack), GooItem.getPart(stack));
        }
        return MixedVolume.ZERO;
    }

    /**
     * Takes whole mB from a goo, running {@code consume} once the stack holds nothing,
     * so a goo whose last whole mB is taken keeps its part under 1 mB.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @param stack   the goo stack
     * @param taken   the whole mB taken, at most its volume
     * @param consume removes the emptied stack from where it stands
     */
    static void depleteWhole(ItemStack stack, int taken, Runnable consume) {
        int remaining = Math.max(0, GooItem.getVolume(stack) - taken);
        if (remaining == 0 && GooItem.getPart(stack).isZero()) {
            consume.run();
        } else {
            GooItem.setVolume(stack, remaining);
        }
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
     * Deducts volume from a goo, consuming the stack once it holds nothing.
     *
     * @param stack    the goo stack
     * @param accepted the accepted volume
     * @param player   the player holding the stack
     */
    private static void depleteGoo(ItemStack stack, int accepted, Player player) {
        depleteWhole(stack, accepted, () -> stack.consume(1, player));
    }

    /**
     * Dumps the entire exact value of {@code source} (a goo of any goo type)
     * into the goo sitting in {@code gooStack}, the two adding through
     * MixedVolume's exact combine. Clears {@code source} by setting
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
        // decision goo-item-carries-its-exact-value-as-a-mixed-remainder
        GooItem.setMixedVolume(gooStack, mixedVolumeOf(gooStack).plus(mixedVolumeOf(source)));
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
