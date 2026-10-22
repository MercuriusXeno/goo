package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.GooLightEntry;
import com.mercuriusxeno.goo.block.ICanisterClickTaker;
import com.mercuriusxeno.goo.block.IGooReceptacle;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.fluid.GooStream;
import com.mercuriusxeno.goo.block.fluid.VesselSourcing;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooLabelRule;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Block entity for the Vat. Multi-type bulk goo storage, base capacity
 * 2^25 mB, grown by the void it holds as ordinary goo (decisions
 * void-goo-grows-a-container and each-void-mb-adds-n-plus-one-capacity).
 *
 * <p>Internal logic delegated to:
 * {@link VatSerialization} (NBT + data components),
 * {@link VatGasketOps} (gasket face resolution, stacking, drops).
 * Gasket field storage owned by {@link GasketState#dual}.</p>
 */
public class VatBlockEntity extends GooGlowingMachineBlockEntity implements IGooReceptacle, ICanisterClickTaker {

    /**
     * The cap gasket's overlay region: the block's upper half.
     */
    private static final AABB CAP_GASKET_BOUNDS = new AABB(0, 0.5, 0, 1, 1, 1);
    /**
     * The base gasket's overlay region: the block's lower half.
     */
    private static final AABB BASE_GASKET_BOUNDS = new AABB(0, 0, 0, 1, 0.5, 1);

    // Package-private fields accessed by VatSerialization, VatStackRedistributor.
    final GooFluidHandler fluidHandler = GooFluidHandler.vatReservoir(
            ContainerCapacity.VAT_BASE, this::onFluidChanged,
            () -> level != null ? level.getGameTime() : 0);
    @Nullable String label;
    /** The goo type the vat's goo label holds it to, or null (decision labeled-vessel-accepts-only-its-label-type). */
    @Nullable ResourceKey<GooTypeDefinition> gooLabel;

    /**
     * Re-entrance guard for {@link VatStackRedistributor}.
     */
    boolean redistributing;

    // --- Stream state (synced to client for BER rendering) ---
    // Package-private: accessed by VatSerialization for snapshot and save/load.
    @Nullable ResourceKey<GooTypeDefinition> vatStreamType;
    int vatStreamRate;
    long vatStreamTick;
    /** True when the stream's last landing was water (decision diagnose-then-fix-waterlogged-gasket-link). */
    boolean vatStreamWater;

    /**
     * Pushes reservoir goo to the base gasket partner on a timed interval.
     * Final, assigned in constructor.
     */
    final GasketPusher gasketPusher;

    /**
     * Creates a new vat block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public VatBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VAT.get(), pos, state, be -> GasketAttachment.dual(be, "cap", "base"));
        this.gasketPusher = gasket().singlePusher(fluidHandler);
        fluidHandler.readDemandFrom(gasketPusher::partnerStatedDemand);
        fluidHandler.holdTo(() -> gooLabel);
    }

    /**
     * Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VatBlockEntity be) {
        VesselSourcing.sourceTick(level, be.getContents(), be);
        be.gasketPusher.tick();
    }

    /**
     * Returns the current capacity, grown by the void held.
     *
     * @return the capacity
     */
    public int getCapacity() {
        return fluidHandler.currentCapacity();
    }

    /**
     * Returns the current goo contents.
     *
     * @return the contents
     */
    public GooContents getContents() {
        return fluidHandler.toGooContents();
    }

    /**
     * Inserts goo of the given type into the reservoir.
     *
     * @param type   the goo type
     * @param volume volume
     * @return the amount actually inserted
     */
    @Override
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int volume) {
        return fluidHandler.insertGoo(type, Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Extracts up to the given amount of a specific goo type.
     *
     * @param type   the goo type
     * @param amount maximum volume
     * @return the amount actually extracted
     */
    public int extractGoo(ResourceKey<GooTypeDefinition> type, int amount) {
        return fluidHandler.extractGoo(type, Math.min(amount, Integer.MAX_VALUE), false);
    }

    // --- Public accessors ---

    /**
     * Returns the fluid handler for Transfer API.
     *
     * @return the fluid handler
     */
    public GooFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    /**
     * Returns true if the vat has room for more goo.
     *
     * @return true if accept
     */
    public boolean canAccept() {
        return fluidHandler.totalVolume() < getCapacity();
    }

    /**
     * A standing canister click on a vat holding goo is the empty-hand unpack into the inventory
     * (decision canister-click-is-any-other-click-on-crucible-and-vat).
     */
    @Override
    public boolean takesCanisterClick(ItemStack canister) {
        return !isEmpty();
    }

    /**
     * Returns the dominant goo type, or null.
     *
     * @return the dominant type
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> getDominantType() {
        return fluidHandler.largestType();
    }

    /**
     * Returns true if the vat contains no goo.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return fluidHandler.isEmpty();
    }

    /**
     * Each fluid entry glows against the vat's current capacity. Void grows
     * capacity, so the same mB amount of glow goo emits less light in a vat
     * holding void -- intentional, scales with the visible fill ratio.
     *
     * @return one light entry per goo type held
     */
    @Override
    protected List<GooLightEntry> lightEntries() {
        return GooLightEntry.ofContents(fluidHandler.toGooContents(), getCapacity());
    }

    /**
     * Returns the player-assigned label, or null.
     *
     * @return the label
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * Sets the player-assigned label (null to clear).
     *
     * @param label the label
     */
    public void setLabel(@Nullable String label) {
        this.label = label;
        markDirtyAndSync();
    }

    /**
     * Returns the goo type the vat's goo label holds it to, or null.
     *
     * @return the goo label type
     */
    public @Nullable ResourceKey<GooTypeDefinition> getGooLabel() {
        return gooLabel;
    }

    /**
     * Puts a goo label on the vat where the label rule lets it on.
     * decision label-goes-on-an-empty-or-matching-vessel
     *
     * @param type the label's goo type
     * @return true when the label went on
     */
    public boolean applyGooLabel(ResourceKey<GooTypeDefinition> type) {
        if (!GooLabelRule.canLabelVat(getContents(), getWaterVolume(), gooLabel, type)) {
            return false;
        }
        gooLabel = type;
        markDirtyAndSync();
        return true;
    }

    /**
     * Returns the stream goo type, or null once the stream's hold has passed.
     *
     * @param currentTick the current game tick
     * @return the vat stream type
     */
    public @Nullable ResourceKey<GooTypeDefinition> getVatStreamType(long currentTick) {
        return GooStream.holds(currentTick, vatStreamTick) ? vatStreamType : null;
    }

    /**
     * Returns the volume the stream's last landing tick carried in mB, or 0 once its hold has passed.
     *
     * @param currentTick the current game tick
     * @return the vat stream rate
     */
    public int getVatStreamRate(long currentTick) {
        return GooStream.holds(currentTick, vatStreamTick) ? vatStreamRate : 0;
    }

    /**
     * Returns true while water is pouring in, until the stream's hold has passed.
     *
     * @param currentTick the current game tick
     * @return true when the vat's stream is water
     */
    public boolean isVatStreamWater(long currentTick) {
        return vatStreamWater && GooStream.holds(currentTick, vatStreamTick);
    }

    /**
     * Returns the water the vat holds beside its goo.
     *
     * @return the water volume in mB
     */
    public int getWaterVolume() {
        return fluidHandler.waterVolume();
    }

    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        double localY = hit.getLocation().y - getBlockPos().getY();
        return GasketRegionResolver.resolveVatRole(hit.getDirection(), localY);
    }

    // --- IGasketHolder ---

    /**
     * {@inheritDoc} Checks per-role blockstate properties.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return holdsBlockGasket(role);
    }

    @Override
    public BooleanProperty gasketFlag(GasketRole role) {
        return role == GasketRole.RECEIVER ? VatBlock.GASKET_CAP : VatBlock.GASKET_BASE;
    }

    /**
     * The cap gasket is the block's upper half and the base gasket its lower half
     * (decision hosts-answer-bounds-through-interfaces).
     */
    @Override
    public @Nullable AABB slotBoundsFor(int slot, GasketRole role) {
        if (!holdsBlockGasket(role)) {
            return null;
        }
        return role == GasketRole.RECEIVER ? CAP_GASKET_BOUNDS : BASE_GASKET_BOUNDS;
    }

    @Override
    public @Nullable String getMachineLabel(int slot) {
        return label;
    }

    /**
     * Snapshots stream state, syncs to clients, and redistributes if stacked.
     */
    private void onFluidChanged() {
        VatSerialization.snapshotStream(this);
        markDirtyAndSync();
        VatSerialization.tryRedistribute(this);
    }

    /** Marks dirty and syncs to clients. Delegates to the gasket attachment. */
    void markDirtyAndSync() {
        gasket().syncToClients();
    }

    // --- Internal ---

    /**
     * Called by {@link VatStackRedistributor} after bulk-loading.
     */
    void syncAfterRedistribution() {
        markDirtyAndSync();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        VatSerialization.saveFields(this, output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        VatSerialization.loadFields(this, input);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        VatSerialization.collectContents(builder, fluidHandler.toGooContents());
        VatSerialization.collectGooLabel(builder, gooLabel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        GooContents contents = VatSerialization.applyContents(getter);
        if (!contents.holdsNothing()) {
            fluidHandler.loadFrom(contents);
        }
        gooLabel = getter.get(GooDataComponents.GOO_LABEL.get());
    }
}
