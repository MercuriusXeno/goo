package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * The reflector: an 8 pixel cuboid centered in its cell with a lens on each side, see-through in the middle, held
 * off a solid face by a short neck. FACING points away from the face it attaches by, as an amethyst cluster's does,
 * and the lens toward that face is closed. A light rail leaves each open lens and joins the next reflector it meets
 * through clear air, open lens to open lens.
 * decision reflector-rails-carry-the-brightest-light
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class ReflectorBlock extends BaseEntityBlock {

    /** The side the reflector points out of, away from the face it attaches by. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** The cuboid's edge, and the neck's, in pixels. */
    private static final float CUBOID = 8f;
    private static final float NECK = 4f;
    private static final float CUBOID_FAR = 4f;
    private static final float CUBOID_NEAR = 12f;
    private static final float CELL = 16f;
    /** The shape facing north, attached by the south face, turned to each facing. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Shapes.or(
            Block.boxZ(CUBOID, CUBOID_FAR, CUBOID_NEAR), Block.boxZ(NECK, CUBOID_NEAR, CELL)));
    private static final BlockEntityTicks<ReflectorBlockEntity> TICKS =
            BlockEntityTicks.onServer(GooBlockEntities.REFLECTOR, ReflectorBlockEntity::serverTick);

    /**
     * Creates the block, pointing up off a floor.
     *
     * @param properties the block properties
     */
    public ReflectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /**
     * The side a reflector attaches by, whose lens is closed.
     *
     * @param state the reflector's state
     * @return the side facing the face it stands on
     */
    public static Direction attachedSide(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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

    /**
     * Remembers the player who placed the reflector, whom the rails it lays credit.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                            @Nullable LivingEntity by, @NonNull ItemStack itemStack) {
        if (by != null && level.getBlockEntity(pos) instanceof ReflectorBlockEntity reflector) {
            reflector.placedBy(by.getUUID());
        }
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
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ReflectorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NonNull Level level,
                                                                           @NonNull BlockState state,
                                                                           @NonNull BlockEntityType<T> type) {
        return TICKS.tickerFor(level, type);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
