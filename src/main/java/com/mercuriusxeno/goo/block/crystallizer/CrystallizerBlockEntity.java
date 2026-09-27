package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooMachineBlockEntity;
import com.mercuriusxeno.goo.block.IGooReceptacle;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.AddressedGasket;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.item.ChrysmItem;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's block entity (decision crystallizer-emits-chrysm): a gasket
 * receiver holding goo of one type and crystal goo, which forms one chrysm of
 * that type and keeps advancing it a tier per phase, each phase spending the goo
 * between the tiers and its crystal over its time, until the tier the dial
 * names. The chrysm stays inside until a player takes it.
 */
public class CrystallizerBlockEntity extends GooMachineBlockEntity implements IGooReceptacle {

    private static final String FACE_LABEL = "crystallizer";
    private static final String TAG_HELD = "Held";
    private static final String TAG_FORMED = "Formed";
    private static final String TAG_PROGRESS = "Progress";

    private final CrystallizerTank tank;
    private ItemStack formed = ItemStack.EMPTY;
    private int progressTicks;

    /**
     * @param pos   the block position
     * @param state the block state
     */
    public CrystallizerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CRYSTALLIZER.get(), pos, state,
                be -> GasketAttachment.single(be, GasketRole.RECEIVER, FACE_LABEL));
        this.tank = new CrystallizerTank(() -> BlockEntitySync.markDirtyAndSync(this), this::capacityFor);
    }

    private int capacityFor(ResourceKey<GooTypeDefinition> type) {
        return CrystallizerPhases.capacityFor(chamber(), type);
    }

    /**
     * @return the crystallizer's state in plain values
     */
    CrystallizerPhases.Chamber chamber() {
        return new CrystallizerPhases.Chamber(tank.toGooContents(), formedTier(),
                formed.get(GooDataComponents.GOO_TYPE.get()), CrystallizerBlock.dialTier(getBlockState()));
    }

    /**
     * Server tick: while the holding carries what the phase in progress needs,
     * counts toward its tier; a holding that stops being ready restarts the count.
     *
     * @param level        the level
     * @param pos          the block position
     * @param state        the block state
     * @param crystallizer the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystallizerBlockEntity crystallizer) {
        crystallizer.advance();
    }

    /**
     * Runs one tick of the phase in progress.
     */
    void advance() {
        CrystallizerPhases.Chamber chamber = chamber();
        GooContents spent = CrystallizerPhases.spentToForm(chamber);
        ChrysmTier goal = CrystallizerPhases.goal(chamber);
        if (spent == null || goal == null) {
            progressTicks = 0;
            return;
        }
        progressTicks++;
        if (progressTicks >= CrystallizerPhases.phaseTicks(goal)) {
            form(goal, CrystallizerPhases.formingType(chamber), spent);
        }
        setChanged();
    }

    private void form(ChrysmTier goal, ResourceKey<GooTypeDefinition> forming, GooContents spent) {
        spent.getAll().forEach((type, volume) -> tank.extractGoo(type, volume, false));
        formed = ChrysmItem.stackOf(goal, forming);
        progressTicks = 0;
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * @return the tier standing formed inside, or null
     */
    public @Nullable ChrysmTier formedTier() {
        return formed.getItem() instanceof ChrysmItem chrysm ? chrysm.tier() : null;
    }

    /**
     * @return a copy of the chrysm standing formed inside, or EMPTY
     */
    public ItemStack formed() {
        return formed.copy();
    }

    /**
     * Takes the formed chrysm out.
     *
     * @return the chrysm, or EMPTY when none stands formed
     */
    public ItemStack takeFormed() {
        ItemStack taken = formed;
        formed = ItemStack.EMPTY;
        if (!taken.isEmpty()) {
            BlockEntitySync.markDirtyAndSync(this);
        }
        return taken;
    }

    /**
     * @return the goo the crystallizer holds
     */
    public GooContents held() {
        return tank.toGooContents();
    }

    /**
     * @return the holding a gasket link pours into
     */
    public GooFluidHandler tank() {
        return tank;
    }

    @Override
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int volume) {
        return tank.insertGoo(type, volume, false);
    }

    // --- IGasketHolder (RECEIVER only) ---

    /**
     * {@inheritDoc} A crystallizer only receives.
     */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        return GasketRole.RECEIVER;
    }

    @Override
    public boolean supportsRole(GasketRole role) {
        return role == GasketRole.RECEIVER && getBlockState().getValue(CrystallizerBlock.HAS_GASKET);
    }

    /**
     * The crystallizer carries one gasket, so any hit addresses it.
     */
    @Override
    public @Nullable AddressedGasket addressedGasket(BlockHitResult hit) {
        return holdsBlockGasket(GasketRole.RECEIVER)
                ? new AddressedGasket(GasketRole.RECEIVER, GooConstants.NO_SLOT) : null;
    }

    @Override
    public @Nullable BooleanProperty gasketFlag(GasketRole role) {
        return role == GasketRole.RECEIVER ? CrystallizerBlock.HAS_GASKET : null;
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_HELD, GooContents.CODEC, tank.toGooContents());
        if (!formed.isEmpty()) {
            output.store(TAG_FORMED, ItemStack.CODEC, formed);
        }
        output.putInt(TAG_PROGRESS, progressTicks);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        tank.loadFrom(input.read(TAG_HELD, GooContents.CODEC).orElse(GooContents.EMPTY));
        formed = input.read(TAG_FORMED, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        progressTicks = input.getIntOr(TAG_PROGRESS, 0);
    }
}
