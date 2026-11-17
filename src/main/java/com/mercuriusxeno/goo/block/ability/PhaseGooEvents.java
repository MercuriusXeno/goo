package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PhaseGooStep;
import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Puts a living thing standing in Phase goo out of phase on the server, each
 * of its ticks, a player stepping in or a mob walked or lured in alike: the
 * phase reaches a tick past this one, so it stands while the thing stands
 * there, plus the linger the laying ability's step names for the cell's
 * layers, so it fades that long after the thing steps off. A phase already
 * standing longer is kept. The phase is the one Phase Self and Phase Other
 * lay, with all it means: mobs lose the phased, only the phased trade hits
 * with them, and they draw as a ghostly image.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * phase-shares-a-plane-between-the-phased
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PhaseGooEvents {

    /** The tick a thing standing in the goo is phased through, so its phase holds until its next tick refreshes it. */
    private static final int STANDING_TICKS = 1;

    private PhaseGooEvents() {
    }

    /**
     * Phases a living thing standing in Phase goo.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && living.level() instanceof ServerLevel level) {
            phaseCellUnder(living).ifPresent(cell -> phaseIn(level, cell, living));
        }
    }

    /**
     * The Phase cell a living thing stands in: one filling the block its
     * feet are in, or else one holding it up.
     *
     * @param living the living thing
     * @return the cell, empty where no Phase goo holds it
     */
    private static Optional<BlockPos> phaseCellUnder(LivingEntity living) {
        return Stream.of(living.blockPosition(), living.getOnPos())
                .filter(pos -> VolumeCellBlock.hasSurface(living.level().getBlockState(pos).getBlock(),
                        CellSurface.PHASE))
                .findFirst();
    }

    private static void phaseIn(ServerLevel level, BlockPos cell, LivingEntity living) {
        BlockState state = level.getBlockState(cell);
        VolumeCellBlock phaseCell = (VolumeCellBlock) state.getBlock();
        PhaseGooStep.in(phaseCell.layingProgram(level)).ifPresent(phase -> {
            int lasting = STANDING_TICKS + phase.lingerTicks(VolumeCellBlock.layersOf(state));
            OutOfPhase standing = living.getData(GooAttachments.OUT_OF_PHASE);
            living.setData(GooAttachments.OUT_OF_PHASE, standing.lastingFor(lasting, level.getGameTime()));
        });
    }
}
