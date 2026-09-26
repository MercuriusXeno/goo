package com.mercuriusxeno.goo.block.reactor;

import com.mercuriusxeno.goo.block.GooGlowingMachineBlockEntity;
import com.mercuriusxeno.goo.block.ICutawayMachine;
import com.mercuriusxeno.goo.block.canister.*;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Reactor block entity: reads fluid from 4 corner canisters on top,
 * matches a reaction recipe, and pushes output into the front canister.
 * Redstone halts processing. Throughput scales with available batches
 * via power-law: ceil(minBatches ^ 0.35).
 *
 * <p>The output canister is a gasket holder over its single slot, the way a
 * canister block slot is: gasket ids and partners live on the canister item's
 * metadata, and the registry location follows the canister in and out.</p>
 */
public class ReactorBlockEntity extends GooGlowingMachineBlockEntity
        implements ICanisterHolder, ICanisterAttachable, ICutawayMachine {

    /**
     * Output canister slot index.
     */
    public static final int OUTPUT_SLOT = 0;
    /**
     * Corner slot indices in the canister block's 3x3 grid.
     */
    private static final int[] INPUT_SLOTS = {0, 2, 6, 8};
    /**
     * Only corner slots are valid for reactor input canisters.
     */
    private static final Set<Integer> CORNER_SLOTS = Set.of(0, 2, 6, 8);
    /**
     * The output hollow holds one canister.
     */
    private static final int OUTPUT_SLOT_COUNT = 1;
    /**
     * Power-law exponent for throughput scaling.
     */
    private static final double THROUGHPUT_EXPONENT = 0.35;
    /**
     * Block-local centre the HUD anchor is pushed out from.
     */
    private static final double HUD_CENTER = 0.5;
    /**
     * How far the HUD anchor sits out from the centre: half a block to the face plus one pixel.
     */
    private static final double HUD_FORWARD = 0.5 + 1.0 / 16.0;
    /**
     * HUD anchor Y: the hollow's mid-height, between pixels 1 and 13.
     */
    private static final double HUD_LIFT = 7.0 / 16.0;

    /**
     * NBT key for the output canister.
     */
    private static final String TAG_OUTPUT_CANISTER = "OutputCanister";
    /**
     * Slotted state for the single output canister.
     */
    private final SlottedCanisterData state = new SlottedCanisterData(this,
            OUTPUT_SLOT_COUNT,
            i -> Shapes.empty(),
            slots -> Shapes.empty());
    /**
     * Client-side wheel rotation angle in degrees. Not serialized.
     */
    public float wheelAngle;
    /**
     * Client-side wheel rotation speed in degrees per tick. Not serialized.
     */
    public float wheelSpeed;

    /** Wheel cycle in degrees -- one logical revolution. Public so the BER
     * can derive sprite-swap fractions from a single source of truth. */
    public static final float WHEEL_CYCLE_PERIOD = 90f;
    /** Max wheel speed in degrees per tick at full crafting. */
    private static final float MAX_WHEEL_SPEED = 24f;
    /** Acceleration in degrees/tick/tick when crafting. */
    private static final float WHEEL_ACCEL = 0.5f;
    /** Natural deceleration rate when crafting stops (degrees/tick/tick). */
    private static final float WHEEL_DECEL = 0.3f;
    /** Speed threshold below which the wheel hard-zeroes and snaps. */
    private static final float WHEEL_SPEED_EPSILON = 0.05f;
    /** Kinematic constant: stop distance under constant decel a is v^2 / (2a). */
    private static final float KINEMATIC_HALF = 2f;

    /**
     * Creates a reactor block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        // Roleless: the output canister's metadata holds the gasket ids,
        // as on a canister block slot (reactor-gasket-click-fix).
        super(GooBlockEntities.REACTOR.get(), pos, state, GasketAttachment::none);
        GasketAttachment gasket = gasket();
        gasket.rebuildPushers(this.state::rebuildAllPushers);
        gasket.afterLoad(() -> {
            if (level instanceof ServerLevel serverLevel) {
                GasketPusher.forceTransmitterChunk(getSlotMetadata(OUTPUT_SLOT).topGasketId(),
                        gasket.registryAccess(), serverLevel, worldPosition);
            }
        });
    }

    /**
     * Server tick: the output canister pushes through its bottom gasket as a
     * canister block slot does, redstone or not; a reaction runs only while
     * not redstone-halted.
     *
     * @param level the server level
     * @param pos   the block position
     * @param state the block state
     * @param be    the reactor block entity
     */
    public static void serverTick(Level level, BlockPos pos,
                                  BlockState state, ReactorBlockEntity be) {
        be.state.tickPushers();
        if (state.getValue(ReactorBlock.TRIGGERED)) {
            be.clearCrafting(level, pos, state);
            return;
        }
        be.tickReaction(level, pos, state);
    }

    /**
     * Client tick: integrates the wheel animation once per tick (20 Hz).
     * While crafting, accelerates toward MAX_WHEEL_SPEED; otherwise
     * decelerates to a clean stop at a cycle boundary.
     *
     * <p>Lives here, not in the BER's extractRenderState, because that
     * method runs at frame rate -- ticking it there caused the wheel to
     * advance ~3x too fast on a 60 fps client.
     *
     * @param level the client level
     * @param pos   the block position
     * @param state the block state (crafting flag drives accel vs decel)
     * @param be    the reactor block entity holding wheel speed/angle
     */
    public static void clientTick(Level level, BlockPos pos,
                                  BlockState state, ReactorBlockEntity be) {
        boolean crafting = state.getValue(ReactorBlock.CRAFTING);
        if (crafting) {
            be.wheelSpeed = Math.min(MAX_WHEEL_SPEED, be.wheelSpeed + WHEEL_ACCEL);
        } else {
            decelerateWheel(be);
        }
        be.wheelAngle = (be.wheelAngle + be.wheelSpeed) % WHEEL_CYCLE_PERIOD;
    }

    /**
     * Decelerates the wheel toward the next cycle-boundary rest. Below
     * the speed epsilon, hard-zeroes and snaps. Otherwise compares the
     * natural stop distance v^2/(2a) against the remaining angle to the
     * boundary; if we'd undershoot, scales decel up to land exactly.
     *
     * @param be the block entity
     */
    private static void decelerateWheel(ReactorBlockEntity be) {
        if (be.wheelSpeed < WHEEL_SPEED_EPSILON) {
            be.wheelSpeed = 0f;
            be.wheelAngle = Math.round(be.wheelAngle / WHEEL_CYCLE_PERIOD) * WHEEL_CYCLE_PERIOD;
            return;
        }
        float dRemaining = WHEEL_CYCLE_PERIOD - (be.wheelAngle % WHEEL_CYCLE_PERIOD);
        float naturalStopDist = (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * WHEEL_DECEL);
        float decel = (naturalStopDist < dRemaining)
                ? (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * dRemaining)
                : WHEEL_DECEL;
        be.wheelSpeed = Math.max(0f, be.wheelSpeed - decel);
    }

    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    @Override
    public int maxTopAttachments() {
        return CORNER_SLOTS.size();
    }

    @Override
    public int currentTopAttachments() {
        if (level == null) {
            return 0;
        }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity cbe)) {
            return 0;
        }
        int count = 0;
        for (int slot : INPUT_SLOTS) {
            if (!cbe.getCanister(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Set<Integer> allowedSlots() {
        return CORNER_SLOTS;
    }

    /**
     * Returns the output canister (may be EMPTY).
     *
     * @return the output canister item stack
     */
    public @NonNull ItemStack getOutputCanister() {
        return state.getCanister(OUTPUT_SLOT);
    }

    /**
     * Inserts a canister into the output slot through the shared slot lifecycle.
     *
     * @param stack the canister to insert
     * @return true if inserted
     */
    public boolean insertOutputCanister(ItemStack stack) {
        return state.insert(OUTPUT_SLOT, stack, false);
    }

    /**
     * Removes and returns the output canister through the shared slot lifecycle.
     *
     * @return the removed canister, or EMPTY
     */
    public @NonNull ItemStack removeOutputCanister() {
        return state.remove(OUTPUT_SLOT);
    }

    // --- IGasketHolder ---

    /**
     * The output canister carries a gasket on either face, as a canister block slot does.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return true;
    }

    /**
     * A hit in the front hollow addresses the output slot; anything else misses.
     */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        return ReactorBlock.isHollowClick(getBlockState(), worldPosition, hit) ? OUTPUT_SLOT : SLOT_MISS;
    }

    // --- Client-read geometry (decision hosts-answer-bounds-through-interfaces) ---

    private Direction facing() {
        return getBlockState().getValue(ReactorBlock.FACING);
    }

    /**
     * The front hollow is the reactor's cutaway.
     */
    @Override
    public boolean isCutawayHit(BlockHitResult hit) {
        return ReactorBlock.isHollowClick(getBlockState(), getBlockPos(), hit);
    }

    @Override
    public @Nullable AABB slotBounds(int index) {
        return index == OUTPUT_SLOT ? ReactorBlock.outputSlotShape(facing()).bounds() : null;
    }

    @Override
    public VoxelShape outlineShape(BlockHitResult hit) {
        return getBlockState().getShape(getLevel(), getBlockPos());
    }

    /**
     * The output canister, while the hit lands on its voxel, as the reactor's
     * empty-hand use checks.
     */
    @Override
    public @Nullable AABB pickupBounds(BlockHitResult hit) {
        boolean onCanister = ReactorBlock.hitOutputSlot(getBlockState(), getBlockPos(), hit);
        return onCanister && isSlotFilled(OUTPUT_SLOT) ? slotBounds(OUTPUT_SLOT) : null;
    }

    @Override
    public @Nullable AABB previewBounds(BlockHitResult hit, boolean sneaking) {
        return isCutawayHit(hit) && !isSlotFilled(OUTPUT_SLOT) ? slotBounds(OUTPUT_SLOT) : null;
    }

    /**
     * On the hollow's side, opposite {@link ReactorBlock#FACING}, pushed a pixel
     * proud of the frame so the billboard never dips into it.
     */
    @Override
    public @Nullable HudAnchor hudAnchor(BlockHitResult hit, HudViewer viewer) {
        if (!isSlotFilled(OUTPUT_SLOT) || !isCutawayHit(hit)) {
            return null;
        }
        Direction front = facing().getOpposite();
        return new HudAnchor(OUTPUT_SLOT, HUD_CENTER + front.getStepX() * HUD_FORWARD,
                HUD_CENTER + front.getStepZ() * HUD_FORWARD, HUD_LIFT, front);
    }

    /**
     * A standing click in the hollow reaches the output slot; a sneak places beside the reactor.
     */
    @Override
    public boolean takesCanisterAt(BlockHitResult hit, boolean sneaking) {
        return !sneaking && isCutawayHit(hit);
    }

    /**
     * A transmitter partner change re-stands the output pusher on the new link.
     */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER) {
            state.rebuildPusher(slot);
        }
    }

    /**
     * Resolves the best matching reaction and executes batches.
     *
     * @param level  the server level
     * @param pos    the block position
     * @param bState the block state
     */
    private void tickReaction(Level level, BlockPos pos, BlockState bState) {
        CanisterBlockEntity inputBe = getInputCanisterBe(level, pos);
        if (inputBe == null || !hasOutputCanister()) {
            clearCrafting(level, pos, bState);
            return;
        }
        if (!tryExecuteReaction(inputBe, level, pos, bState)) {
            clearCrafting(level, pos, bState);
        }
    }

    /**
     * Attempts to resolve and execute a reaction batch; returns false if any step fails.
     *
     * @param inputBe the input canister block entity above
     * @param level   the server level
     * @param pos     the reactor block position
     * @param bState  the current block state
     * @return true if a reaction was executed
     */
    private boolean tryExecuteReaction(
            CanisterBlockEntity inputBe, Level level, BlockPos pos, BlockState bState) {
        GooReaction reaction = resolveReaction(inputBe);
        if (reaction == null) {
            return false;
        }
        if (!outputCanAcceptProducts(reaction)) {
            return false;
        }
        int batches = computeBatches(inputBe, reaction);
        if (batches <= 0) {
            return false;
        }
        consumeInputs(inputBe, reaction.inputs(), batches);
        produceOutputs(reaction.outputs(), batches, reaction.rate());
        setChanged();
        setCrafting(level, pos, bState);
        return true;
    }

    /**
     * Returns true if the output slot has a canister to receive products.
     *
     * @return true if an output canister is present
     */
    private boolean hasOutputCanister() {
        return !getOutputCanister().isEmpty();
    }

    /**
     * Returns true if the output canister can accept all products of the
     * reaction. The canister must be empty or already contain the same
     * fluid as every output entry.
     *
     * @param reaction the matched reaction
     * @return true if the output canister is compatible
     */
    private boolean outputCanAcceptProducts(GooReaction reaction) {
        CanisterFluidContent content = CanisterItem.getFluidContent(getOutputCanister());
        if (content.isEmpty()) {
            return true;
        }
        for (GooReaction.FluidEntry entry : reaction.outputs()) {
            if (!content.resource().equals(entry.resource())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the canister block entity above (input source).
     *
     * @param level the level
     * @param pos   the reactor position
     * @return the canister BE, or null
     */
    private @Nullable CanisterBlockEntity getInputCanisterBe(
            Level level, BlockPos pos) {
        BlockEntity above = level.getBlockEntity(pos.above());
        return above instanceof CanisterBlockEntity cbe ? cbe : null;
    }

    /**
     * Finds the first matching reaction (superset-first).
     *
     * @param inputBe the input canister BE
     * @return the matched reaction, or null
     */
    private @Nullable GooReaction resolveReaction(CanisterBlockEntity inputBe) {
        for (GooReaction reaction : GooReactionLoader.getReactions()) {
            if (inputsSatisfy(inputBe, reaction)) {
                return reaction;
            }
        }
        return null;
    }

    /**
     * Checks whether all reaction inputs are present in the corner slots.
     *
     * @param inputBe  the input canister BE
     * @param reaction the candidate reaction
     * @return true if all inputs are satisfied
     */
    private boolean inputsSatisfy(CanisterBlockEntity inputBe,
                                  GooReaction reaction) {
        for (GooReaction.FluidEntry entry : reaction.inputs()) {
            if (getAvailableFluid(inputBe, entry.resource()) < entry.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sums fluid volume across the 4 corner input slots.
     *
     * @param inputBe the input canister BE
     * @param fluid   the fluid resource to sum
     * @return total mB available
     */
    private int getAvailableFluid(CanisterBlockEntity inputBe, FluidResource fluid) {
        int total = 0;
        for (int slot : INPUT_SLOTS) {
            ItemStack stack = inputBe.getCanister(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            if (content.resource().equals(fluid)) {
                total += content.amount();
            }
        }
        return total;
    }

    /**
     * Computes batches per tick: ceil(minBatches ^ 0.35).
     *
     * @param inputBe  the input canister BE
     * @param reaction the matched reaction
     * @return batches to execute
     */
    private int computeBatches(CanisterBlockEntity inputBe,
                               GooReaction reaction) {
        int minBatches = Integer.MAX_VALUE;
        for (GooReaction.FluidEntry entry : reaction.inputs()) {
            int available = getAvailableFluid(inputBe, entry.resource());
            int possible = available / entry.amount();
            minBatches = Math.min(minBatches, possible);
        }
        if (minBatches <= 0) {
            return 0;
        }
        return (int) Math.ceil(Math.pow(minBatches, THROUGHPUT_EXPONENT));
    }

    /**
     * Consumes input fluids from corner canister slots.
     *
     * @param inputBe the input canister BE
     * @param inputs  the reaction inputs
     * @param batches number of batches
     */
    private void consumeInputs(CanisterBlockEntity inputBe,
                               List<GooReaction.FluidEntry> inputs, int batches) {
        for (GooReaction.FluidEntry entry : inputs) {
            consumeFluid(inputBe, entry.resource(), entry.amount() * batches);
        }
    }

    /**
     * Drains a fluid across corner input slots via their fluid handlers.
     * Uses the slot handler's {@code extractFluid} convenience (which
     * opens, commits, and closes its own transaction) so this matches
     * every other extract call site in the canister machinery.
     *
     * @param inputBe the input canister BE
     * @param fluid   the fluid resource to drain
     * @param amount  total mB to drain
     */
    private void consumeFluid(CanisterBlockEntity inputBe,
                              FluidResource fluid, int amount) {
        int remaining = amount;
        for (int slot : INPUT_SLOTS) {
            if (remaining <= 0) {
                break;
            }
            CanisterSlotFluidHandler handler =
                    inputBe.containerState().getSlotFluidHandler(slot);
            if (handler == null) {
                continue;
            }
            int extracted = handler.extractFluid(fluid, remaining, false);
            remaining -= extracted;
        }
    }

    /**
     * Pushes output fluids into the output canister in the hollow.
     *
     * @param outputs the reaction outputs
     * @param batches number of batches
     * @param rate    output multiplier
     */
    private void produceOutputs(List<GooReaction.FluidEntry> outputs,
                                int batches, int rate) {
        CanisterSlotFluidHandler handler = state.getSlotFluidHandler(OUTPUT_SLOT);
        if (handler == null) {
            return;
        }
        for (GooReaction.FluidEntry entry : outputs) {
            int amount = entry.amount() * batches * rate;
            try (var tx = Transaction.openRoot()) {
                handler.insert(0, entry.resource(), amount, tx);
                tx.commit();
            }
        }
    }

    /**
     * Sets CRAFTING blockstate if not already set.
     *
     * @param level  the level
     * @param pos    the position
     * @param bState the current state
     */
    private void setCrafting(Level level, BlockPos pos, BlockState bState) {
        if (!bState.getValue(ReactorBlock.CRAFTING)) {
            level.setBlock(pos, bState.setValue(ReactorBlock.CRAFTING, true),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Clears CRAFTING blockstate if currently set.
     *
     * @param level  the level
     * @param pos    the position
     * @param bState the current state
     */
    private void clearCrafting(Level level, BlockPos pos, BlockState bState) {
        if (bState.getValue(ReactorBlock.CRAFTING)) {
            level.setBlock(pos, bState.setValue(ReactorBlock.CRAFTING, false),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected SlottedCanisterData heldSlots() {
        return state;
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        state.save(output, TAG_OUTPUT_CANISTER);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        state.load(input, TAG_OUTPUT_CANISTER);
    }
}
