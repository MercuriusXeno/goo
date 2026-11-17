package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SpikesStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

/**
 * Metal's Spikes cell: the volume cell bound to Metal's goo, which on each
 * of its ticks impales every living thing standing on its top, still or
 * moving, by the strike, cooldown and spend chance Spikes' step names, then
 * ticks again shortly. A player standing still sends the server no
 * movement, so the cell looks for what stands on it rather than waiting to
 * be stepped on. The strike names the player who placed the cell.
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * urchin-rests-bristled-and-spears
 * standing-goo-thing-credits-whoever-placed-it
 */
public class SpikesCellBlock extends VolumeCellBlock {

    /** The ticks between a cell's looks for what stands on it, well under the strike's cooldown. */
    private static final int STRIKE_SCAN_TICKS = 2;
    /** How far over the goo's surface a thing's feet may stand and still count as standing on it, in blocks. */
    private static final double STANDING_REACH = 0.25;

    /**
     * Creates a Spikes cell.
     *
     * @param properties the block properties
     * @param binding    the Spikes binding
     */
    public SpikesCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * A Spikes cell's strike hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Pours as any cell does, then, where the cell still stands, impales what
     * stands on it and keeps a tick coming, a pending pour's or the next look.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        SpikesStep.in(layingProgram(level)).ifPresent(spikes -> impaleWhatStands(level, pos, spikes));
        if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, STRIKE_SCAN_TICKS);
        }
    }

    /**
     * Impales each living thing standing on the cell's top, while the cell stands.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param spikes the spikes step the cell's laying ability runs
     */
    private void impaleWhatStands(ServerLevel level, BlockPos pos, SpikesStep spikes) {
        for (LivingEntity standing : level.getEntitiesOfClass(LivingEntity.class,
                standingOn(pos, layersOf(level.getBlockState(pos))))) {
            BlockState cell = level.getBlockState(pos);
            if (!cell.is(this)) {
                return;
            }
            SpikesImpale.impale(level, pos, cell, standing, spikes, creditedPlayerAt(level, pos));
        }
    }

    /**
     * The space a thing's feet stand in when it stands on a cell: the cell's
     * column from its goo's surface to a little over it.
     *
     * @param pos    the cell
     * @param layers the layers the cell holds
     * @return the space
     */
    static AABB standingOn(BlockPos pos, int layers) {
        double top = pos.getY() + (double) layers / MAX_LAYERS;
        return new AABB(pos.getX(), top, pos.getZ(), pos.getX() + 1.0, top + STANDING_REACH, pos.getZ() + 1.0);
    }
}
