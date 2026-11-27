package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;

/**
 * Alien pillar: the alien structure family's column, standing along the axis it is placed on, its carving held as a
 * block state so one block carries every style.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class AlienPillarBlock extends RotatedPillarBlock {

    /** The style the column is carved in. */
    public static final EnumProperty<PillarStyle> STYLE = EnumProperty.create("style", PillarStyle.class);

    /**
     * Creates the column, fluted and upright.
     *
     * @param properties the block properties
     */
    public AlienPillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STYLE, PillarStyle.FLUTED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STYLE);
    }
}
