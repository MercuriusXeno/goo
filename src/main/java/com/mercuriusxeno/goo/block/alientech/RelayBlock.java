package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.ability.pulse.RelayNetwork;
import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The relay: a block on a solid face that carries redstone through air, the reflector's twin in mechanism alone. A
 * link leaves every side but the one it attaches by and joins the next relay it meets through clear air, facing side
 * to facing side, and each relay gives, to every side, the strongest redstone signal reaching any other relay in its
 * linked network.
 * decision relay-prism-carries-the-signal-through-air
 * decision all-utilities-are-redesigned-as-alien-tech-blocks
 */
public class RelayBlock extends FaceMountedBlock {

    /** The signal the relay gives, its network's strongest. */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /**
     * Whether the relay gives or receives a signal, which lights its torch tip.
     * decision relay-tip-resembles-a-redstone-torch
     */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** A lit redstone torch's light, which a lit relay gives. */
    private static final int TIP_LIGHT = 7;
    /**
     * The coil tower's foot, and its height off the face to the top of its tip's glow, in pixels: the tip sits on
     * the cell's center.
     * decision relay-shrinks-so-its-tip-sits-centered-and-beams-join-tips
     */
    private static final float WIDTH = 8f;
    private static final float HEIGHT = 11f;
    private static final float CELL = 16f;
    private static final BlockEntityTicks<RelayBlockEntity> TICKS =
            BlockEntityTicks.onServer(GooBlockEntities.RELAY, RelayBlock::carry);

    /**
     * Creates the block, pointing up off a floor and giving no signal.
     *
     * @param properties the block properties
     */
    public RelayBlock(Properties properties) {
        super(properties, boxZ(WIDTH, CELL - HEIGHT, CELL));
        registerDefaultState(defaultBlockState().setValue(POWER, 0).setValue(LIT, false));
    }

    /**
     * The light a relay gives: a lit torch's while lit, none otherwise.
     *
     * @param state the relay's state
     * @return the light level
     */
    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? TIP_LIGHT : 0;
    }

    /**
     * Gives the strongest signal reaching any other relay the relay links to, and lights the relay while it gives a
     * signal or one enters it, each tick.
     *
     * @param level the level
     * @param pos   the relay's position
     * @param state its state
     * @param relay its block entity
     */
    private static void carry(Level level, BlockPos pos, BlockState state, RelayBlockEntity relay) {
        if (level instanceof ServerLevel server) {
            int carried = RelayNetwork.carriedTo(server, pos);
            boolean lit = carried > 0 || RelayNetwork.entering(server, pos) > 0;
            BlockState next = state.setValue(POWER, carried).setValue(LIT, lit);
            if (next != state) {
                level.setBlock(pos, next, UPDATE_ALL);
            }
        }
    }

    @Override
    protected boolean isSignalSource(@NonNull BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                            @NonNull Direction direction) {
        return state.getValue(POWER);
    }

    /**
     * Dust joins the relay from every side it can stand on, as it joins a redstone block.
     */
    @Override
    protected boolean shouldRedstoneWireConnectTo(@NonNull BlockState state, @NonNull BlockGetter level,
                                                  @NonNull BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new RelayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NonNull Level level,
                                                                           @NonNull BlockState state,
                                                                           @NonNull BlockEntityType<T> type) {
        return TICKS.tickerFor(level, type);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER, LIT);
    }
}
