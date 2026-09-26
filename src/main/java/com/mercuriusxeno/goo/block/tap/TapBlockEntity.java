package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.DripFall;
import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.AddressedGasket;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Tap block entity: drips goo from a canister placed in its body slot.
 * On a timer, draws 1 mB from that canister alone and sends a drip particle
 * from the spigot. Optionally has a choral gasket for remote fluid
 * reception (RECEIVER role).
 */
public class TapBlockEntity extends GooGlowingMachineBlockEntity implements ICanisterHolder {

    /**
     * The tap has exactly one canister slot.
     */
    public static final int SLOT = 0;
    /**
     * Drip interval in ticks (40 ticks = 2 seconds), the one constant fixing
     * the drip rate (decision fixed-drip-interval).
     */
    public static final int DRIP_INTERVAL = 40;
    /**
     * Face label returned for tuner display.
     */
    private static final String FACE_LABEL = "tap";
    /**
     * NBT key for the canister item.
     */
    private static final String TAG_CANISTER = "Canister";
    /**
     * Slot state holding the single canister.
     */
    private final SlottedCanisterData state;

    /**
     * Ticks left until the next drip, held while the valve is closed.
     */
    private final TapDripCountdown dripCountdown = new TapDripCountdown(DRIP_INTERVAL);

    /**
     * Creates a new tap block entity.
     *
     * @param pos    the block position
     * @param bstate the block state
     */
    public TapBlockEntity(BlockPos pos, BlockState bstate) {
        super(GooBlockEntities.TAP.get(), pos, bstate,
                be -> GasketAttachment.single(be, GasketRole.RECEIVER, FACE_LABEL));
        this.state = new SlottedCanisterData(this, 1,
                i -> Shapes.empty(),
                slots -> Shapes.empty());
    }

    /**
     * Server tick handler: while the valve is open, every {@link #DRIP_INTERVAL}
     * ticks, draws one drip from the canister slot, sends its particle from
     * the spigot and queues its landing on the first surface below. A closed
     * valve holds the countdown where it stands; a bottomless drop drips nothing.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param tap   the tap block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  TapBlockEntity tap) {
        if (!state.getValue(TapBlock.OPEN)) {
            return;
        }
        boolean due = tap.dripCountdown.tick();
        tap.setChanged();
        if (!due || !(level instanceof ServerLevel server)) {
            return;
        }
        TapDripLanding landing = TapDripLanding.below(server, pos);
        if (landing == null) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = TapDrip.draw(tap, SLOT);
        if (type == null) {
            return;
        }
        Vec3 spigot = TapSpigot.underside(pos);
        TapDrip.emit(TapDrip.sinkOf(server),
                GooColors.get(server.registryAccess(), type), spigot);
        int fallTicks = DripFall.fallTicks(spigot.y - landing.surfaceY(), -TapDrip.DRIP_LEAVE_SPEED);
        TapDripScheduler.enqueue(new TapDripScheduler.PendingDrip(server, pos, landing.pos(), Direction.UP,
                type, server.getServer().getTickCount() + fallTicks));
    }

    // --- Canister slot (single-slot convenience) ---

    /**
     * {@inheritDoc}
     */
    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    /**
     * Returns the canister in the tap's slot (may be EMPTY).
     *
     * @return the canister item stack, or EMPTY if none is inserted
     */
    public @NonNull ItemStack getCanister() {
        return state.getCanister(SLOT);
    }

    /**
     * Inserts a canister into the tap's slot through the shared slot lifecycle,
     * restarting the drip countdown. Returns false if the slot is occupied.
     *
     * @param stack the canister item stack to insert
     * @return true if the canister was inserted, false if slot was occupied
     */
    public boolean insertCanister(ItemStack stack) {
        if (!state.insert(SLOT, stack, false)) {
            return false;
        }
        dripCountdown.restart();
        return true;
    }

    /**
     * Removes and returns the canister from the tap's slot through the shared slot lifecycle.
     *
     * @return the removed canister item stack, or EMPTY if slot was empty
     */
    public @NonNull ItemStack removeCanister() {
        return state.remove(SLOT);
    }

    /**
     * Returns the fluid content of the inserted canister, or EMPTY.
     *
     * @return the fluid content of the inserted canister, or EMPTY
     */
    public CanisterFluidContent getFluidContent() {
        return getSlotFluidContent(SLOT);
    }

    /**
     * Inserts goo into the canister. Returns the amount actually accepted.
     *
     * @param type   the goo type to insert
     * @param volume volume in microblobs to insert
     * @return the amount actually accepted (mB)
     */
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int volume) {
        return insertGoo(SLOT, type, volume);
    }

    /**
     * Extracts goo from the canister. Returns the amount actually removed.
     *
     * @param type      the goo type to extract
     * @param requested the desired volume in microblobs
     * @return the amount actually extracted (mB)
     */
    public int extractGoo(ResourceKey<GooTypeDefinition> type, int requested) {
        return extractGoo(SLOT, type, requested);
    }

    // --- IGasketHolder (RECEIVER only) ---

    /**
     * Returns true if the canister has remaining capacity.
     *
     * @return true if the canister has remaining capacity for goo
     */
    public boolean canAcceptGoo() {
        return canAccept(SLOT);
    }

    // --- Tick and drip logic ---

    /**
     * {@inheritDoc} A tap only receives, so every hit resolves RECEIVER
     * (decision diagnose-then-fix-tap-gasket-role).
     */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        return GasketRole.RECEIVER;
    }

    /**
     * {@inheritDoc} Checks blockstate in addition to role.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(TapBlock.HAS_GASKET);
    }

    /**
     * The tap carries one gasket, so any hit on the tap addresses it.
     */
    @Override
    public @Nullable AddressedGasket addressedGasket(BlockHitResult hit) {
        return holdsBlockGasket(GasketRole.RECEIVER)
                ? new AddressedGasket(GasketRole.RECEIVER, GooConstants.NO_SLOT) : null;
    }

    @Override
    public @Nullable BooleanProperty gasketFlag(GasketRole role) {
        return role == GasketRole.RECEIVER ? TapBlock.HAS_GASKET : null;
    }

    // --- Framework lifecycle ---

    @Override
    protected SlottedCanisterData heldSlots() {
        return state;
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        state.save(output, TAG_CANISTER);
        dripCountdown.save(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        state.load(input, TAG_CANISTER);
        dripCountdown.load(input);
    }
}
