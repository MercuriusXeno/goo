package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.canister.HudAnchor;
import com.mercuriusxeno.goo.block.canister.HudViewer;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Held;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Roles;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ChrysmItem;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * The crystallizer's block entity (decision crystallizer-emits-chrysm): two
 * canisters stand on its top, whichever holds crystal the catalyst and the
 * other's goo what grows, and the goo crystallizes as it goes, spending crystal
 * at 10% of it, up to the tier the knob names. The item inside is the highest
 * tier the crystallized volume reached, and it stays until a player takes it;
 * the remainder stays crystallized. Goo reaches it only through the canisters,
 * each carrying its own gaskets as the reactor's output canister does.
 */
public class CrystallizerBlockEntity extends GooGlowingMachineBlockEntity implements ICanisterHolder {

    /** The two canister slots on the top, back left then back right. */
    public static final int SLOT_COUNT = 2;

    private static final String TAG_CANISTERS = "Canisters";
    private static final String TAG_CRYSTALLIZED = "Crystallized";
    private static final String TAG_FORMING_TYPE = "FormingType";
    /** Idle ticks the inlay stays lit after the last crystallizing, so a trickle feed reads steady. */
    private static final int ACTIVE_HOLD_TICKS = 20;

    private final SlottedCanisterData state = new SlottedCanisterData(this, SLOT_COUNT,
            i -> Shapes.empty(), slots -> Shapes.empty());
    private long crystallized;
    private int idleTicks;
    private @Nullable ResourceKey<GooTypeDefinition> formingType;

    /**
     * @param pos      the block position
     * @param blockState the block state
     */
    public CrystallizerBlockEntity(BlockPos pos, BlockState blockState) {
        // Roleless: each canister's metadata holds its gasket ids, as on the reactor.
        super(GooBlockEntities.CRYSTALLIZER.get(), pos, blockState, GasketAttachment::none);
        GasketAttachment gasket = gasket();
        gasket.rebuildPushers(this.state::rebuildAllPushers);
        gasket.afterLoad(() -> {
            if (level instanceof ServerLevel serverLevel) {
                for (int slot = 0; slot < SLOT_COUNT; slot++) {
                    GasketPusher.forceTransmitterChunk(getSlotMetadata(slot).topGasketId(),
                            gasket.registryAccess(), serverLevel, worldPosition);
                }
            }
        });
    }

    /**
     * Server tick: the canisters push through their gaskets, then the crystallizer
     * crystallizes what the catalyst canister pays for.
     *
     * @param level        the level
     * @param pos          the block position
     * @param blockState   the block state
     * @param crystallizer the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState blockState,
                                  CrystallizerBlockEntity crystallizer) {
        crystallizer.state.tickPushers();
        crystallizer.advance();
    }

    /**
     * Runs one tick's crystallizing.
     */
    void advance() {
        Held first = held(0);
        Held second = held(1);
        Roles roles = CrystallizerPhases.roles(first, second);
        CrystallizerPhases.Step step = roles == null ? null : CrystallizerPhases.step(
                roles.ingredient() == 0 ? first : second, roles.catalyst() == 0 ? first : second,
                crystallized, formingType, CrystallizerBlock.knobTier(getBlockState()));
        if (step == null) {
            idleTicks++;
            if (idleTicks == ACTIVE_HOLD_TICKS) {
                showActive(false);
            }
            return;
        }
        idleTicks = 0;
        showActive(true);
        extractGoo(roles.ingredient(), step.type(), step.goo());
        extractGoo(roles.catalyst(), CrystallizerPhases.CATALYST, step.crystal());
        crystallized += step.goo();
        formingType = step.type();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * @param slot the canister slot
     * @return what its canister holds, in plain values
     */
    Held held(int slot) {
        CanisterFluidContent content = getSlotFluidContent(slot);
        return content.isEmpty() ? Held.NOTHING : new Held(content.getGooType(), content.amount());
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
        if (crystallized == 0) {
            formingType = null;
        }
        BlockEntitySync.markDirtyAndSync(this);
        return taken;
    }

    // --- Canister slots ---

    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    @Override
    protected SlottedCanisterData heldSlots() {
        return state;
    }

    private Direction facing() {
        return getBlockState().getValue(CrystallizerBlock.FACING);
    }

    @Override
    public @Nullable AABB slotBounds(int index) {
        return index >= 0 && index < SLOT_COUNT ? CrystallizerBlock.canisterSlotShape(facing(), index).bounds() : null;
    }

    @Override
    public VoxelShape outlineShape(BlockHitResult hit) {
        return getBlockState().getShape(getLevel(), getBlockPos());
    }

    /**
     * The canister the hit lands on, when its slot is filled.
     */
    @Override
    public @Nullable AABB pickupBounds(BlockHitResult hit) {
        int slot = CrystallizerBlock.slotAt(getBlockState(), getBlockPos(), hit);
        return slot != NO_SLOT && isSlotFilled(slot) ? slotBounds(slot) : null;
    }

    @Override
    public @Nullable AABB previewBounds(BlockHitResult hit, boolean sneaking) {
        int slot = CrystallizerBlock.slotAt(getBlockState(), getBlockPos(), hit);
        return slot != NO_SLOT && !isSlotFilled(slot) ? slotBounds(slot) : null;
    }

    /**
     * Over the canister the hit lands on.
     */
    @Override
    public @Nullable HudAnchor hudAnchor(BlockHitResult hit, HudViewer viewer) {
        int slot = CrystallizerBlock.slotAt(getBlockState(), getBlockPos(), hit);
        if (slot == NO_SLOT || !isSlotFilled(slot)) {
            return null;
        }
        AABB bounds = CrystallizerBlock.canisterSlotShape(facing(), slot).bounds();
        return new HudAnchor(slot, bounds.getCenter().x, bounds.getCenter().z, bounds.maxY, Direction.UP);
    }

    /**
     * A standing canister click over a slot footprint goes into that slot.
     */
    @Override
    public boolean takesCanisterAt(BlockHitResult hit, boolean sneaking) {
        return !sneaking && CrystallizerBlock.slotAt(getBlockState(), getBlockPos(), hit) != NO_SLOT;
    }

    // --- IGasketHolder: each canister carries its own gaskets ---

    @Override
    public boolean supportsRole(GasketRole role) {
        return true;
    }

    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = CrystallizerBlock.slotAt(getBlockState(), getBlockPos(), hit);
        return slot != NO_SLOT ? slot : SLOT_MISS;
    }

    /**
     * A transmitter partner change re-stands that canister's pusher on the new link.
     */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER) {
            state.rebuildPusher(slot);
        }
    }

    // --- Serialization ---

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        state.save(output, TAG_CANISTERS);
        output.putLong(TAG_CRYSTALLIZED, crystallized);
        output.storeNullable(TAG_FORMING_TYPE, GooTypes.KEY_CODEC, formingType);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        state.load(input, TAG_CANISTERS);
        crystallized = input.getLongOr(TAG_CRYSTALLIZED, 0L);
        formingType = input.read(TAG_FORMING_TYPE, GooTypes.KEY_CODEC).orElse(null);
    }
}
