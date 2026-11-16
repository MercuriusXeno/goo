package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.HostStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.Collection;

/**
 * Deep's Host cell: the volume cell bound to Host's sculky slime, which a
 * sculk charge treats as ground it runs through. Vanilla's charge cursor
 * moves only onto blocks answering SculkBehaviour, so a charge reaching a
 * Host cell enters it and travels from Host cell to Host cell keeping its
 * whole charge, where on ordinary sculk it would decay; at each cell it
 * crosses, the block under the cell turns to sculk where the spreader's
 * replaceable tag allows, and the cell stands lower by the layers Host's step
 * names a block of sculk spends, gone where that empties it. A charge
 * leaving the goo onto sculk or veins behaves as vanilla's again.
 * sculk-charge-runs-along-host-goo-without-weakening
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 */
public class HostCellBlock extends VolumeCellBlock implements SculkBehaviour {

    /**
     * Creates a Host cell.
     *
     * @param properties the block properties
     * @param binding    the Host binding
     */
    public HostCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Turns the block under the cell to sculk, spending the cell's layers,
     * and passes the charge on whole.
     */
    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos,
                                RandomSource random, SculkSpreader spreader, boolean spreadVeins) {
        BlockPos pos = cursor.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(this) && level instanceof Level world) {
            HostStep.in(layingProgram(world)).ifPresent(host ->
                    turnTheBlockUnder(world, pos, state, spreader, host.sculkLayers()));
        }
        return HostSpread.chargeOut(cursor.getCharge());
    }

    /**
     * Turns the block under a cell to sculk where the cell holds a block of
     * sculk's layers over ground sculk replaces, the cell resting on it lower
     * by those layers and gone where that empties it.
     *
     * @param level       the level
     * @param pos         the cell
     * @param state       the cell's state
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     */
    private static void turnTheBlockUnder(Level level, BlockPos pos, BlockState state, SculkSpreader spreader,
                                          int sculkLayers) {
        BlockPos under = pos.below();
        int layers = state.getValue(LAYERS);
        int spent = HostSpread.layersSpent(layers, level.getBlockState(under).is(spreader.replaceableBlocks()),
                sculkLayers);
        if (spent == 0) {
            return;
        }
        level.setBlock(under, Blocks.SCULK.defaultBlockState(), UPDATE_ALL);
        level.setBlock(pos, layers > spent ? state.setValue(LAYERS, layers - spent)
                : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        level.playSound(null, under, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * Grows no veins from the goo: the charge runs through the slime itself.
     */
    @Override
    public boolean attemptSpreadVein(LevelAccessor level, BlockPos pos, BlockState state,
                                     @Nullable Collection<Direction> facings, boolean postProcess) {
        return false;
    }
}
