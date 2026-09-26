package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.*;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Core crucible logic: melts items into goo via a per-tick drain pipeline.
 *
 * <p>Internal logic delegated to:
 * {@link CrucibleMelting} (tick pipeline),
 * {@link CrucibleInsertion} (item/goo insertion),
 * {@link CrucibleSerialization} (NBT).</p>
 */
public class CrucibleBlockEntity extends GooGlowingMachineBlockEntity implements IGooReceptacle {

    /** Reference saturation cap (mB) for crucible reservoir light scaling.
     * Mirrors the BER's visual fill cap so the light response tracks the
     * surface fill the player sees. */
    private static final int LIGHT_REFERENCE_CAPACITY = 64_000;

    /** Base ignition spray duration in ticks. */
    static final int IGNITION_BASE_TICKS = 4;
    /** Random variance added to ignition spray duration (exclusive bound). */
    static final int IGNITION_RANDOM_TICKS = 2;
    /** Minimum ticks between sizzle sounds (debounce). */
    private static final int SIZZLE_DEBOUNCE_TICKS = 20;

    // Package-private fields accessed by CrucibleMelting, CrucibleInsertion, CrucibleSerialization.

    /** NBT key for goo reservoir contents. */
    static final String TAG_RESERVOIR = "Reservoir";
    /** NBT key for the melting item stack. */
    static final String TAG_MELTING_ITEM = "MeltingItem";
    /** NBT key for the heat ticks left. */
    static final String TAG_HEAT_TICKS = "HeatTicks";
    /** NBT key for the fuel goo type that bought the heat. */
    static final String TAG_HEAT_FUEL = "HeatFuel";
    /** NBT key for face label. */
    private static final String TAG_CRUCIBLE = "crucible";

    ItemStack meltingItem = ItemStack.EMPTY;

    /** Heat bought from fuel goo, spent one tick per melt tick (decision fuel-goo-heats-per-mb). */
    final CrucibleHeat heat = new CrucibleHeat();

    /** Multi-type goo reservoir backed by the Transfer API. */
    final GooFluidHandler reservoir = GooFluidHandler.withCapacityPerType(
        CrucibleCapacity.TYPE_CAPACITY, gasket().syncCallback());

    /** The reservoir as the stock the heat buys fuel from. */
    final CrucibleHeat.FuelStock fuelStock = new CrucibleHeat.FuelStock() {
        @Override
        public int volume(ResourceKey<GooTypeDefinition> type) {
            return reservoir.getVolume(type);
        }

        @Override
        public int extract(ResourceKey<GooTypeDefinition> type, int amount) {
            return reservoir.extractGoo(type, amount, false);
        }
    };

    /** Game time of the last sizzle sound play (debounce, not serialized). */
    private long lastSizzleTick;

    /** Per-instance bubble spawn history for proximity rejection. */
    final CrucibleParticleHelper.BubbleHistory bubbleHistory =
        new CrucibleParticleHelper.BubbleHistory();

    /** Evaluates container items (shulker boxes, bundles) for goo content. */
    final ContainerEvaluator containerEvaluator = new ContainerEvaluator();

    /** Number of remaining ticks to spray ignition sparks. */
    int ignitionSprayTicks;

    /** Pushes reservoir goo to gasket partners on a timed interval. Final, assigned in constructor. */
    final GasketPusher gasketPusher;

    /**
     * Creates a crucible block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CRUCIBLE.get(), pos, state,
            be -> GasketAttachment.single(be, GasketRole.TRANSMITTER, TAG_CRUCIBLE));
        this.gasketPusher = gasket().singlePusher(reservoir);
    }

    // --- Heat ---

    /** Adds heat ticks at blaze's grade, as a grant outside the fuel goo.
     *
     * @param ticks the heat ticks to add
     */
    public void addHeat(int ticks) {
        heat.set(heat.heatTicks() + ticks, FuelGrade.configuredBlaze());
        syncToClients();
    }

    /** Returns the heat ticks left.
     *
     * @return the heat ticks
     */
    public int heatTicks() { return heat.heatTicks(); }

    /** Returns true when the crucible holds heat or fuel goo to buy it with.
     *
     * @return true if the crucible can melt
     */
    public boolean canHeat() {
        return heat.canHeat(FuelGrade.configured(), fuelStock);
    }

