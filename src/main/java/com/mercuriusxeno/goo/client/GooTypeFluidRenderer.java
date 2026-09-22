package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.fluid.GooFluidBlockEntity;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.fluid.CustomFluidRenderer;
import org.jspecify.annotations.NonNull;
import java.util.Map;

/**
 * Renders a placed goo fluid on the sprites the type stamped at its
 * position names, through the vanilla tessellator (decision
 * type-named-textures). A position with no stamped type falls through to
 * the registered grey model.
 */
public final class GooTypeFluidRenderer implements CustomFluidRenderer {

    private final GooFluidTintSource greyTint;

    /**
     * @param greyTint the tint source the grey base renders under
     */
    public GooTypeFluidRenderer(GooFluidTintSource greyTint) {
        this.greyTint = greyTint;
    }

    @Override
    public boolean renderFluid(@NonNull FluidRenderer fluidRenderer, @NonNull FluidState fluidState,
                               @NonNull BlockAndTintGetter level, @NonNull BlockPos pos,
                               FluidRenderer.@NonNull Output output, @NonNull BlockState blockState) {
        ResourceKey<GooTypeDefinition> type = GooFluidBlockEntity.typeAt(level, pos);
        if (type == null) {
            return false;
        }
        FluidModel model = GooSubmitter.fluidModel(type, greyTint);
        // The typed model carries no custom renderer, so this tessellation does not re-enter.
        FluidStateModelSet models = new FluidStateModelSet(
            Map.of(GooFluids.SOURCE.get(), model, GooFluids.FLOWING.get(), model), model);
        new FluidRenderer(models).tesselate(level, pos, output, blockState, fluidState);
        return true;
    }
}
