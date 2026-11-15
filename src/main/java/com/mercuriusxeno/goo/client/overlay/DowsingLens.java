package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A Dowsing cell as a lens onto ore: an ore Glimmer or Magnet detects, a
 * block tagged goo:gem_ores or goo:metal_ores, shows where the line from the
 * eye to the ore's middle passes through the cell's goo, and the ore's middle
 * lies less than the cell's layer count in blocks along that line past where
 * the line leaves the goo. Straight through, one layer shows the block behind
 * the goo, and a whole block of fifteen layers shows fifteen blocks deep.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * dowsing-shows-ore-one-block-deep-per-layer
 */
public final class DowsingLens {

    /** The gem ores Glimmer detects. */
    static final TagKey<Block> GEM_ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Goo.MODID, "gem_ores"));
    /** The metal ores Magnet detects. */
    static final TagKey<Block> METAL_ORES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Goo.MODID, "metal_ores"));

    private DowsingLens() {
    }

    /**
     * The ore a Dowsing cell shows the eye.
     *
     * @param eye    where the eye looks from
     * @param goo    the cell's goo, in world coordinates
     * @param layers the layers the cell holds, its reach in blocks past the goo
     * @param blocks the block standing at each position
     * @return the ore blocks shown
     */
    public static Set<BlockPos> oreShown(Vec3 eye, AABB goo, int layers, Function<BlockPos, BlockState> blocks) {
        Set<BlockPos> shown = new HashSet<>();
        AABB reach = goo.inflate(layers);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(reach.minX), Mth.floor(reach.minY),
                Mth.floor(reach.minZ), Mth.ceil(reach.maxX) - 1, Mth.ceil(reach.maxY) - 1,
                Mth.ceil(reach.maxZ) - 1)) {
            if (seenThroughTheGoo(eye, goo, Vec3.atCenterOf(pos), layers) && isOre(blocks.apply(pos))) {
                shown.add(pos.immutable());
            }
        }
        return shown;
    }

    /**
     * Whether the line from the eye to a block's middle passes through the
     * goo and reaches that middle less than the reach past where it leaves
     * the goo.
     *
     * @param eye    where the eye looks from
     * @param goo    the cell's goo
     * @param middle the block's middle
     * @param reach  the blocks past the goo the lens reaches
     * @return true where the block lies behind the goo within the reach
     */
    static boolean seenThroughTheGoo(Vec3 eye, AABB goo, Vec3 middle, int reach) {
        if (goo.contains(middle) || !goo.contains(eye) && goo.clip(eye, middle).isEmpty()) {
            return false;
        }
        Optional<Vec3> leaves = goo.clip(middle, eye);
        return leaves.isPresent() && leaves.get().distanceTo(middle) < reach;
    }

    /**
     * @param state a block
     * @return true for an ore Glimmer or Magnet detects
     */
    static boolean isOre(BlockState state) {
        return state.is(GEM_ORES) || state.is(METAL_ORES);
    }
}
