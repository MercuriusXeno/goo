package com.mercuriusxeno.goo.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import org.jspecify.annotations.NonNull;

/**
 * Molten iron's liquid block. Molten iron carries the {@code minecraft:lava} fluid tag so that a thing in it moves and
 * sees as in lava, and vanilla's {@code LiquidBlock.onPlace} cools any lava-tagged liquid poured beside water to
 * obsidian or cobblestone. Placing molten iron here runs NeoForge's fluid interactions instead, as a neighbour change
 * does, so water cools it to iron core or anorthosite.
 * decision molten-iron-flows-cools-and-burns-hotter-than-lava
 */
public class MoltenIronBlock extends LiquidBlock {

    /**
     * @param fluid      molten iron's source fluid
     * @param properties the block's properties
     */
    public MoltenIronBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        if (!FluidInteractionRegistry.canInteract(level, pos)) {
            level.scheduleTick(pos, state.getFluidState().getType(), fluid.getTickDelay(level));
        }
    }
}
