package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.LayerGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The layers of an {@link AreaShape} footprint, as the block positions a
 * layer walk strikes, computed from the marker's stack count and placed
 * face alone. Two invariants of the rock design hold for every shape
 * (RockExecutor): the footprint lies perpendicular to
 * {@code blastDir = placedFace.getOpposite()}, and layer {@code i} of a
 * tunnel is centered on {@code origin.relative(blastDir, i + 1)}, so
 * layer 0 is the struck block and never the marker's air block.
 */
final class AreaLayers {

    private static final int Z_INDEX = 2;

    private AreaLayers() {
    }

    /**
     * Counts the layers the walk strikes: the tunnel depth, the flat
     * ring count, or the freeze radius as shell count.
     *
     * @param shape  the footprint shape
     * @param stacks the marker's stack count
     * @return the layer count
     */
    static int layerCount(AreaShape shape, int stacks) {
        return switch (shape) {
            case TUNNEL -> ChainFootprint.tunnelDepth(stacks);
            case FLAT_CIRCLE -> ChainFootprint.flatRings(stacks).size();
            case SPHERE -> AbilityMath.computeFreezeRadius(stacks);
        };
    }

    /**
     * Counts the blocks the whole footprint covers at a stack count, the
     * sum of its layers.
     *
     * @param shape  the footprint shape
     * @param stacks the marker's stack count; zero or fewer covers nothing
     * @return the block count
     */
    static int blockCount(AreaShape shape, int stacks) {
        if (stacks <= 0) {
            return 0;
        }
        int blocks = 0;
        for (int layer = 0; layer < layerCount(shape, stacks); layer++) {
            blocks += layerSize(shape, stacks, layer);
        }
        return blocks;
    }

    /**
     * Counts the blocks of one layer.
     *
     * @param shape  the footprint shape
     * @param stacks the marker's stack count
     * @param layer  the layer index, from zero
     * @return the layer's block count
     */
    private static int layerSize(AreaShape shape, int stacks, int layer) {
        return switch (shape) {
            case TUNNEL -> ChainFootprint.layerFootprint(stacks).size();
            case FLAT_CIRCLE -> ChainFootprint.flatRings(stacks).get(layer).size();
            case SPHERE -> ChainFootprint.sphereShell(layer).size();
        };
    }

    /**
     * Lists the block positions of one layer.
     *
     * @param shape      the footprint shape
     * @param stacks     the marker's stack count
     * @param origin     the marker position
     * @param placedFace the face the marker was placed on
     * @param layer      the layer index, from zero
     * @return the positions the layer covers, in footprint order
     */
    static List<BlockPos> layerCells(AreaShape shape, int stacks, BlockPos origin, Direction placedFace, int layer) {
        return switch (shape) {
            case TUNNEL -> perpendicular(ChainFootprint.layerFootprint(stacks), origin, placedFace, layer);
            case FLAT_CIRCLE -> perpendicular(ChainFootprint.flatRings(stacks).get(layer), origin, placedFace, 0);
            case SPHERE -> offsets(ChainFootprint.sphereShellOffsets(layer, placedFace), origin);
        };
    }

    /**
     * Places a two-dimensional footprint on the plane of one layer.
     *
     * @param footprint  the footprint's perpendicular offsets
     * @param origin     the marker position
     * @param placedFace the face the marker was placed on
     * @param layer      the depth index of the layer's center
     * @return the positions
     */
    private static List<BlockPos> perpendicular(List<int[]> footprint, BlockPos origin, Direction placedFace,
                                                int layer) {
        BlockPos center = LayerGeometry.layerCenter(origin, placedFace, layer);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        List<BlockPos> cells = new ArrayList<>(footprint.size());
        for (int[] offset : footprint) {
            cells.add(LayerGeometry.offsetPerpendicular(center, blastAxis, offset[0], offset[1]));
        }
        return cells;
    }

    /**
     * Applies three-dimensional offsets to the origin.
     *
     * @param offsets the offsets
     * @param origin  the marker position
     * @return the positions
     */
    private static List<BlockPos> offsets(List<int[]> offsets, BlockPos origin) {
        List<BlockPos> cells = new ArrayList<>(offsets.size());
        for (int[] offset : offsets) {
            cells.add(origin.offset(offset[0], offset[1], offset[Z_INDEX]));
        }
        return cells;
    }
}
