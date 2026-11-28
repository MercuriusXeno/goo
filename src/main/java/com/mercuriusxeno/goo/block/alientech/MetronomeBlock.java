package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The metronome: a plate like a repeater that pulses out its front at the cadence dialed on it, with no memory of
 * signals. FACING holds its back, the side its input reads, as a repeater's does. The gap switch steps the cadence
 * through the repeater's four, a pulse every 2, 4, 6 and 8 ticks; each pulse powers the front for half the cadence
 * and rests for the other half. The button flips the invert toggle: a plain metronome runs until a signal reaches its
 * back, an inverted one runs only while one does.
 * decision metronome-prism-pulses-at-the-learned-rate
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class MetronomeBlock extends DiodeBlock {

    /** The cadence in the repeater's own steps: a pulse every two ticks per step. */
    public static final IntegerProperty CADENCE = BlockStateProperties.DELAY;
    /** Whether the button is pressed, so the metronome runs only while its back is powered. */
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    /** The ticks before the first beat of a newly placed or restarted metronome. */
    private static final int START_DELAY = 1;

    /**
     * Creates the block, placed on at the fastest cadence and uninverted.
     *
     * @param properties the block properties
     */
    public MetronomeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CADENCE, 1)
                .setValue(INVERTED, false).setValue(POWERED, false));
    }

    /**
     * The shape its model draws, the switch torch and the button standing on the plate, which a look lands on.
     * operator ruling 2026-11-28: the model is what a click hits
     */
    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return MetronomeShape.of(state.getValue(FACING), state.getValue(CADENCE), state.getValue(INVERTED));
    }

    /**
     * The plate alone, which a walker stands on, as a repeater's is.
     */
    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return super.getShape(state, level, pos, context);
    }

    /**
     * The ticks between the front's turns on and off: half the cadence, which a cadence step of two ticks makes one
     * tick per step.
     *
     * @param state the block state
     * @return the half-cadence in ticks
     */
    @Override
    protected int getDelay(@NonNull BlockState state) {
        return state.getValue(CADENCE);
    }

    /**
     * Turns the front on or off on each beat while the metronome runs, and settles it off once it stops.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        if (isRunning(level, pos, state)) {
            level.scheduleTick(pos, this, getDelay(state));
            level.setBlock(pos, state.cycle(POWERED), UPDATE_CLIENTS);
        } else if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), UPDATE_CLIENTS);
        }
    }

    @Override
    protected void checkTickOnNeighbor(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state) {
        wake(level, pos, state);
    }

    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            wake(level, pos, state);
        }
    }

    /**
     * Steps the cadence on a click on the gap switch, or flips the invert toggle on a click on the button.
     */
    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        Vec3 local = hitResult.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        MetronomePart part = MetronomePart.at(state.getValue(FACING), local.x, local.z);
        BlockState dialed = part == MetronomePart.GAP_SWITCH ? state.cycle(CADENCE) : state.cycle(INVERTED);
        level.setBlockAndUpdate(pos, dialed);
        wake(level, pos, dialed);
        return InteractionResult.SUCCESS;
    }

    /**
     * Schedules the next beat where none waits and the metronome has a beat to give or a powered front to settle.
     *
     * @param level the level holding the metronome
     * @param pos   its position
     * @param state its state
     */
    private void wake(Level level, BlockPos pos, BlockState state) {
        boolean owesATick = isRunning(level, pos, state) || state.getValue(POWERED);
        if (owesATick && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, START_DELAY);
        }
    }

    /**
     * Whether the metronome beats: with its back unpowered, or, inverted, with it powered.
     *
     * @param level the level holding the metronome
     * @param pos   its position
     * @param state its state
     * @return true while it beats
     */
    private boolean isRunning(Level level, BlockPos pos, BlockState state) {
        return shouldTurnOn(level, pos, state) == state.getValue(INVERTED);
    }

    /**
     * Dust joins the metronome at its back and its front, as it does a repeater.
     */
    @Override
    protected boolean shouldRedstoneWireConnectTo(@NonNull BlockState state, @NonNull BlockGetter level,
                                                  @NonNull BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING, CADENCE, INVERTED, POWERED);
    }
}
