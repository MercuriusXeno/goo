package com.mercuriusxeno.goo.block.ability;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * A wisp of light Radiant leaves in dark air: a light source that counts
 * as air for placement, with no shape and no collision, that fades through
 * its last stages on scheduled ticks and is gone after its life. Its block
 * entity renderer draws the soft floating mote.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 */
public class WispBlock extends BaseEntityBlock {

    public static final MapCodec<WispBlock> CODEC = simpleCodec(WispBlock::new);

    /** How far the wisp has faded: 0 bright, LAST_FADE about to go out. */
    public static final int LAST_FADE = 3;
    public static final IntegerProperty FADE = IntegerProperty.create("fade", 0, LAST_FADE);
    /** The light a fresh wisp gives. */
    public static final int LIGHT = 12;
    /** The light each fade stage takes away. */
    private static final int LIGHT_PER_FADE = 3;
    /** Ticks each fade stage lasts at the end of a wisp's life. */
    public static final int FADE_STAGE_TICKS = 20;

    /**
     * Creates the wisp block.
     *
     * @param properties the block properties
     */
    public WispBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FADE, 0));
    }

    /**
     * The light a wisp gives at a fade stage.
     *
     * @param state the block state
     * @return the light emission level
     */
    public static int lightLevel(BlockState state) {
        return LIGHT - state.getValue(FADE) * LIGHT_PER_FADE;
    }

    /**
     * The ticks before a wisp of a life starts to fade: its life less the fade stages that end it.
     *
     * @param lifeTicks the wisp's whole life
     * @return the ticks it shines unfaded, at least one
     */
    public static int ticksBeforeFading(int lifeTicks) {
        return Math.max(1, lifeTicks - (LAST_FADE + 1) * FADE_STAGE_TICKS);
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FADE);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * Fades the wisp one stage, or puts it out after its last.
     *
     * @param state  the block state
     * @param level  the server level
     * @param pos    the wisp's position
     * @param random the random source
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        int fade = state.getValue(FADE);
        if (fade >= LAST_FADE) {
            level.removeBlock(pos, false);
            return;
        }
        level.setBlock(pos, state.setValue(FADE, fade + 1), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, FADE_STAGE_TICKS);
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new WispBlockEntity(pos, state);
    }
}
