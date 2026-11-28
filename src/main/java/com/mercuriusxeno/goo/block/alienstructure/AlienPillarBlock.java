package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Alien pillar: the alien structure family's column, standing along the axis it is placed on, its material and its
 * carving held as block states so one block carries every style in every material.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class AlienPillarBlock extends RotatedPillarBlock {

    /** The style the column is carved in. */
    public static final EnumProperty<PillarStyle> STYLE = EnumProperty.create(AlienLooks.STYLE, PillarStyle.class);
    /** The state properties alien pillar holds. */
    public static final List<Property<?>> STATE_PROPERTIES = List.of(AXIS, AlienMaterialBlock.MATERIAL, STYLE);

    /**
     * Creates the column, fluted and upright.
     *
     * @param properties the block properties
     */
    public AlienPillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AlienMaterialBlock.MATERIAL, AlienMaterial.GLAZED)
                .setValue(STYLE, PillarStyle.FLUTED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(STATE_PROPERTIES.toArray(Property<?>[]::new));
    }
}
