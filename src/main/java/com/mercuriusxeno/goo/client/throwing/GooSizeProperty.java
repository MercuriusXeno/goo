package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.item.GooItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Item model property for goo size tiers.
 * GooStacks use volume-based tiers: 0.0 (micro), 1.0 (goo), 2.0 (kilo), 3.0 (mega+).
 */
public class GooSizeProperty implements RangeSelectItemModelProperty {

    /**
     * Codec for deserialization (no config parameters, singleton).
     *
     * @return the result
     */
    public static final MapCodec<GooSizeProperty> MAP_CODEC =
        MapCodec.unit(new GooSizeProperty());

    /** Volume threshold for the small goo model (Goo = 1,000 mB). */
    private static final int GOO_THRESHOLD = 1_000;

    /** Volume threshold for the base goo model (Kilogoo = 1,000,000 mB). */
    private static final int KILOGOO_THRESHOLD = 1_000_000;

    /** Volume threshold for the large goo model (Megagoo = 1,000,000,000 mB). */
    private static final int MEGAGOO_THRESHOLD = 1_000_000_000;
    /** Model variant value for megagoo tier. */
    private static final float SIZE_MEGAGOO = 3.0f;
    /** Model variant value for kilogoo tier. */
    private static final float SIZE_KILOGOO = 2.0f;

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        if (stack.getItem() instanceof GooItem) {
            return gooSize(GooItem.getVolume(stack));
        }
        return 0.0f;
    }

    /** Returns the model variant size for a goo based on its volume tier.
     *
     * @param volume the goo volume
     * @return the model variant float
     */
    private float gooSize(int volume) {
        if (volume >= MEGAGOO_THRESHOLD) { return SIZE_MEGAGOO; }
        if (volume >= KILOGOO_THRESHOLD) { return SIZE_KILOGOO; }
        return volume >= GOO_THRESHOLD ? 1.0f : 0.0f;
    }

    @Override
    public @NonNull MapCodec<GooSizeProperty> type() {
        return MAP_CODEC;
    }
}
