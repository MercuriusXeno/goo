package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PhaseGooStep;
import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Puts a living thing standing in Phase goo, or hovering in the zone over
 * its top, out of phase on the server, each of its ticks, a player stepping
 * in or a mob walked or lured in alike, so a jump over the goo keeps it. The
 * phase reaches only a couple of ticks past now, so it stands while the
 * thing stands there and expires when it steps off. A phase already standing
 * longer is kept. The phase is the out-of-phase state, with all it means:
 * mobs lose the phased, only the phased trade hits with them, and they draw
 * as a ghostly image. A faded phase ends on its own thing's tick, here, and
 * each shift sounds where the thing stands: the respawn anchor's deplete,
 * high as it slips out of phase and low as it steps back in.
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
    /** The shift sounds' volume. */
    private static final float SHIFT_VOLUME = 0.8F;
    /** The pitch the thing slips out of phase at, the phase abilities' own. */
    private static final float OUT_OF_PHASE_PITCH = 1.6F;
    /** The pitch the thing steps back into phase at, lower, the way back. */
    private static final float INTO_PHASE_PITCH = 0.9F;
    /** How many blocks under its feet a thing's goo is sought: its own block and the tallest zone's worth. */
    private static final int ZONE_REACH = (int) Math.ceil(PhaseGooStep.MOST_ZONE_HEIGHT) + 1;

    private PhaseGooEvents() {
    }

    /**
     * Phases a living thing standing in Phase goo, ends a phase faded off the
     * goo, and sounds either shift.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)) {
            return;
        }
        boolean inGoo = heldByTheGoo(living);
        // getData lays the default on a thing holding none, a synced attachment on every living thing, which a
        // teleport then syncs to the player; a thing out of no goo and never phased holds none and gets none
        OutOfPhase standing = living.hasData(GooAttachments.OUT_OF_PHASE)
                ? living.getData(GooAttachments.OUT_OF_PHASE) : OutOfPhase.NONE;
        PhaseShift shift = PhaseShift.of(inGoo, standing, level.getGameTime());
        if (inGoo) {
            phaseIn(level, living);
        } else if (shift == PhaseShift.INTO_PHASE) {
            living.removeData(GooAttachments.OUT_OF_PHASE);
        }
        soundShift(level, living, shift);
    }

    private static void soundShift(ServerLevel level, LivingEntity living, PhaseShift shift) {
        if (shift == PhaseShift.NONE) {
            return;
        }
        float pitch = shift == PhaseShift.OUT_OF_PHASE ? OUT_OF_PHASE_PITCH : INTO_PHASE_PITCH;
        level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                living.getSoundSource(), SHIFT_VOLUME, pitch);
    }

    /**
     * Whether Phase goo holds a living thing: the first block with a body at
     * or under its feet is a Phase cell, and its feet stand in the goo or in
     * the zone over the goo's top the laying ability's step names, so a jump
     * over the goo keeps the phase. The search ends at the first block with
     * a body, so a thing on ordinary ground reads one block.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     *
     * @param living the living thing
     * @return true where the goo or its zone holds it
     */
    private static boolean heldByTheGoo(LivingEntity living) {
        Level level = living.level();
        BlockPos.MutableBlockPos pos = living.blockPosition().mutable();
        for (int down = 0; down <= ZONE_REACH; down++, pos.move(Direction.DOWN)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof VolumeCellBlock cell && cell.surface() == CellSurface.PHASE) {
                return holds(level, pos, state, cell, living.getY());
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return false;
    }

    private static boolean holds(Level level, BlockPos pos, BlockState state, VolumeCellBlock cell, double feetY) {
        double gooTop = pos.getY() + (double) VolumeCellBlock.layersOf(state) / VolumeCellBlock.MAX_LAYERS;
        return PhaseGooStep.in(cell.layingProgram(level)).map(phase -> phase.holds(feetY - gooTop)).orElse(false);
    }

    private static void phaseIn(ServerLevel level, LivingEntity living) {
        OutOfPhase standing = living.getData(GooAttachments.OUT_OF_PHASE);
        living.setData(GooAttachments.OUT_OF_PHASE, standing.lastingFor(STANDING_TICKS, level.getGameTime()));
    }
}
