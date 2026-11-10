package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * How each face of a block looks as it begins to peel: the texture its model
 * lays on that face, or its particle texture where the model lays none, and
 * the biome tint the world gives that face, so a grass block's chunks wear a
 * green top and dirt sides.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
final class PeelFaces {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF000000;

    private PeelFaces() {
    }

    /**
     * @param level the client level
     * @param pos   where the block stands
     * @param state the block
     * @return how each of its faces looks, by {@link Direction#get3DDataValue()}
     */
    static BlockPeels.FaceLook[] of(ClientLevel level, BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        List<BlockStateModelPart> parts = new ArrayList<>();
        mc.getModelManager().getBlockStateModelSet().get(state).collectParts(level, pos, state,
                RandomSource.create(state.getSeed(pos)), parts);
        TextureAtlasSprite particle = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state, level,
                pos).sprite();
        BlockPeels.FaceLook[] faces = new BlockPeels.FaceLook[Direction.values().length];
        for (Direction face : Direction.values()) {
            BakedQuad quad = quadFacing(parts, face);
            TextureAtlasSprite sprite = quad == null || !quad.materialInfo().sprite().atlasLocation()
                    .equals(particle.atlasLocation()) ? particle : quad.materialInfo().sprite();
            int tint = quad == null ? OPAQUE_WHITE : tintOf(level, pos, state, quad.materialInfo().tintIndex());
            faces[face.get3DDataValue()] = BlockPeels.FaceLook.of(new SpriteCrop(sprite.getU0(), sprite.getU1(),
                    sprite.getV0(), sprite.getV1()), tint);
        }
        return faces;
    }

    /**
     * @param parts the block model's parts
     * @param face  a face of the block
     * @return the model's first quad on that face, or its first unculled quad facing that way, or null
     */
    private static @Nullable BakedQuad quadFacing(List<BlockStateModelPart> parts, Direction face) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> culled = part.getQuads(face);
            if (!culled.isEmpty()) {
                return culled.getFirst();
            }
        }
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == face) {
                    return quad;
                }
            }
        }
        return null;
    }

    /**
     * @param level     the client level
     * @param pos       where the block stands
     * @param state     the block
     * @param tintIndex the quad's tint index, below zero for none
     * @return the opaque ARGB the world tints that quad by, opaque white for none
     */
    private static int tintOf(ClientLevel level, BlockPos pos, BlockState state, int tintIndex) {
        if (tintIndex < 0) {
            return OPAQUE_WHITE;
        }
        BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
        return tint == null ? OPAQUE_WHITE : OPAQUE_ALPHA | tint.colorAsTerrainParticle(state, level, pos);
    }
}
