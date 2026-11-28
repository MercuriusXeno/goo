package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.ability.program.ExplosionMode;
import com.mercuriusxeno.goo.ability.program.GooExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Volatile crystal: a crystal growing in clusters from the warrens' rock, rich in crystal and unstable. A redstone
 * signal reaching it shatters it: the crystal is gone and the mod's own explosion goes off where it stood, at about
 * one TNT's power. Mined while no signal reaches it, it drops whole. A blast breaks a neighbouring crystal as it
 * breaks any block, dropping it whole, so blasts do not chain and the signal alone decides what explodes.
 * decision volatile-crystals-are-rich-in-crystal-and-unstable
 */
public class VolatileCrystalBlock extends AmethystClusterBlock {

    /** The blast's power, one TNT's, a starting guess the operator tunes in play. */
    static final float BLAST_POWER = 4.0F;

    /**
     * Creates the crystal.
     *
     * @param height     the crystal's height in pixels
     * @param width      the crystal's width in pixels
     * @param properties the block properties
     */
    public VolatileCrystalBlock(float height, float width, Properties properties) {
        super(height, width, properties);
    }

    /** A redstone signal reaching the crystal as a neighbour changes shatters it. */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        shatterWhereSignalled(level, pos);
    }

    /** A crystal placed where a signal already reaches shatters as it lands. */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        shatterWhereSignalled(level, pos);
    }

    /**
     * Shatters the crystal at a position when a redstone signal reaches it: the crystal is removed and the explosion
     * goes off at its centre.
     *
     * @param level the level the crystal stands in
     * @param pos   the crystal
     */
    void shatterWhereSignalled(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !server.getBlockState(pos).is(this)
                || !server.hasNeighborSignal(pos)) {
            return;
        }
        server.removeBlock(pos, false);
        GooExplosion.detonate(server, Vec3.atCenterOf(pos), BLAST_POWER, ExplosionMode.TNT, GooExplosion.Look.vanilla());
    }
}