    /** Returns the burns the heat and fuel goo hold, combo first.
     *
     * @return the burns
     */
    public List<CrucibleHeat.FuelBurn> burnForecast() {
        return heat.forecast(FuelGrade.configured(), GooConfig.COMBO_DRAIN_PER_TICK.get(), fuelStock::volume);
    }

    /** Returns true if the crucible is enabled (no redstone signal).
     *
     * @return true if enabled
     */
    public boolean isEnabled() { return !getBlockState().getValue(CrucibleBlock.POWERED); }

    /**
     * Returns the backing fluid handler for direct Transfer API access.
     *
     * @return the fluid handler
     */
    public GooFluidHandler reservoirHandler() { return reservoir; }

    /**
     * Returns the current goo contents as an immutable snapshot.
     *
     * @return the snapshot
     */
    public GooContents getReservoir() { return reservoir.toGooContents(); }

    /**
     * Inserts goo of the given type into the reservoir.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    @Override
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int volume) {
        return reservoir.insertGoo(type, Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Extracts up to the given amount of a specific goo type.
     *
     * @param type   the goo type
     * @param amount maximum volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(ResourceKey<GooTypeDefinition> type, int amount) {
        return reservoir.extractGoo(type, Math.min(amount, Integer.MAX_VALUE), false);
    }

    /** Empties the entire reservoir. */
    public void drainReservoir() {
        reservoir.loadFrom(GooContents.EMPTY);
        syncToClients();
    }

    /**
     * Each reservoir entry glows against {@link #LIGHT_REFERENCE_CAPACITY},
     * the visual fill cap.
     *
     * @return one light entry per reservoir type
     */
    @Override
    protected List<GooLightEntry> lightEntries() {
        return GooLightEntry.ofContents(reservoir.toGooContents(), LIGHT_REFERENCE_CAPACITY);
    }

    /** Returns the melting item stack (may be empty).
     *
     * @return the melting item
     */
    public ItemStack getMeltingItem() { return meltingItem; }

    /** Returns the total mB remaining in the PMI pool.
     *
     * @return the pool volume
     */
    public long getPoolVolume() {
        return meltingItem.isEmpty() ? 0 : PartiallyMeltedItem.getContents(meltingItem).totalVolume();
    }

    /**
     * Returns the goo the drawn surface stands for: what has melted into the
     * reservoir, never the unmelted item's pool, so a first melt tick draws a
     * puddle (decision puddle-touches-walls-at-a-thousand).
     *
     * @return the surface volume in mB
     */
    public long getSurfaceVolume() {
        return reservoir.totalVolume();
    }

    /** Returns true when neither the reservoir nor the PMI pool holds goo.
     *
     * @return true if the crucible holds no goo
     */
    public boolean holdsNoGoo() {
        return CrucibleBasin.holdsNoGoo(reservoir.totalVolume(), getPoolVolume());
    }

    /** Returns true if enough time has passed since the last sizzle sound.
     *
     * @param gameTime the game time
     * @return true if should play sizzle
     */
    public boolean shouldPlaySizzle(long gameTime) {
        if (gameTime - lastSizzleTick >= SIZZLE_DEBOUNCE_TICKS) {
            lastSizzleTick = gameTime;
            return true;
        }
        return false;
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        CrucibleMelting.serverTick(be, level, pos, state);
    }

    // --- IGasketHolder ---

    @Override
    public GasketRole resolveRole(BlockHitResult hit) { return GasketRegionResolver.resolveCrucibleRole(); }

    /** {@inheritDoc} Checks blockstate rather than static role. */
    @Override
    public boolean supportsRole(GasketRole role) { return getBlockState().getValue(CrucibleBlock.HAS_GASKET); }

    @Override
    public @Nullable BooleanProperty gasketFlag(GasketRole role) {
        return role == GasketRole.TRANSMITTER ? CrucibleBlock.HAS_GASKET : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CrucibleSerialization.saveMeltingState(this, output);
        GooContents reservoirContents = reservoir.toGooContents();
        if (!reservoirContents.isEmpty()) {
            output.store(TAG_RESERVOIR, GooContents.CODEC, reservoirContents);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CrucibleSerialization.loadMeltingState(this, input);
        reservoir.loadFrom(input.read(TAG_RESERVOIR, GooContents.CODEC).orElse(GooContents.EMPTY));
    }

    /** Marks dirty and syncs to tracking clients. Delegates to the gasket attachment. */
    void syncToClients() { gasket().syncToClients(); }
}
