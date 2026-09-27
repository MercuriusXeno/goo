package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypeNames;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Chrysm, crystallized goo at a fixed tier (decision chrysm-tiers-fixed-and-stackable):
 * stackable, its type in the GOO_TYPE component and its volume fixed by its
 * {@link ChrysmTier}, so it carries no BLOB_VOLUME.
 */
public class ChrysmItem extends Item {

    private final ChrysmTier tier;

    /**
     * @param properties item properties, left at the default stack size
     * @param tier       the tier fixing this item's volume
     */
    public ChrysmItem(Properties properties, ChrysmTier tier) {
        super(properties);
        this.tier = tier;
    }

    /**
     * @return the tier fixing this item's volume
     */
    public ChrysmTier tier() {
        return tier;
    }

    /**
     * Creates one chrysm of this tier carrying the given type.
     *
     * @param key the goo type's registry key
     * @return a new stack of one
     */
    public ItemStack createOf(ResourceKey<GooTypeDefinition> key) {
        ItemStack stack = new ItemStack(this);
        stack.set(GooDataComponents.GOO_TYPE.get(), key);
        return stack;
    }

    /**
     * Creates one chrysm of the tier carrying the given type.
     *
     * @param tier the tier
     * @param key  the goo type's registry key
     * @return a new stack of one
     */
    public static ItemStack stackOf(ChrysmTier tier, ResourceKey<GooTypeDefinition> key) {
        return GooItems.CHRYSM_TIERS.get(tier.ordinal()).get().createOf(key);
    }

    /**
     * Returns the display name as "[Type] [Tier]".
     *
     * @param stack the item stack
     * @return the display name component
     */
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        return Component.translatable(tier.translationKey(),
                GooTypeNames.name(stack.get(GooDataComponents.GOO_TYPE.get())));
    }
}
