package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.frost.RimeChill;
import com.mercuriusxeno.goo.ability.frost.RimeFeet;
import com.mercuriusxeno.goo.ability.program.RimeStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Frost's Rime cell: the volume cell bound to Rime's endothermic goo, which
 * on each scheduled tick freezes the water and lava sources within the reach
 * Rime's step names, for good, then ticks again after the step's chill
 * ticks, so water or lava reaching it later freezes too; a living thing
 * standing on it barefoot freezes and takes freeze damage.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * rime-freezing-is-permanent-with-no-restore-on-removal
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 */
public class RimeCellBlock extends VolumeCellBlock {

    /**
     * Creates a Rime cell.
     *
     * @param properties the block properties
     * @param binding    the Rime binding
     */
    public RimeCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, freezes what
     * is in its reach and keeps a tick coming, a pending pour's or the next
     * chill's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        RimeStep.in(layingProgram(level)).ifPresent(rime -> {
            RimeChill.freezeAround(level, pos, rime.reach());
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, rime.chillTicks());
            }
        });
    }

    /**
     * Freezes a living thing standing on the cell with nothing on its feet,
     * as powder snow freezes it, by the frozen ticks Rime's step names each
     * tick, and hurts it with freeze damage on the step's cadence; anything
     * worn on its feet spares it.
     * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living
                && RimeFeet.of(living.getItemBySlot(EquipmentSlot.FEET)) == RimeFeet.FROZEN) {
            RimeStep.in(layingProgram(level)).ifPresent(rime -> chill(server, living, rime));
        }
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Raises a barefoot living thing's frozen ticks up to a whole freeze, and
     * hurts it on the step's damage cadence.
     *
     * @param level  the server level
     * @param living the living thing standing on Rime
     * @param rime   Rime's step, naming the freeze and the damage
     */
    private static void chill(ServerLevel level, LivingEntity living, RimeStep rime) {
        living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze(),
                living.getTicksFrozen() + rime.freezeTicks()));
        if (living.tickCount % rime.damageTicks() == 0) {
            living.hurtServer(level, level.damageSources().freeze(), rime.damage());
        }
    }
}
