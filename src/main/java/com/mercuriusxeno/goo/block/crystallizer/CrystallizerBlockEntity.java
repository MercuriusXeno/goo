package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The crystallizer's block entity (decision crystallizer-emits-chrysm): a gasket
 * receiver holding goo of one type and crystal goo, which crystallizes the goo
 * as it arrives, spending crystal at 10% of it, up to the tier the knob names.
 * The item inside is the highest tier the crystallized volume reached, and it
 * stays until a player takes it; the remainder stays crystallized.
 */
public class CrystallizerBlockEntity extends GooMachineBlockEntity implements IGooReceptacle {

    private static final String FACE_LABEL = "crystallizer";
    private static final String TAG_HELD = "Held";
    private static final String TAG_CRYSTALLIZED = "Crystallized";
    private static final String TAG_FORMING_TYPE = "FormingType";

    private final CrystallizerTank tank;
    /** Idle ticks the inlay stays lit after the last crystallizing, so a trickle feed reads steady. */
    private static final int ACTIVE_HOLD_TICKS = 20;

    private long crystallized;
    private int idleTicks;
    private @Nullable ResourceKey<GooTypeDefinition> formingType;

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
        return new CrystallizerPhases.Chamber(tank.toGooContents(), crystallized, formingType,
                CrystallizerBlock.knobTier(getBlockState()));
    }

    /**
     * Server tick: crystallizes what the held crystal pays for.
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
     * Runs one tick's crystallizing.
     */
    void advance() {
        CrystallizerPhases.Step step = CrystallizerPhases.step(chamber());
        if (step == null) {
            idleTicks++;
            if (idleTicks == ACTIVE_HOLD_TICKS) {
                showActive(false);
            }
            return;
        }
        idleTicks = 0;
        showActive(true);
        tank.extractGoo(step.type(), step.goo(), false);
        tank.extractGoo(CrystallizerPhases.CATALYST, step.crystal(), false);
        crystallized += step.goo();
        formingType = step.type();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Lights or darkens the model's inlay, touching the block state only when it changes.
     *
     * @param active whether the crystallizer is crystallizing
     */
    private void showActive(boolean active) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(CrystallizerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(CrystallizerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * @return the goo crystallized so far, in mB
     */
    public long crystallized() {
        return crystallized;
    }

    /**
     * @return the highest tier the crystallized volume reached, or null
     */
    public @Nullable ChrysmTier formedTier() {
        return CrystallizerPhases.reachedTier(crystallized);
    }

    /**
     * @return one chrysm of the highest tier reached, or EMPTY
     */
    public ItemStack formed() {
        ChrysmTier tier = formedTier();
        return tier == null || formingType == null ? ItemStack.EMPTY : ChrysmItem.stackOf(tier, formingType);
    }

    /**
     * Takes one chrysm of the highest tier reached out, keeping the remainder crystallized.
     *
     * @return the chrysm, or EMPTY when no tier is reached
     */
    public ItemStack takeFormed() {
        ItemStack taken = formed();
        ChrysmTier tier = formedTier();
        if (taken.isEmpty() || tier == null) {
            return ItemStack.EMPTY;
        }
        crystallized -= tier.volume();
        if (crystallized == 0 && CrystallizerPhases.formingType(tank.toGooContents()) == null) {
            formingType = null;
        }
        BlockEntitySync.markDirtyAndSync(this);
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
        output.putLong(TAG_CRYSTALLIZED, crystallized);
        output.storeNullable(TAG_FORMING_TYPE, GooTypes.KEY_CODEC, formingType);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        tank.loadFrom(input.read(TAG_HELD, GooContents.CODEC).orElse(GooContents.EMPTY));
        crystallized = input.getLongOr(TAG_CRYSTALLIZED, 0L);
        formingType = input.read(TAG_FORMING_TYPE, GooTypes.KEY_CODEC).orElse(null);
    }
}
