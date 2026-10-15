package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.MeltAbsorbPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Melt's landing: the struck stone becomes a lava source, the watchers draw
 * the stone mingling into the lava and the blob sinking into it, and the
 * lava bucket's pour sounds.
 * melt-blob-absorbs-into-lava
 */
final class MeltLanding {

    private MeltLanding() {
    }

    /**
     * Melts the block the blob struck where the tag names it.
     *
     * @param level    the server level
     * @param cell     the landing cell, in front of the struck face
     * @param face     the struck face
     * @param gooType  the goo type thrown
     * @param meltable the blocks that melt
     * @return true when the block melted
     */
    static boolean melt(ServerLevel level, BlockPos cell, Direction face, ResourceKey<GooTypeDefinition> gooType,
                        TagKey<Block> meltable) {
        BlockPos struck = cell.relative(face.getOpposite());
        if (!level.getBlockState(struck).is(meltable)) {
            return false;
        }
        BlockBreakHost.transform(level, struck, Blocks.LAVA.defaultBlockState());
        BlockVisuals.sendToWatchers(level, struck, new MeltAbsorbPayload(gooType, struck, face));
        level.playSound(null, struck, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }
}
