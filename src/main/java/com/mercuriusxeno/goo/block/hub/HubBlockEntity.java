package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.canister.*;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Hub: holds up to 8 canisters in radial slots (N, NE, E, SE, S, SW, W, NW).
 * Central input on top auto-routes goo to canisters with remaining capacity.
 *
 * <p>Slot state and the slot lifecycle are owned by {@link SlottedCanisterData}.
 * Intake gasket field storage owned by {@link GasketState#single}.</p>
 */
public class HubBlockEntity extends GooGlowingMachineBlockEntity implements ICanisterHolder, ICanisterAttachable {

    public static final int MAX_CANISTERS = 8;

    /**
     * Face label for the intake gasket.
     */
    private static final String FACE_LABEL = "hub";
    /**
     * NBT tag for the slot grid (per-slot child compounds).
     */
    private static final String TAG_SLOTS = "Slots";
    /**
     * Block update flags: notify neighbours + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /**
     * Behavioral component owning slot arrays, handlers, and stream state.
     */
    private final SlottedCanisterData state;

    /**
     * Creates a hub block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.HUB.get(), pos, state,
                be -> GasketAttachment.single(be, GasketRole.RECEIVER, FACE_LABEL));
        GasketAttachment gasket = gasket();
        this.state = new SlottedCanisterData(this, MAX_CANISTERS,
                HubBlock::slotShape,
                HubBlockEntity::computeShape);
        gasket.rebuildPushers(this.state::rebuildAllPushers);
        gasket.afterLoad(() -> {
            if (level instanceof ServerLevel serverLevel) {
                GasketPusher.forceTransmitterChunk(gasket.state().getId(GasketRole.RECEIVER),
                        gasket.registryAccess(), serverLevel, worldPosition);
                GasketPusher.forceSlotTransmitterChunks(this.state.slots, gasket.registryAccess(),
                        serverLevel, worldPosition);
            }
        });
    }

    /**
     * Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HubBlockEntity be) {
        be.state.tickPushers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    /**
     * Inserts a canister into a slot through the shared slot lifecycle.
     *
     * @param slot          the slot index (0-7)
     * @param canisterStack the canister item stack; one is taken from it
     * @return true if inserted
     */
    public boolean insertCanister(int slot, ItemStack canisterStack) {
        return state.insert(slot, canisterStack, false);
    }

    /**
     * Inserts a canister into the first empty slot.
     *
     * @param canisterStack the canister item stack; one is taken from it
     * @return true if inserted
     */
    public boolean insertCanisterAnywhere(ItemStack canisterStack) {
        for (int i = 0; i < MAX_CANISTERS; i++) {
            if (state.insert(i, canisterStack, false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the canister from a slot through the shared slot lifecycle.
     *
     * @param slot the slot index (0-7)
     * @return the removed canister, or EMPTY
     */
    public ItemStack removeCanister(int slot) {
        return state.remove(slot);
    }

    /**
     * Computes the union of the frame and all occupied slot shapes.
     *
     * @param slots the slot array
     * @return the computed shape
     */
    static VoxelShape computeShape(CanisterSlot[] slots) {
        VoxelShape result = HubBlock.frameShape();
        for (CanisterSlot s : slots) {
            VoxelShape shape = s.shape();
            if (shape != null) {
                result = Shapes.or(result, shape);
            }
        }
        return result;
    }

    // --- ICanisterAttachable ---

    /**
     * {@inheritDoc}
     */
    @Override
    public int currentTopAttachments() {
        if (level == null) {
            return 0;
        }
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe) {
            return canisterBe.getCanister(CanisterBlock.CENTER_SLOT).isEmpty() ? 0 : 1;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public java.util.Set<Integer> allowedSlots() {
        return java.util.Set.of(CanisterBlock.CENTER_SLOT);
    }

    /**
     * Returns the block-level fluid handler for Transfer API.
     *
     * @return the fluid handler
     */
    public HubFluidHandler getFluidHandler() {
        return new HubFluidHandler(this);
    }

    // --- IGasketHolder ---

    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = HubBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    @Override
    public boolean hasIntake() {
        return true;
    }

    /**
     * The intake gasket is the hub's one block-level gasket, flagged in its blockstate.
     */
    @Override
    public boolean holdsBlockGasket(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(HubBlock.HAS_GASKET);
    }

    /**
     * {@inheritDoc} Clears intake-only state. Does not rebuild slot pushers since the
     * intake gasket is independent of slot topology. Also flips the HAS_GASKET blockstate.
     */
    @Override
    public void clearGasket(GasketRole role) {
        gasket().state().clear(role, this::onGasketCleared);
    }

    /**
     * Clears the HAS_GASKET blockstate flag and syncs to client.
     */
    private void onGasketCleared() {
        if (getLevel() != null) {
            BlockState bs = getLevel().getBlockState(getBlockPos());
            if (bs.getValue(HubBlock.HAS_GASKET)) {
                getLevel().setBlock(getBlockPos(),
                        bs.setValue(HubBlock.HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            }
        }
        BlockEntitySync.markDirtyAndSync(this);
    }

    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER) {
            state.rebuildPusher(slot);
        }
    }

    // --- Framework lifecycle ---

    @Override
    protected SlottedCanisterData heldSlots() {
        return state;
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        state.save(output, TAG_SLOTS);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        state.load(input, TAG_SLOTS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        List<ItemStack> nonEmpty = new java.util.ArrayList<>();
        for (CanisterSlot slot : state.slots) {
            if (!slot.isEmpty()) {
                nonEmpty.add(slot.canister());
            }
        }
        if (!nonEmpty.isEmpty()) {
            builder.set(GooDataComponents.HUB_CANISTERS.get(), nonEmpty);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        List<ItemStack> fromItem = getter.get(GooDataComponents.HUB_CANISTERS.get());
        if (fromItem != null) {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                state.slots[i].setCanister(i < fromItem.size() ? fromItem.get(i) : ItemStack.EMPTY);
            }
        }
    }
}
