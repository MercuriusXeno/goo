package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * A monolith marker: a short black standing stone in the warrens, the surface monolith's slab shrunk to one block, a
 * full block tall, half a block wide and an eighth thick, near the monolith's own one to four to nine. Its wide faces
 * turn across the horizontal axis it stands along. It does nothing but stand and be mined.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class MonolithMarkerBlock extends Block {

    /** The horizontal axis the slab's width runs along. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** The look the slab stands in. */
    public static final EnumProperty<MarkerLook> LOOK = EnumProperty.create(MarkerLook.STATE, MarkerLook.class);
    /** The state properties a marker holds. */
    public static final List<Property<?>> STATE_PROPERTIES = List.of(AXIS, LOOK);

    /** The slab's width runs from this pixel to its mirror, and its thickness likewise, out of sixteen. */
    private static final double WIDTH_FROM = 4.0;
    private static final double THICKNESS_FROM = 7.0;
    private static final double FULL = 16.0;
    private static final VoxelShape ALONG_X = Block.box(WIDTH_FROM, 0.0, THICKNESS_FROM, FULL - WIDTH_FROM, FULL,
            FULL - THICKNESS_FROM);
    private static final VoxelShape ALONG_Z = Block.box(THICKNESS_FROM, 0.0, WIDTH_FROM, FULL - THICKNESS_FROM, FULL,
            FULL - WIDTH_FROM);

    /**
     * Creates the marker, its width running east to west.
     *
     * @param properties the block properties
     */
    public MonolithMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X)
                .setValue(LOOK, MarkerLook.XENO_RUNES));
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected @NonNull BlockState rotate(@NonNull BlockState state, @NonNull Rotation rotation) {
        boolean quarterTurn = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        Direction.Axis axis = state.getValue(AXIS);
        return quarterTurn ? state.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
                : state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(STATE_PROPERTIES.toArray(Property<?>[]::new));
    }
}
