package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Jelly's Nectar cell: the volume cell bound to Nectar's goo, which on each
 * of its ticks draws the mobs about it to eat or fight over it, a layer for
 * each feeding, as NectarFeeds runs it, then ticks again a quarter second
 * on. It is the sticky goo: a cell attached to a wall is climbed as a
 * ladder is, and a thing touching the goo, on a wall or on a top, jumps
 * lower and moves slower, as NectarStick runs it.
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-feeding-eats-a-layer-of-nectar
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 */
public class NectarCellBlock extends VolumeCellBlock {

    /** The ticks between a cell's feedings: often enough that a mob reaching it eats at once. */
    private static final int FEED_TICKS = 5;

    /**
     * Creates a Nectar cell.
     *
     * @param properties the block properties
     * @param binding    the Nectar binding
     */
    public NectarCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, feeds the
     * mobs about it and keeps a tick coming, a pending pour's or the next
     * feeding's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        BlockState poured = level.getBlockState(pos);
        if (!poured.is(this)) {
            return;
        }
        nectarIn(level).ifPresent(nectar -> NectarFeeds.feedAround(level, pos, poured, nectar));
        if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, FEED_TICKS);
        }
    }

    /**
     * A cell attached to any of its walls is climbed as a ladder is, by a
     * thing standing in its space.
     * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
     */
    @Override
    public boolean isLadder(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos,
                           @NonNull LivingEntity entity) {
        return Direction.Plane.HORIZONTAL.stream()
                .anyMatch(side -> state.getValue(coatProperty(side)) == SideCoat.ATTACHED);
    }

    /**
     * Holds down a thing standing on the goo's top, as every cell's surface
     * then acts on it.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        stick(level, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Holds down a thing in the goo's space, wading in it or climbing a coated wall.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        stick(level, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    private void stick(Level level, Entity entity) {
        nectarIn(level).ifPresent(nectar -> NectarStick.touch(level, entity, nectar));
    }

    /**
     * @param level a level, either side
     * @return the nectar goo step Nectar's ability ships there, empty where it is not loaded
     */
    public Optional<NectarGooStep> nectarIn(Level level) {
        return NectarGooStep.in(layingProgram(level));
    }
}
