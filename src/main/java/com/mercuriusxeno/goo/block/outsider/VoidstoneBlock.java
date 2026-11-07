package com.mercuriusxeno.goo.block.outsider;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Voidstone: the unbreakable block Outsider's platform is made of. Its block
 * entity lets the client draw its faces through a shader whose stars are
 * placed by world position, so a floor of it reads as one unbroken, random
 * field of stars rather than a tile repeated block by block.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class VoidstoneBlock extends BaseEntityBlock {

    public static final MapCodec<VoidstoneBlock> CODEC = simpleCodec(VoidstoneBlock::new);

    /**
     * @param properties the block's properties
     */
    public VoidstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidstoneBlockEntity(pos, state);
    }

    /** The model draws the dark stone under the shader's stars, and alone past the stars' draw distance. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
