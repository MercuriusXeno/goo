package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.DripFall;
import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.IGooReceptacle;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.gasket.AddressedGasket;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.gasket.SlotGasketRegistration;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * Tap block entity: drips goo from a canister placed in its body slot.
 * On a timer, draws 1 mB from that canister alone and sends a drip particle
 * from the spigot. Optionally has a choral gasket for remote fluid
 * reception (RECEIVER role).
 */
public class TapBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements ICanisterHolder, IGasketHolder, IGooReceptacle {

    /**
     * The tap has exactly one canister slot.
     */
    public static final int SLOT = 0;
    /**
     * Face label returned for tuner display.
     */
    private static final String FACE_LABEL = "tap";
    /**
     * NBT key for the canister item.
     */
    private static final String TAG_CANISTER = "Canister";
    /**
     * NBT key for the stream the tap pours at 1:1.
     */
    private static final String TAG_STREAM = "Stream";
    /**
     * Slot state holding the single canister.
     */
    private final SlottedCanisterData state;

    /**
     * Composed gasket integration: RECEIVER-only, no pushers.
     */
    private final GasketAttachment gasket = GasketAttachment.single(this, GasketRole.RECEIVER, FACE_LABEL);

    /**
     * The rate the tap drips at while its valve is open.
     */
    private TapDripGrade dripGrade = TapDripGrade.SLOWEST;

    /**
     * Ticks left until the next drip, held while the valve is closed.
     */
    private final TapDripCountdown dripCountdown = new TapDripCountdown(dripGrade.intervalTicks());

    /**
     * The stream the tap pours at 1:1, or null while it drips or stands idle.
     */
    private @Nullable TapStream stream;

    /**
     * Creates a new tap block entity.
     *
     * @param pos    the block position
     * @param bstate the block state
     */
    public TapBlockEntity(BlockPos pos, BlockState bstate) {
        super(GooBlockEntities.TAP.get(), pos, bstate);
        this.state = new SlottedCanisterData(1,
                i -> Shapes.empty(),
                slots -> Shapes.empty(),
                gasket.syncCallback());
    }

    /**
     * Server tick handler: while the valve is open, once per interval of the
     * tap's {@link TapDripGrade}, draws one drip from the canister slot, sends its particle from
     * the spigot, or at 1:1 pours it as the synced stream, and queues its landing on the first
     * surface below. A closed
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
            tap.setStream(null);
            return;
        }
        boolean due = tap.dripCountdown.tick();
        tap.setChanged();
        if (!due || !(level instanceof ServerLevel server)) {
            return;
        }
        TapDripLanding landing = TapDripLanding.below(server, pos);
        if (landing == null) {
            tap.setStream(null);
            return;
        }
        ResourceKey<GooTypeDefinition> type = TapDrip.draw(tap, SLOT);
        if (type == null) {
            tap.setStream(null);
            return;
        }
        tap.release(server, pos, landing, type);
    }

    /**
     * Shows a drawn drip leaving the spigot, as a particle or at 1:1 as the
     * stream, and queues its landing after its fall.
     *
     * @param server  the server level
     * @param pos     the tap's position
     * @param landing where the drip lands
     * @param type    the goo type drawn
     */
    private void release(ServerLevel server, BlockPos pos, TapDripLanding landing,
                         ResourceKey<GooTypeDefinition> type) {
        Vec3 spigot = TapSpigot.underside(pos);
        setStream(TapDrip.release(dripGrade, new TapStream(type, landing.surfaceY()), TapDrip.sinkOf(server),
                TapDrip.dripParticle(GooParticles.TAP_DRIP.get(), GooColors.get(server.registryAccess(), type)), spigot));
        int fallTicks = DripFall.fallTicks(spigot.y - landing.surfaceY(), -TapDrip.DRIP_LEAVE_SPEED);
        TapDripScheduler.enqueue(new TapDripScheduler.PendingDrip(server, pos, landing.pos(), Direction.UP,
                type, server.getServer().getTickCount() + fallTicks));
    }

    // --- Drip grade ---

    /**
     * @return the rate the tap drips at while its valve is open
     */
    public TapDripGrade dripGrade() {
        return dripGrade;
    }

