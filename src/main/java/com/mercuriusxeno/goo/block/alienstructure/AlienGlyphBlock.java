package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.NonNull;

/**
 * Alien glyph: a glyph etched into one of the alien materials, both held as block states, so one block carries every
 * glyph on every material.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class AlienGlyphBlock extends AlienMaterialBlock {

    /** The glyph etched into the block. */
    public static final EnumProperty<GlyphMark> MARK = EnumProperty.create("mark", GlyphMark.class);

    /**
     * Creates the block, the eye etched into the glaze.
     *
     * @param properties the block properties
     */
    public AlienGlyphBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MARK, GlyphMark.EYE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MARK);
    }
}
