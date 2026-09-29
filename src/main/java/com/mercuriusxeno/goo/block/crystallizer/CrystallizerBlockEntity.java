package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.ICutawayMachine;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.ICanisterAttachable;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Held;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Roles;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ChrysmItem;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The crystallizer's block entity (decision crystallizer-emits-chrysm): two
 * canisters stand on its top, whichever holds crystal the catalyst and the
 * other's goo what grows, and the goo crystallizes as it goes, spending crystal
 * at 10% of it, up to the tier the knob names. The item inside is the highest
 * tier the crystallized volume reached, and it stays until a player takes it;
 * the remainder stays crystallized. The canisters stand in a canister block on
 * its top, as the reactor's inputs do, in the two slots {@link CrystallizerLayout}
 * names for its facing; goo reaches them through that block's slot gaskets.
 */
public class CrystallizerBlockEntity extends GooGlowingMachineBlockEntity
        implements ICanisterAttachable, ICutawayMachine {

    /** The two canisters, 0 back left and 1 back right. */
    private static final int ROLE_COUNT = 2;

    /** The two canisters it reads, back left then back right. */
    public static final int SLOT_COUNT = CrystallizerLayout.CANISTER_COUNT;

    private static final String TAG_CRYSTALLIZED = "Crystallized";
    private static final String TAG_FORMING_TYPE = "FormingType";
    /** Idle ticks the inlay stays lit after the last crystallizing, so a trickle feed reads steady. */
    private static final int ACTIVE_HOLD_TICKS = 20;

    private long crystallized;
    /** Client only: the crystal growth drawn this tick and last tick, eased toward the synced volume. */
    private double displayedGrowth;
    private double previousGrowth;
    /** The pace's unspent mB, carried across ticks; not saved, a reload starts the tick afresh. */
    private double paceBudget;
    private int idleTicks;
    private @Nullable ResourceKey<GooTypeDefinition> formingType;

    /**
     * @param pos      the block position
     * @param blockState the block state
     */
    public CrystallizerBlockEntity(BlockPos pos, BlockState blockState) {
        // No gaskets of its own: the canister block on its top holds its canisters' gaskets.
        super(GooBlockEntities.CRYSTALLIZER.get(), pos, blockState, GasketAttachment::none);
    }

    /**
     * Server tick: the crystallizer crystallizes what the catalyst canister pays for.
     *
     * @param level        the level
     * @param pos          the block position
     * @param blockState   the block state
     * @param crystallizer the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState blockState,
                                  CrystallizerBlockEntity crystallizer) {
        crystallizer.advance();
    }

    /**
     * Client tick: eases the drawn crystal growth toward the synced volume, so the
     * crystal grows smoothly between server ticks.
     *
     * @param level        the level
     * @param pos          the block position
     * @param blockState   the block state
     * @param crystallizer the block entity
     */
    public static void clientTick(Level level, BlockPos pos, BlockState blockState,
                                  CrystallizerBlockEntity crystallizer) {
        crystallizer.previousGrowth = crystallizer.displayedGrowth;
        crystallizer.displayedGrowth = CrystalCluster.ease(crystallizer.displayedGrowth,
                CrystalCluster.growth(crystallizer.crystallized));
    }

    /**
     * The crystal growth to draw this frame, between last tick's and this tick's.
     *
     * @param partialTick the share of the tick past
     * @return the growth, from 0 to 1
     */
    public double drawnGrowth(float partialTick) {
        return previousGrowth + (displayedGrowth - previousGrowth) * partialTick;
    }

    /**
     * Runs one tick's crystallizing.
     */
    void advance() {
        Held first = held(0);
        Held second = held(1);
        Roles roles = CrystallizerPhases.roles(first, second);
        paceBudget = CrystallizerPhases.nextBudget(paceBudget, crystallized);
        CrystallizerPhases.Step step = roles == null ? null : CrystallizerPhases.step(
                roles.ingredient() == 0 ? first : second, roles.catalyst() == 0 ? first : second,
                crystallized, formingType, CrystallizerBlock.knobTier(getBlockState()), paceBudget);
        if (step == null) {
            idleTicks++;
            if (idleTicks == ACTIVE_HOLD_TICKS) {
                showActive(false);
            }
            return;
        }
        idleTicks = 0;
        paceBudget -= step.goo();
        showActive(true);
        spend(roles, step);
        crystallized += step.goo();
        formingType = step.type();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Draws a step's goo from the ingredient canister and its crystal from the catalyst canister.
     *
     * @param roles which canister is which
     * @param step  the step crystallized
     */
    private void spend(Roles roles, CrystallizerPhases.Step step) {
        CanisterBlockEntity canisters = canistersAbove();
        if (canisters != null) {
            canisters.extractGoo(canisterSlot(roles.ingredient()), step.type(), step.goo());
            canisters.extractGoo(canisterSlot(roles.catalyst()), CrystallizerPhases.CATALYST, step.crystal());
        }
    }

    /**
     * @param role the canister, 0 back left or 1 back right
     * @return what its canister in the canister block above holds, in plain values
     */
    Held held(int role) {
        CanisterBlockEntity canisters = canistersAbove();
        CanisterFluidContent content = canisters == null ? CanisterFluidContent.EMPTY
                : canisters.getSlotFluidContent(canisterSlot(role));
        return content.isEmpty() ? Held.NOTHING : new Held(content.getGooType(), content.amount());
    }

    /**
     * @return the canister block standing on the top, or null
     */
    public @Nullable CanisterBlockEntity canistersAbove() {
        return level != null && level.getBlockEntity(worldPosition.above()) instanceof CanisterBlockEntity canisters
                ? canisters : null;
    }

    /**
     * The canister block slot a canister role reads, fixed per facing.
     *
     * @param role the canister, 0 back left or 1 back right
     * @return the slot index in the canister block above
     */
    public int canisterSlot(int role) {
        return CrystallizerLayout.slot(facing(), role);
    }

    /**
     * Lights or darkens the model's inlay, touching the block state only when it changes.
     *
     * @param active whether the crystallizer is crystallizing
     */
    private void showActive(boolean active) {
        BlockState current = getBlockState();
        if (level != null && current.getValue(CrystallizerBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, current.setValue(CrystallizerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * @return the goo crystallized so far, in mB
     */
    public long crystallized() {
        return crystallized;
    }

    /**
     * @return the type crystallized so far, or null when none is
     */
    public @Nullable ResourceKey<GooTypeDefinition> formingType() {
        return formingType;
    }

    /**
     * @return the highest tier the crystallized volume reached, or null
     */
    public @Nullable ChrysmTier formedTier() {
        return CrystallizerPhases.reachedTier(crystallized);
    }

    /**
     * @param knob the tier the knob names, or null when it is off
     * @return true when the crystal is mature, as {@link CrystallizerPhases#isMature} reads it
     */
    public boolean isMature(@Nullable ChrysmTier knob) {
        return CrystallizerPhases.isMature(crystallized, knob);
    }

    /**
     * @return one chrysm of the tier a click hands, once the crystal is mature, or EMPTY
     */
    public ItemStack formed() {
        ChrysmTier tier = CrystallizerPhases.harvestTier(crystallized, CrystallizerBlock.knobTier(getBlockState()));
        return tier == null || formingType == null ? ItemStack.EMPTY : ChrysmItem.stackOf(tier, formingType);
    }

    /**
     * Takes a mature crystal whole (operator ruling): one chrysm of the tier a click
     * hands, then the excess past that tier as an omniblob of its goo and one of the
     * crystal spent on it, so nothing is lost; the crystallizer empties. A part-grown
     * crystal hands nothing.
     *
     * @return the chrysm first, then the excess omniblobs; none while the crystal is not mature
     */
    public List<ItemStack> takeFormed() {
        ChrysmTier tier = CrystallizerPhases.harvestTier(crystallized, CrystallizerBlock.knobTier(getBlockState()));
        List<ItemStack> taken = new ArrayList<>();
        if (tier == null || formingType == null) {
            return taken;
        }
        taken.add(ChrysmItem.stackOf(tier, formingType));
        int excess = Math.toIntExact(crystallized - tier.volume());
        if (excess > 0) {
            taken.add(BlobStacks.createForOutput(formingType, excess));
            ItemStack crystal = BlobStacks.createForOutput(CrystallizerPhases.CATALYST,
                    excess / CrystallizerPhases.GOO_PER_CRYSTAL);
            if (!crystal.isEmpty()) {
                taken.add(crystal);
            }
        }
        crystallized = 0;
        formingType = null;
        paceBudget = 0;
        BlockEntitySync.markDirtyAndSync(this);
        return taken;
    }

    // --- ICanisterAttachable: its canisters stand in the canister block on its top ---

    private Direction facing() {
        return getBlockState().getValue(CrystallizerBlock.FACING);
    }

    /**
     * Operator ruling: the crystallizer takes two canisters, back left and back right.
     *
     * @return the two canister block slots it reads
     */
    @Override
    public boolean admitsGoo(int slot, @Nullable ResourceKey<GooTypeDefinition> incoming) {
        for (int role = 0; role < ROLE_COUNT; role++) {
            if (canisterSlot(role) == slot) {
                return CrystallizerPhases.admits(held(ROLE_COUNT - 1 - role), incoming);
            }
        }
        return true;
    }

    @Override
    public Set<Integer> allowedSlots() {
        return CrystallizerLayout.allowedSlots(facing());
    }

    @Override
    public int maxTopAttachments() {
        return SLOT_COUNT;
    }

    @Override
    public int currentTopAttachments() {
        CanisterBlockEntity canisters = canistersAbove();
        if (canisters == null) {
            return 0;
        }
        int count = 0;
        for (int role = 0; role < SLOT_COUNT; role++) {
            if (canisters.isSlotFilled(canisterSlot(role))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Operator ruling: the canisters stand off the grid, at the model's (4, 4) and (12, 4).
     *
     * @return the grid with the two slots it reads moved to those centers, turned to its facing
     */
    @Override
    public float[][] slotCenters() {
        return CrystallizerLayout.centers(facing());
    }

    /**
     * The knob and a mature crystal take a held canister's click, so no canister block places there.
     *
     * @param hit the hit on the crystallizer
     * @return true when the hit lands on the knob or a mature crystal
     */
    @Override
    public boolean isCutawayHit(BlockHitResult hit) {
        return CrystallizerBlock.hitsKnobOrMatureCrystal(this, getBlockState(), getBlockPos(), hit);
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putLong(TAG_CRYSTALLIZED, crystallized);
        output.storeNullable(TAG_FORMING_TYPE, GooTypes.KEY_CODEC, formingType);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        crystallized = input.getLongOr(TAG_CRYSTALLIZED, 0L);
        formingType = input.read(TAG_FORMING_TYPE, GooTypes.KEY_CODEC).orElse(null);
    }
}
