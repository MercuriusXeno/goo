package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.alientech.LightBeaconColumn;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A light beacon's column as a reflector's light rail at full power: rail at the max light through every cell of air
 * the column runs through, to the first block that stops it, taken back where the column no longer runs.
 * decision beacon-column-into-a-reflector-counts-as-max-light
 * operator ruling 2026-11-28: the beacon's beam works as a reflector's beam does, at full power
 */
public final class BeaconRails {

    private BeaconRails() {
    }

    /**
     * Lays a beacon's column as full-power rail and takes back the rail it laid before where the column no longer
     * runs.
     *
     * @param level  the server level
     * @param beacon the beacon's position
     * @param facing the side its column leaves by
     * @param railed the cells its column held rail in before
     * @return the cells its column holds rail in now, nearest first
     */
    public static List<BlockPos> lay(ServerLevel level, BlockPos beacon, Direction facing, List<BlockPos> railed) {
        List<BlockPos> column = LightBeaconColumn.cellsRunThrough(level, beacon, facing, LightBeaconColumn.REACH);
        Set<BlockPos> kept = new HashSet<>(column);
        takeBack(level, railed.stream().filter(cell -> !kept.contains(cell)).toList());
        BlockState rail = GooBlocks.LIGHT_RAIL.get().defaultBlockState()
                .setValue(LightRailBlock.LEVEL, LightRailBlock.BRIGHTEST);
        for (BlockPos cell : column) {
            if (!level.getBlockState(cell).equals(rail)) {
                level.setBlock(cell, rail, Block.UPDATE_ALL);
            }
        }
        return column;
    }

    /**
     * Takes back the rail a column held in loaded cells.
     *
     * @param level the server level
     * @param cells the cells the column held rail in
     */
    public static void takeBack(ServerLevel level, List<BlockPos> cells) {
        for (BlockPos cell : cells) {
            if (level.isLoaded(cell) && level.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get())) {
                level.removeBlock(cell, false);
            }
        }
    }
}
