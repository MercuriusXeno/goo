package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * The machine family's block entity: it owns the gasket attachment and runs
 * the lifecycle every machine shares once, so a machine states only what
 * differs (decision machine-base-owns-the-lifecycle). A machine whose goo
 * glows extends {@link GooGlowingMachineBlockEntity}.
 */
public abstract class GooMachineBlockEntity extends GooSyncedBlockEntity implements IGasketHolder {

    private final GasketAttachment gasket;

    /** True once the chunk holding this machine began unloading: its removal then drops nothing. */
    private boolean unloading;

    /** True once the machine dropped its gaskets, so a later removal hook drops none twice. */
    private boolean gasketsDropped;

    /**
     * Creates a machine block entity and composes its gasket attachment.
     *
     * @param type     the block entity type
     * @param pos      the block position
     * @param state    the block state
     * @param attacher builds the attachment for this machine, e.g.
     *                 {@code be -> GasketAttachment.single(be, role, label)}
     */
    protected GooMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                    Function<BlockEntity, GasketAttachment> attacher) {
        super(type, pos, state);
        this.gasket = attacher.apply(this);
    }

    @Override
    public GasketAttachment gasket() {
        return gasket;
    }

    /**
     * The canister slots whose gaskets this machine holds beside its attachment;
     * the base registers them on a server level and releases them on removal.
     *
     * @return the slot grid, or null for a machine that holds no canister slots
     */
    protected @Nullable SlottedCanisterData heldSlots() {
        return null;
    }

    @Override
    public final void setLevel(@NonNull Level level) {
        super.setLevel(level);
        gasket().onSetLevel(level);
        SlottedCanisterData slots = heldSlots();
        if (slots != null && level instanceof ServerLevel) {
            slots.registerSlotGaskets();
        }
    }

    /**
     * Rebuilds pushers, then re-propagates goo emission: the chunk-load light
     * scan ran before {@code loadAdditional}, so loaded goo would stay dark.
     */
    @Override
    public final void onLoad() {
        super.onLoad();
        gasket().onLoad();
        BlockEntitySync.kickLightingOnLoad(this);
    }

    /**
     * Loads the packet's contents, then rechecks light at this position:
     * the client's engine sees new goo only this way (decision
     * diagnose-then-fix-vat-stale-light).
     *
     * @param net   the connection the packet came from
     * @param input the packet data
     */
    @Override
    public final void onDataPacket(@NonNull Connection net, @NonNull ValueInput input) {
        super.onDataPacket(net, input);
        BlockEntitySync.relightOnContentsArrived(this);
    }

    /**
     * Drops the machine's gaskets once, on the server, as it leaves the level:
     * the block's {@code playerWillDestroy} calls it for a player break, and
     * {@link #setRemoved} for every other removal.
     */
    public final void dropGasketsOnce() {
        if (!gasketsDropped && level != null && !level.isClientSide()) {
            gasketsDropped = true;
            dropGaskets();
        }
    }

    /**
     * NeoForge calls this on every block entity of an unloading chunk before
     * {@link #setRemoved}, so the machine can tell unloading from leaving.
     */
    @Override
    public final void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    /**
     * Releases the slot gaskets' registry locations, and drops the block-level
     * gaskets unless the chunk is only unloading.
     */
    @Override
    public final void setRemoved() {
        SlottedCanisterData slots = heldSlots();
        if (slots != null) {
            slots.releaseSlotGaskets();
        }
        if (!unloading) {
            dropGasketsOnce();
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        gasket().saveAdditional(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        gasket().loadAdditional(input);
    }
}
