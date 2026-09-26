package com.mercuriusxeno.goo.block;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * The tick methods a block runs for its block entity type, per side. Every
 * goo block's {@code getTicker} answers through {@link #tickerFor}, so the
 * side check and the type check exist once (decision
 * machine-base-owns-the-lifecycle).
 *
 * @param type   the block entity type the ticks apply to
 * @param server the server tick, or null for none
 * @param client the client tick, or null for none
 * @param <E>    the block entity class
 */
public record BlockEntityTicks<E extends BlockEntity>(Supplier<? extends BlockEntityType<E>> type,
                                                      @Nullable BlockEntityTicker<? super E> server,
                                                      @Nullable BlockEntityTicker<? super E> client) {

    /**
     * Ticks on the server alone.
     *
     * @param type   the block entity type
     * @param server the server tick
     * @param <E>    the block entity class
     * @return the ticks
     */
    public static <E extends BlockEntity> BlockEntityTicks<E> onServer(
            Supplier<? extends BlockEntityType<E>> type, BlockEntityTicker<? super E> server) {
        return new BlockEntityTicks<>(type, server, null);
    }

    /**
     * Ticks on both sides, each with its own method.
     *
     * @param type   the block entity type
     * @param server the server tick
     * @param client the client tick
     * @param <E>    the block entity class
     * @return the ticks
     */
    public static <E extends BlockEntity> BlockEntityTicks<E> bothSides(
            Supplier<? extends BlockEntityType<E>> type,
            BlockEntityTicker<? super E> server, BlockEntityTicker<? super E> client) {
        return new BlockEntityTicks<>(type, server, client);
    }

    /**
     * Answers the ticker for the level's side when the queried type is this one.
     *
     * @param level   the level asking
     * @param queried the block entity type the level asks about
     * @param <T>     the queried block entity class
     * @return the ticker, or null when the side has no tick or the type differs
     */
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> tickerFor(Level level,
                                                                           BlockEntityType<T> queried) {
        BlockEntityTicker<? super E> tick = level.isClientSide() ? client : server;
        return tick == null ? null : GooMachineBlock.bindTicker(queried, type.get(), tick);
    }
}