    /**
     * Sets the rate the tap drips at, retiming the countdown to it.
     *
     * @param grade the new drip rate
     */
    public void setDripGrade(TapDripGrade grade) {
        dripGrade = grade;
        dripCountdown.retime(grade.intervalTicks());
        if (!grade.pours()) {
            stream = null;
        }
        markDirtyAndSync();
    }

    /**
     * @return the stream the tap pours at 1:1, or null while it drips or stands idle
     */
    public @Nullable TapStream pourStream() {
        return stream;
    }

    /**
     * Holds the stream the tap pours, syncing only when it changes so a
     * steady pour sends no packet per tick.
     *
     * @param poured the stream now pouring, or null
     */
    private void setStream(@Nullable TapStream poured) {
        if (!Objects.equals(stream, poured)) {
            stream = poured;
            markDirtyAndSync();
        }
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
     * Inserts a canister into the tap's slot. Returns false if the slot is occupied.
     *
     * @param stack the canister item stack to insert
     * @return true if the canister was inserted, false if slot was occupied
     */
    public boolean insertCanister(ItemStack stack) {
        if (!getCanister().isEmpty()) {
            return false;
        }
        state.slots[SLOT].setCanister(stack.copyWithCount(1));
        state.slots[SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        dripCountdown.restart();
        registerSlotGaskets();
        markDirtyAndSync();
        return true;
    }

    // --- Goo pass-through (delegates to ICanisterHolder slot 0) ---

    /**
     * Removes and returns the canister from the tap's slot.
     *
     * @return the removed canister item stack, or EMPTY if slot was empty
     */
    public @NonNull ItemStack removeCanister() {
        ItemStack current = getCanister();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        deregisterSlotGaskets();
        state.slots[SLOT].clear();
        markDirtyAndSync();
        return current;
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
    @Override
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

    @Override
    public GasketAttachment gasket() {
        return gasket;
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
    public boolean holdsBlockGasket(GasketRole role) {
        return supportsRole(role);
    }

    @Override
    public void uninstallGasket(AddressedGasket gasket) {
        clearGasket(gasket.role());
        level.setBlock(worldPosition, getBlockState().setValue(TapBlock.HAS_GASKET, false), Block.UPDATE_ALL);
    }

    private void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    private void registerSlotGaskets() {
        if (!getCanister().isEmpty()) {
            SlotGasketRegistration.register(gasket.registryAccess(), level, worldPosition,
                    SLOT, getSlotMetadata(SLOT));
        }
    }

    private void deregisterSlotGaskets() {
        if (!getCanister().isEmpty()) {
            SlotGasketRegistration.deregister(gasket.registryAccess(), getSlotMetadata(SLOT));
        }
    }

    @Override
    public void setLevel(@NonNull Level newLevel) {
        super.setLevel(newLevel);
        gasket.onSetLevel(newLevel);
        if (newLevel instanceof ServerLevel) {
            registerSlotGaskets();
        }
    }

    @Override
    public void setRemoved() {
        deregisterSlotGaskets();
        super.setRemoved();
    }

    /** Re-propagates goo emission after NBT load; the chunk-load light scan
     * ran before {@code loadAdditional}, so any loaded goo content would
     * otherwise stay dark. */
    @Override
    public void onLoad() {
        super.onLoad();
        gasket.onLoad();
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
    public void onDataPacket(Connection net, ValueInput input) {
        super.onDataPacket(net, input);
        BlockEntitySync.relightOnContentsArrived(this);
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        ItemStack can = getCanister();
        if (!can.isEmpty()) {
            output.store(TAG_CANISTER, ItemStack.CODEC, can);
        }
        dripGrade.save(output);
        dripCountdown.save(output);
        output.storeNullable(TAG_STREAM, TapStream.CODEC, stream);
        gasket.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        ItemStack loaded = input.read(TAG_CANISTER, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        state.slots[SLOT].setCanister(loaded);
        if (!loaded.isEmpty()) {
            state.slots[SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        }
        dripGrade = TapDripGrade.load(input);
        dripCountdown.retime(dripGrade.intervalTicks());
        dripCountdown.load(input);
        stream = input.read(TAG_STREAM, TapStream.CODEC).orElse(null);
        gasket.loadAdditional(input);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return gasket.getUpdateTag(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return gasket.getUpdatePacket();
    }
}
