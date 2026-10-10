package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * The prism Crystal's Prism ability grows where its blob lands: a milky
 * quartz crystal about half a block wide, standing out of the center of the
 * face it landed on, the stationary host every type's prism combo grows on.
 * Its block entity renderer draws the model, so the prism can scale up out
 * of the shrinking blob; the block drops nothing.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 * decision prism-hosts-the-combos
 */
public class PrismBlock extends BaseEntityBlock {

    public static final MapCodec<PrismBlock> CODEC = simpleCodec(PrismBlock::new);

    /** The face the prism grew from, its base against the block behind it. */
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);

    /**
     * The redstone power the prism gives the blocks beside it: full for the
     * tick a metronome pulses (decision metronome-prism-pulses-at-the-learned-rate),
     * and a receiving relay's carried strength while its signal holds
     * (decision relay-prism-carries-the-signal-through-air).
     */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();
    /** Half a block, in pixels. */
    private static final double HALF_PIXELS = 8;
    /** How far the oculus's eye hovers off its face, middle to face, in pixels. */
    private static final double EYE_LIFT_PIXELS = 4;
    /** Half the oculus's eye, in pixels. */
    private static final double EYE_HALF_PIXELS = 3.5;
    private static final Map<Direction, VoxelShape> EYE_SHAPES = buildEyeShapes();

    /** The ability whose combo makes a prism an oculus (decision oculus-prism-becomes-a-hovering-eye). */
    private static final String OCULUS = "goo:ender_oculus";
    /** One display tick in this many an oculus murmurs, about once every ten seconds. */
    private static final int OCULUS_MURMUR_ODDS = 200;
    private static final float OCULUS_MURMUR_VOLUME = 0.5f;
    /** The murmur's pitch, well above the enderman's own, and how far it varies up from there. */
    private static final float OCULUS_MURMUR_PITCH = 1.6f;
    private static final float OCULUS_MURMUR_PITCH_SPREAD = 0.4f;

    /** A combo's program ticks on the server; the client ticks what a combo shows around the prism. */
    private static final BlockEntityTicks<PrismBlockEntity> TICKS =
            BlockEntityTicks.bothSides(GooBlockEntities.PRISM, PrismBlockEntity::serverTick,
                    PrismBlockEntity::clientTick);

    /**
     * Creates the prism block.
     *
     * @param properties the block properties
     */
    public PrismBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 0));
    }

    /**
     * Now and then an oculus murmurs a high-pitched enderman's sound, heard by
     * the client drawing it.
     * decision oculus-prism-becomes-a-hovering-eye
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(OCULUS_MURMUR_ODDS) == 0 && level.getBlockEntity(pos) instanceof PrismBlockEntity prism
                && OCULUS.equals(prism.getCombo())) {
            level.playLocalSound(pos, SoundEvents.ENDERMAN_AMBIENT, SoundSource.BLOCKS, OCULUS_MURMUR_VOLUME,
                    OCULUS_MURMUR_PITCH + random.nextFloat() * OCULUS_MURMUR_PITCH_SPREAD, false);
        }
    }

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            // prism-is-one-pointed-quartz-column: the shape follows the column the prism draws
            shapes.put(facing, PrismColumn.shapeFor(facing));
        }
        return shapes;
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    /**
     * The block entity renderer draws the prism, scaled by the transformation
     * that grows it, so the chunk mesh draws nothing.
     *
     * @param state the block state
     * @return {@link RenderShape#INVISIBLE}
     */
    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        // oculus-prism-becomes-a-hovering-eye: an oculus is its eye, not the column it grew from
        return level.getBlockEntity(pos) instanceof PrismBlockEntity prism && OCULUS.equals(prism.getCombo())
                ? EYE_SHAPES.get(state.getValue(FACING)) : SHAPES.get(state.getValue(FACING));
    }

    /**
     * The eye's box for each facing: seven pixels a side, its middle four
     * pixels off the face the prism grew from, where the oculus draws it.
     *
     * @return the box per facing
     */
    private static Map<Direction, VoxelShape> buildEyeShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            double[] middle = {HALF_PIXELS, HALF_PIXELS, HALF_PIXELS};
            int axis = facing.getAxis().ordinal();
            int step = facing.getAxisDirection().getStep();
            middle[axis] = HALF_PIXELS - step * HALF_PIXELS + step * EYE_LIFT_PIXELS;
            double x = middle[Direction.Axis.X.ordinal()];
            double y = middle[Direction.Axis.Y.ordinal()];
            double z = middle[Direction.Axis.Z.ordinal()];
            shapes.put(facing, Block.box(x - EYE_HALF_PIXELS, y - EYE_HALF_PIXELS, z - EYE_HALF_PIXELS,
                    x + EYE_HALF_PIXELS, y + EYE_HALF_PIXELS, z + EYE_HALF_PIXELS));
        }
        return shapes;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /**
     * The prism stands while the block it grew from offers a sturdy face.
     *
     * @param state the block state
     * @param level the level
     * @param pos   the prism's position
     * @return true when the support stands
     */
    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return FaceSupport.supports(level, pos, state.getValue(FACING));
    }

    /**
     * Breaks the prism once its support is gone, as a torch breaks.
     *
     * @param state    the block state
     * @param level    the level
     * @param pos      the prism's position
     * @param neighbor the neighbor that changed
     */
    @Override
    public void onNeighborChange(@NonNull BlockState state, @NonNull LevelReader level,
                                 @NonNull BlockPos pos, @NonNull BlockPos neighbor) {
        FaceSupport.breakUnsupported(canSurvive(state, level, pos), level, pos, false);
    }

    /**
     * Records the game time of each redstone signal the prism starts
     * receiving, so a metronome prism learns its beat from the last two
     * (decision metronome-prism-pulses-at-the-learned-rate).
     *
     * @param state          the block state
     * @param level          the level
     * @param pos            the prism's position
     * @param neighborBlock  the neighbor's block
     * @param orientation    the update's orientation, when known
     * @param movedByPiston  whether a piston moved the neighbor
     */
    @Override
    protected void neighborChanged(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                   @NonNull Block neighborBlock, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PrismBlockEntity prism) {
            prism.hearSignal(level.hasNeighborSignal(pos), level.getGameTime());
        }
    }

    /**
     * A prism gives redstone power, though only a pulsing metronome or a
     * receiving relay does.
     *
     * @param state the block state
     * @return true
     */
    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    /**
     * The prism's power to every side.
     *
     * @param state     the block state
     * @param level     the level
     * @param pos       the prism's position
     * @param direction the side asked
     * @return the prism's power, 0 to 15
     */
    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                            @NonNull Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NonNull Level level, @NonNull BlockState state,
                                                                  @NonNull BlockEntityType<T> type) {
        return TICKS.tickerFor(level, type);
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new PrismBlockEntity(pos, state);
    }
}
