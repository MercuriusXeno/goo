package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.GooMachineBlockEntity;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the world-placed choral gasket. When waterlogged,
 * acts as an infinite water source pushing 1000^0.75 mB/tick through
 * the gasket network. No internal storage - the water source is the
 * water block itself.
 */
public class ChoralGasketBlockEntity extends GooMachineBlockEntity {

    /**
     * NBT face label for the gasket attachment.
     */
    private static final String TAG_GASKET = "gasket";

    /**
     * Infinite water source - always reports 1000 mB, never depletes.
     */
    private final InfiniteWaterSource waterSource = new InfiniteWaterSource();

    /**
     * Pushes water to gasket partners. Constructed in the BE constructor so it can
     * see the attachment's stable callbacks; assigned final via constructor.
     */
    private final GasketPusher gasketPusher;

    /**
     * Creates a choral gasket block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChoralGasketBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHORAL_GASKET.get(), pos, state,
                be -> GasketAttachment.single(be, GasketRole.TRANSMITTER, TAG_GASKET));
        this.gasketPusher = gasket().singlePusher(waterSource);
    }

    /**
     * Server tick: push water through the network when waterlogged.
     *
     * @param level the server level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos,
                                  BlockState state, ChoralGasketBlockEntity be) {
        if (state.getValue(ChoralGasketBlock.WATERLOGGED)) {
            be.gasketPusher.tick();
        }
    }

    /**
     * A standing choral gasket holds its gasket once tuning gave it an id; breaking
     * it pops that gasket and unlinks it (decision machine-base-owns-the-lifecycle).
     *
     * @param role the gasket role
     * @return true for the transmitter once it carries an id
     */
    @Override
    public boolean holdsBlockGasket(GasketRole role) {
        return role == GasketRole.TRANSMITTER && getGasketId(role) != null;
    }

    /**
     * Returns the water source handler for capability exposure.
     *
     * @return the water source handler
     */
    public InfiniteWaterSource getWaterSource() {
        return waterSource;
    }
}
