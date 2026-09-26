package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The machine family's block: it holds the goo light pair and the ticker
 * registration once, so a machine names only its state-driven light and its
 * ticks (decision machine-base-owns-the-lifecycle).
 */
public abstract class GooMachineBlock extends BaseEntityBlock {

    /**
     * Creates a machine block.
     *
     * @param properties the block properties
     */
    protected GooMachineBlock(Properties properties) {
        super(properties);
    }

    /**
     * Binds a tick method to the block entity type it serves, answering null
     * when the level asks about another type.
     *
     * @param queried  the block entity type the level asks about
     * @param expected the block entity type the tick serves
     * @param tick     the tick method
     * @param <T>      the queried block entity class
     * @param <E>      the served block entity class
     * @return the ticker, or null when the types differ
     */
    static <T extends BlockEntity, E extends BlockEntity> @Nullable BlockEntityTicker<T> bindTicker(
            BlockEntityType<T> queried, BlockEntityType<E> expected, BlockEntityTicker<? super E> tick) {
        return createTickerHelper(queried, expected, tick);
    }

    /**
     * The ticks this machine's block entity runs.
     *
     * @return the ticks, or null for a machine that never ticks
     */
    protected @Nullable BlockEntityTicks<?> ticks() {
        return null;
    }

    /**
     * Light the block state gives on its own, without the block entity.
     *
     * @param state the block state
     * @return the state-driven light in [0, 15]
     */
    protected int stateLightEmission(BlockState state) {
        return 0;
    }

    /**
     * The brighter of the state's own light and the block entity's goo glow.
     *
     * @param state  the block state
     * @param getter the block-getter
     * @param pos    the block position
     * @return the light level in [0, 15]
     */
    @Override
    public final int getLightEmission(@NonNull BlockState state, @NonNull BlockGetter getter,
                                      @NonNull BlockPos pos) {
        return Math.max(stateLightEmission(state), IGooLightSource.blockEmissionFor(getter, pos));
    }

    /**
     * BE-driven emission: the value depends on the block entity, so NeoForge
     * queries with a real getter and position rather than probing once with an
     * empty getter, which would read 0 and skip the chunk.
     *
     * @param state the block state
     * @return true
     */
    @Override
    public final boolean hasDynamicLightEmission(@NonNull BlockState state) {
        return true;
    }

    /**
     * Drops the machine's gaskets before a player breaks it, while its block
     * entity still stands (decision machine-base-owns-the-lifecycle).
     *
     * @param level  the level
     * @param pos    the block position
     * @param state  the block state
     * @param player the breaking player
     * @return the state the break proceeds with
     */
    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos,
                                                @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GooMachineBlockEntity machine) {
            machine.dropGasketsOnce();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public final <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        BlockEntityTicks<?> ticks = ticks();
        return ticks == null ? null : ticks.tickerFor(level, type);
    }
}
