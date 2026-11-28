package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.petrify.Statues;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A host a held channel runs on: it carries the hold's aim for this tick
 * and breaks blocks as the channeling player would.
 * decision flatten-disc-cursor-breaks-above-the-plane
 * decision bore-vortex-with-a-worldspace-shake
 */
public interface ChannelHost extends BlockBreakHost {

    /**
     * A sprayed program's top-level steps split by the pass they run in:
     * those needing the channel run once a tick over the cone's blocks, and
     * the rest run on each living thing the spray reaches, a stream's cone
     * and a spored corpse's burst alike.
     * mycosis-grows-and-reaps-nether-wart
     *
     * @param behaviors the program's top-level steps
     * @param blockPass true for the block pass's steps, false for the entity pass's
     * @return the steps of that pass, in program order
     */
    static List<Step> passSteps(List<Step> behaviors, boolean blockPass) {
        return behaviors.stream()
                .filter(step -> step.requires().contains(HostCapability.CHANNEL) == blockPass)
                .toList();
    }

    /**
     * The player whose goo the host places, whom a goo cell it feeds credits.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @return the player's id, or null where no player places it
     */
    default @Nullable UUID placingPlayer() {
        return null;
    }

    /**
     * The hold's aim this tick.
     *
     * @return the aim, empty when the host runs outside a held channel
     */
    Optional<ChannelAim> channelAim();

    /**
     * The channeling player's eye, where the aim line starts.
     *
     * @return the eye position
     */
    Vec3 eye();

    /**
     * Whether the channeling player's glove is in their right hand, the side
     * of the sight line their lifted blob stands on.
     * decision bore-cross-section-is-where-the-angled-drill-meets-the-look-plane
     *
     * @return true for a glove in the right hand
     */
    default boolean gloveOnTheRight() {
        return true;
    }

    /**
     * Whether the channeling player can reach a block to break it.
     *
     * @param pos the block
     * @return true within the player's block interaction range
     */
    boolean reaches(BlockPos pos);

    /**
     * Hands the body a host bound to each living entity, the channeling
     * player aside, whose body overlaps any of the cells and that every
     * filter keeps; Bore strikes what stands in its tunnel this way
     * (decision bore-vortex-with-a-worldspace-shake).
     *
     * @param cells   the block cells to scan
     * @param filters the filters an entity must pass
     * @param body    what to run on the host bound to each entity
     */
    void forEachLivingIn(List<BlockPos> cells, Set<EntityFilter> filters, Consumer<TargetHost> body);

    /**
     * The marks the channeling player's current hold has left on the blocks
     * it reached (decision decay-gnats-degrade-each-block-once).
     *
     * @return the hold's marks
     */
    HoldMarks holdMarks();

    /**
     * What each thing the hold lays pays as it lands.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @return the hold's placement price, free for a hold paying by the tick
     */
    default PlacementPrice placementPrice() {
        return PlacementPrice.FREE;
    }

    /**
     * The gradual break the channeling player's current hold progresses its
     * blocks through, fresh for each hold.
     * decision gradual-breaks-share-one-break-texture-piece
     *
     * @return the hold's gradual break
     */
    GradualBreak gradualBreak();

    /**
     * Counts one tick Vacuum's cone reaches a goo cell toward its next layer,
     * in the server's count store, so the count outlives the hold.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param cell        the cell
     * @param memoryTicks the ticks the count is kept after this one
     * @return the cell's count after this tick
     */
    default int countVacuumTick(BlockPos cell, int memoryTicks) {
        return GooServerState.of(level().getServer()).vacuumCounts().countTick(level().dimension(), cell,
                level().getServer().getTickCount(), memoryTicks);
    }

    /**
     * Drops a goo cell's Vacuum count, as its layer taken does.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param cell the cell
     */
    default void forgetVacuumCount(BlockPos cell) {
        GooServerState.of(level().getServer()).vacuumCounts().forget(level().dimension(), cell);
    }

    /**
     * Toggles every lever, button, door, trapdoor or fence gate standing in
     * the cells as a hand would, and gives every repeater and redstone
     * receiver there one Zap pulse, each once however many of its cells the
     * list holds, with no memory of earlier toggles.
     * pulser-toggles-rapidly-while-held
     * tick-pulses-anything-a-signal-runs-while-held
     *
     * @param cells the blocks to toggle the devices of
     */
    void toggleEachDevice(List<BlockPos> cells);

    /**
     * Carries the Mortar over Level's disc with the cursor, paving the gaps
     * under the disc with stone as it goes.
     * level-dragged-under-rock-goo-carries-it-and-deposits-stone-under-the-disc
     *
     * @param aim   the hold's aim this tick
     * @param aimed the block the cursor rests on
     */
    default void carryMortarUnderDisc(ChannelAim aim, BlockPos aimed) {
        MortarPaving.carryUnderDisc(this, aim, aimed);
    }

    /**
     * Whether a statue stands at a position.
     * decision heal-wakes-a-statue-at-a-nearly-full-gauge
     *
     * @param pos the position
     * @return true where the block standing there is a statue
     */
    default boolean statueAt(BlockPos pos) {
        return level().getBlockState(pos).is(GooBlocks.STATUE.get());
    }

    /**
     * Wakes the statue at a position back into its mob.
     * decision heal-wakes-a-statue-at-a-nearly-full-gauge
     *
     * @param pos the statue
     */
    default void wakeStatue(BlockPos pos) {
        Statues.wake(level(), pos);
    }
}

