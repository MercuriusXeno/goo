package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Permanent glow crystal Bulb places as its program runs: one model at one
 * size emitting the max light. No collision, breaks like a torch and drops
 * the glow goo Bulb cost. Attaches to any surface (floor, wall, ceiling).
 * decision bulb-one-model-max-light-beacon-combo
 *
 * <p>Blockstate properties: FACING (6 dirs).</p>
 */
public class GlowCrystalBlock extends Block {

    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class);
    /** The light every crystal emits: the max. */
    public static final int LIGHT_LEVEL = 15;
    /** The model's min coordinate on the lateral axes, in block fractions. */
    public static final double LATERAL_MIN = 2.0 / 16;
    /** The model's max coordinate on the lateral axes, in block fractions. */
    public static final double LATERAL_MAX = 14.0 / 16;
    /** The model's depth out of its face, in block fractions. */
    public static final double DEPTH = 2.0 / 16;
    private static final Map<Direction, VoxelShape> SHAPES = buildShapeTable();

    /**
     * Creates a glow crystal block.
     *
     * @param properties the block properties
     */
    public GlowCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /**
     * The crystal's lateral extent across its face.
     *
     * @return the width in block fractions
     */
    public static double lateralExtent() {
        return LATERAL_MAX - LATERAL_MIN;
    }

    /**
     * The crystal's voxel shape on a face.
     *
     * @param facing the surface direction
     * @return the voxel shape
     */
    public static VoxelShape shapeOn(Direction facing) {
        return SHAPES.get(facing);
    }

    private static Map<Direction, VoxelShape> buildShapeTable() {
        Map<Direction, VoxelShape> table = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            table.put(facing, shapeFor(facing, LATERAL_MIN, LATERAL_MAX, DEPTH));
        }
        return table;
    }

    /**
     * Builds a voxel shape anchored to the given face.
     *
     * @param facing the surface direction
     * @param min    lateral min (block fraction)
     * @param max    lateral max (block fraction)
     * @param depth  depth from the face (block fraction)
     * @return the voxel shape
     */
    public static VoxelShape shapeFor(Direction facing, double min, double max, double depth) {
        return switch (facing.getAxis()) {
            case Y -> shapeAlongY(facing, min, max, depth);
            case Z -> shapeAlongZ(facing, min, max, depth);
            case X -> shapeAlongX(facing, min, max, depth);
        };
    }

    /**
     * Builds a shape anchored to the up or down face.
     *
     * @param facing vertical surface direction (UP or DOWN)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the Y-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongY(Direction facing, double min, double max, double depth) {
        return facing == Direction.UP
                ? Shapes.box(min, 0, min, max, depth, max)
                : Shapes.box(min, 1 - depth, min, max, 1, max);
    }

    /**
     * Builds a shape anchored to the north or south face.
     *
     * @param facing horizontal Z-axis direction (NORTH or SOUTH)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the Z-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongZ(Direction facing, double min, double max, double depth) {
        // a crystal facing north stands on the north face of the block south of it, so it hugs z = 1
        return facing == Direction.NORTH
                ? Shapes.box(min, min, 1 - depth, max, max, 1)
                : Shapes.box(min, min, 0, max, max, depth);
    }

    /**
     * Builds a shape anchored to the west or east face.
     *
     * @param facing horizontal X-axis direction (WEST or EAST)
     * @param min    lateral min in block fractions
     * @param max    lateral max in block fractions
     * @param depth  depth from the face in block fractions
     * @return the X-axis-anchored voxel shape
     */
    private static VoxelShape shapeAlongX(Direction facing, double min, double max, double depth) {
        // a crystal facing west stands on the west face of the block east of it, so it hugs x = 1
        return facing == Direction.WEST
                ? Shapes.box(1 - depth, min, min, 1, max, max)
                : Shapes.box(0, min, min, depth, max, max);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state,
                                           @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state,
                                                    @NonNull BlockGetter level, @NonNull BlockPos pos,
                                                    @NonNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face);
    }

    /**
     * Checks that the supporting surface is solid.
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level,
                                 @NonNull BlockPos pos) {
        return FaceSupport.supports(level, pos, state.getValue(FACING));
    }

    /**
     * Breaks when the support block is removed (like torches).
     */
    @Override
    public void onNeighborChange(@NonNull BlockState state, @NonNull LevelReader level,
                                 @NonNull BlockPos pos, @NonNull BlockPos neighbor) {
        FaceSupport.breakUnsupported(canSurvive(state, level, pos), level, pos, true);
    }

    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state,
                                                LootParams.@NonNull Builder builder) {
        return List.of(GooStacks.createForOutput(GooTypes.GLOW, GooStacks.THOUSAND));
    }
}
