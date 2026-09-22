package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tints a generic goo item's grey base texture by the highlight color of
 * the type its GOO_TYPE component names (decision generic-goo-items). The
 * item model JSON names it as {@code goo:goo_type}.
 */
public final class GooTypeItemTint implements ItemTintSource {

    /**
     * The one instance, since the tint reads nothing but the stack.
     */
    public static final GooTypeItemTint INSTANCE = new GooTypeItemTint();
    /**
     * Codec of the tint source, which carries no fields.
     */
    public static final MapCodec<GooTypeItemTint> MAP_CODEC = MapCodec.unit(INSTANCE);
    /**
     * Path of the tint source id under the goo namespace.
     */
    public static final String PATH = "goo_type";

    private static final int OPAQUE_ALPHA = 0xFF000000;
    private static final int UNTYPED = OPAQUE_ALPHA | 0xFFFFFF;

    private GooTypeItemTint() {
    }

    @Override
    public int calculate(@NonNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        ResourceKey<GooTypeDefinition> key = stack.get(GooDataComponents.GOO_TYPE.get());
        return key == null ? UNTYPED : OPAQUE_ALPHA | ClientGooTypes.color(key);
    }

    @Override
    public @NonNull MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
