package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * A small fungal colony bud spores leave on a floor. It ripens on random
 * ticks and, ripe, grows into a mushroom of its own choosing: a fungus on
 * nylium, a brown or red mushroom anywhere else. Nothing drives it but the
 * world's own ticks, so a placed bud grows naturally.
 * mycosis-spore-stream-buds-and-poisons
 */
public class FungalBudBlock extends Block {

    /** The bud's ripeness, grown one step per random tick. */
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, MAX_AGE);

    private static final double BUD_WIDTH_MIN = 5.0 / 16;
    private static final double BUD_WIDTH_MAX = 11.0 / 16;
    private static final double BUD_HEIGHT = 4.0 / 16;
    private static final VoxelShape SHAPE =
            Shapes.box(BUD_WIDTH_MIN, 0, BUD_WIDTH_MIN, BUD_WIDTH_MAX, BUD_HEIGHT, BUD_WIDTH_MAX);
    private static final List<Block> MUSHROOMS = List.of(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM);

    /**
     * Creates the bud block.
     *
     * @param properties the block properties
     */
    public FungalBudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    /**
     * The mushroom a ripe bud grows into on a floor: the floor's own fungus
     * on crimson or warped nylium, otherwise a brown or red mushroom at
     * random.
     *
     * @param floor  the block the bud stands on
     * @param random the random source choosing the mushroom
     * @return the block the bud becomes
     */
    public static Block growthOn(BlockState floor, RandomSource random) {
        if (floor.is(Blocks.CRIMSON_NYLIUM)) {
            return Blocks.CRIMSON_FUNGUS;
        }
        if (floor.is(Blocks.WARPED_NYLIUM)) {
            return Blocks.WARPED_FUNGUS;
        }
        return MUSHROOMS.get(random.nextInt(MUSHROOMS.size()));
    }

    private static boolean standsOnAFloor(LevelReader level, BlockPos pos) {
        BlockPos floor = pos.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return standsOnAFloor(level, pos);
    }

    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    /**
     * Ripens the bud one step, and grows the ripe bud into its mushroom.
     */
    @Override
    protected void randomTick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                              @NonNull RandomSource random) {
        int age = state.getValue(AGE);
        if (age < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            return;
        }
        level.setBlock(pos, growthOn(level.getBlockState(pos.below()), random).defaultBlockState(),
                Block.UPDATE_ALL);
    }
}
