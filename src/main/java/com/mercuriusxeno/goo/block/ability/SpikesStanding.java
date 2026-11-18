package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SpikesStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Optional;

/**
 * Spikes goo striking whatever stands on it: on each server tick of a living
 * thing, the Spikes cell its feet stand on, if any, impales it, so a thing
 * standing still, a player sending no movement among them, is struck as
 * surely as one walking, and no cell's own block tick decides it. A cell
 * keeps one pending block tick, which a slow pour holds, and a cell laid
 * before the strike ran on its tick has none, so striking from the cell's
 * tick missed the things on many cells.
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class SpikesStanding {

    /** How far under a thing's feet the goo's surface may lie for the thing to stand on it, in blocks. */
    private static final double UNDERFOOT = 0.05;

    private SpikesStanding() {
    }

    /**
     * Impales a living thing standing on a Spikes cell, on the server.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof LivingEntity living)
                || living.isSpectator()) {
            return;
        }
        cellUnder(level, living).ifPresent(pos -> {
            BlockState state = level.getBlockState(pos);
            SpikesCellBlock cell = (SpikesCellBlock) state.getBlock();
            SpikesStep.in(cell.layingProgram(level)).ifPresent(spikes -> SpikesImpale.impale(level, pos, state,
                    living, spikes, cell.creditedPlayerAt(level, pos)));
        });
    }

    /**
     * The Spikes cell a living thing stands on: the cell holding its feet, or
     * the cell just under them, where its goo's surface lies within a step
     * under the feet.
     *
     * @param level  the server level
     * @param living the living thing
     * @return the cell, empty where it stands on no Spikes goo
     */
    private static Optional<BlockPos> cellUnder(ServerLevel level, LivingEntity living) {
        Vec3 feet = living.position();
        BlockPos[] candidates = {BlockPos.containing(feet), BlockPos.containing(feet.subtract(0, UNDERFOOT, 0))};
        for (BlockPos pos : candidates) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SpikesCellBlock
                    && standsOn(feet.y, pos, VolumeCellBlock.layersOf(state))) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether feet at a height stand on a cell's goo: at its surface or a
     * little over it, the space a thing standing on it keeps its feet in.
     *
     * @param feetY  the feet's height
     * @param pos    the cell
     * @param layers the layers the cell holds
     * @return true where the feet stand on the goo
     */
    static boolean standsOn(double feetY, BlockPos pos, int layers) {
        double top = pos.getY() + (double) layers / VolumeCellBlock.MAX_LAYERS;
        return feetY >= top - UNDERFOOT && feetY <= top + SpikesCellBlock.STANDING_REACH;
    }
}
