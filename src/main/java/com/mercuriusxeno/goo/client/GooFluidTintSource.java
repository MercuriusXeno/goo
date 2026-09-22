package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.fluid.GooFluidBlockEntity;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tints the one goo fluid by the type at hand: in the world, the type
 * stamped on the block entity at the position; as a stack, the type
 * component the stack carries (decision generic-goo-fluids). The color is
 * the entry's highlight, read from the synced registry at render time.
 */
public final class GooFluidTintSource implements FluidTintSource {

    private static final int OPAQUE_ALPHA = 0xFF000000;
    private static final int UNTYPED = OPAQUE_ALPHA | 0xFFFFFF;

    /**
     * A fluid state alone names no type, so it reads white.
     *
     * @param state the fluid state
     * @return opaque white
     */
    @Override
    public int color(@NonNull FluidState state) {
        return UNTYPED;
    }

    @Override
    public int colorInWorld(@NonNull FluidState fluidState, @NonNull BlockState blockState,
                            @NonNull BlockAndTintGetter level, @NonNull BlockPos pos) {
        return colorOf(GooFluidBlockEntity.typeAt(level, pos));
    }

    @Override
    public int colorAsStack(@NonNull FluidStack stack) {
        return colorOf(stack.getComponents().get(GooDataComponents.GOO_TYPE.get()));
    }

    private static int colorOf(@Nullable ResourceKey<GooTypeDefinition> key) {
        return key == null ? UNTYPED : OPAQUE_ALPHA | ClientGooTypes.color(key);
    }
}
