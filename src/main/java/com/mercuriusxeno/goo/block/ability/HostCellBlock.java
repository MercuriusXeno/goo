package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.HostStep;
import com.mercuriusxeno.goo.registry.GooServerState;
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
     * and passes the charge on whole, ending it only where it is stranded:
     * no Host cell it can reach turns its block and no step leads off the goo.
     */
    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos,
                                RandomSource random, SculkSpreader spreader, boolean spreadVeins) {
        BlockPos pos = cursor.getPos();
        if (!(level instanceof Level world) || !level.getBlockState(pos).is(this)) {
            return cursor.getCharge();
        }
        int sculkLayers = HostStep.in(layingProgram(world)).map(HostStep::sculkLayers).orElse(0);
        if (sculkLayers == 0) {
            return cursor.getCharge();
        }
        turnTheBlockUnder(world, pos, spreader, sculkLayers);
        return HostSpread.chargeOut(cursor.getCharge(), stranded(world, pos, spreader, sculkLayers));
    }

    /**
     * Whether a charge standing in a cell is stranded, walking the goo only
     * where no recent walk found the cell free, and recording the cells a
     * walk finds free.
     *
     * @param level       the level
     * @param pos         where the charge stands
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     * @return true where no reachable cell turns its block and no step leads off the goo
     */
    private static boolean stranded(Level level, BlockPos pos, SculkSpreader spreader, int sculkLayers) {
        GooServerState state = GooServerState.of(level);
        long now = level.getGameTime();
        if (state != null && state.hostFreeCells().isFree(level, pos, now)) {
            return false;
        }
        HostReach.Walk walk = HostReach.walk(level, pos, cell -> layersSpentAt(level, cell, spreader, sculkLayers) > 0);
        if (!walk.stranded() && state != null) {
            state.hostFreeCells().markFree(level, walk.visited(), now);
        }
        return walk.stranded();
    }

    /**
     * The layers a cell would spend turning the block under it to sculk.
     *
     * @param level       the level
     * @param cell        the cell
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     * @return the layers spent, 0 for a cell turning nothing or for anything but a Host cell
     */
    private static int layersSpentAt(Level level, BlockPos cell, SculkSpreader spreader, int sculkLayers) {
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof HostCellBlock)) {
            return 0;
        }
        return HostSpread.layersSpent(state.getValue(LAYERS),
                level.getBlockState(cell.below()).is(spreader.replaceableBlocks()), sculkLayers);
    }

    /**
     * Turns the block under a cell to sculk where the cell holds a block of
     * sculk's layers over ground sculk replaces, the cell resting on it lower
     * by those layers and gone where that empties it.
     *
     * @param level       the level
     * @param pos         the cell
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     */
    private static void turnTheBlockUnder(Level level, BlockPos pos, SculkSpreader spreader, int sculkLayers) {
        int spent = layersSpentAt(level, pos, spreader, sculkLayers);
        if (spent == 0) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        int layers = state.getValue(LAYERS);
        BlockPos under = pos.below();
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
