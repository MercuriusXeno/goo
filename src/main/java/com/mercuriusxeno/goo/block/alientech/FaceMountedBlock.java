package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.Map;

/**
 * An alien tech block that connects to a solid face: FACING points away from the face it attaches by, as an amethyst
 * cluster's does; it is placed against the face clicked and pops off once that face goes.
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public abstract class FaceMountedBlock extends BaseEntityBlock {

    /** The side the block points out of, away from the face it attaches by. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private final Map<Direction, VoxelShape> shapes;

    /**
     * Creates the block, pointing up off a floor.
     *
     * @param properties the block properties
     * @param northShape its shape pointing north, attached by the south face, which turns to each facing
     */
    protected FaceMountedBlock(Properties properties, VoxelShape northShape) {
        super(properties);
        this.shapes = Shapes.rotateAll(northShape);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /**
     * The side a face-mounted block attaches by.
     *
     * @param state the block's state
     * @return the side facing the face it stands on
     */
    public static Direction attachedSide(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction directionToNeighbour, @NonNull BlockPos neighbourPos,
                                              @NonNull BlockState neighbourState, @NonNull RandomSource random) {
        return directionToNeighbour == attachedSide(state) && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState,
                        random);
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected @NonNull BlockState rotate(@NonNull BlockState state, @NonNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected @NonNull BlockState mirror(@NonNull BlockState state, @NonNull Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
