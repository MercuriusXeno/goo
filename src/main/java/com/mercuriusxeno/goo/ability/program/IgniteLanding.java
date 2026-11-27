package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;

/**
 * Ignite's landing: a fire lit in the cell the blob lands in; Nitro the fire
 * heats explodes by its own rule.
 * ignite-fire-and-explosive-combo
 * ignite-detonate-step-leaves-with-the-ability-block
 */
final class IgniteLanding {

    private IgniteLanding() {
    }

    /**
     * Lights a fire in the cell where fire can stand there.
     *
     * @param level the server level
     * @param cell  the landing cell
     * @param face  the struck face
     */
    static void lightFire(ServerLevel level, BlockPos cell, Direction face) {
        if (BaseFireBlock.canBePlacedAt(level, cell, face)) {
            level.setBlock(cell, BaseFireBlock.getState(level, cell), Block.UPDATE_ALL);
        }
    }
}
