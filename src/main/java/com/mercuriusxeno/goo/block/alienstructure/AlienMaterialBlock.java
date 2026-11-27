package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;

/**
 * An alien structure block whose look is the material it is cut from, held as a block state so one block carries
 * every material: alien stone and alien tile.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public class AlienMaterialBlock extends Block {

    /** The material the block is cut from. */
    public static final EnumProperty<AlienMaterial> MATERIAL = EnumProperty.create("material", AlienMaterial.class);

    /**
     * Creates the block, cut from the glaze.
     *
     * @param properties the block properties
     */
    public AlienMaterialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MATERIAL, AlienMaterial.GLAZED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(MATERIAL);
    }
}
