package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Puts a living thing standing in Phase goo out of phase on the server, each
 * of its ticks, a player stepping in or a mob walked or lured in alike. The
 * phase reaches only a couple of ticks past now, so it stands while the
 * thing stands there and expires when it steps off. A phase already standing
 * longer is kept. The phase is the out-of-phase state, with all it means:
 * mobs lose the phased, only the phased trade hits with them, and they draw
 * as a ghostly image.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * phase-shares-a-plane-between-the-phased
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PhaseGooEvents {

    /**
     * The ticks a thing standing in the goo is phased ahead, two so its phase
     * still stands for whatever ticks before it on the next tick, until its
     * own tick refreshes it.
     */
    private static final int STANDING_TICKS = 2;

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
            if (phaseCellUnder(living).isPresent()) {
                phaseIn(level, living);
            }
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

    private static void phaseIn(ServerLevel level, LivingEntity living) {
        OutOfPhase standing = living.getData(GooAttachments.OUT_OF_PHASE);
        living.setData(GooAttachments.OUT_OF_PHASE, standing.lastingFor(STANDING_TICKS, level.getGameTime()));
    }
}
