package com.mercuriusxeno.goo.client.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * The level a held piece's riding Mortar meshes against: the Mortar in its
 * cell, the piece's block it coats beside it, and open air around them, lit
 * by the world where the Mortar is drawn, so the Mortar's model shapes it as
 * Mortar lying on or clinging to that block, wherever the piece is held.
 * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
 *
 * @param level       the client level, which lights and tints the Mortar
 * @param cell        the world cell the Mortar is drawn in
 * @param mortar      the Mortar's state
 * @param coated      the cell of the piece's block the Mortar coats
 * @param coatedState that block
 */
record RidingMortarView(ClientLevel level, BlockPos cell, BlockState mortar, BlockPos coated, BlockState coatedState)
        implements BlockAndTintGetter {

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.equals(cell)) {
            return mortar;
        }
        return pos.equals(coated) ? coatedState : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return level.getBrightness(layer, cell);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return level.getRawBrightness(cell, darkening);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return level.cardinalLighting();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return level.getBlockTint(cell, color);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }
}
