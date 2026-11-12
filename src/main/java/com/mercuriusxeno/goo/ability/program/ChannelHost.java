package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.petrify.Statues;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * The gradual break the channeling player's current hold progresses its
     * blocks through, fresh for each hold.
     * decision gradual-breaks-share-one-break-texture-piece
     *
     * @return the hold's gradual break
     */
    GradualBreak gradualBreak();

    /**
     * Toggles every lever, button, door, trapdoor or fence gate standing in
     * the cells as a hand would, each once however many of its cells the
     * list holds, with no memory of earlier toggles
     * (decision pulser-toggles-rapidly-while-held).
     *
     * @param cells the blocks to toggle the devices of
     */
    void toggleEachDevice(List<BlockPos> cells);

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

    /**
     * The channeling player's horizontal speed, read from the movement their
     * client reports, since the server leaves a player's delta movement at rest.
     * glove-stream-motes-carry-the-players-travel-velocity
     *
     * @return the speed, in blocks a tick
     */
    double channelerTravelSpeed();

    /**
     * The speed the channeling player's movement speed walks them at on
     * ordinary ground, unadjusted by any slick.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @return the speed, in blocks a tick
     */
    double channelerUnadjustedSpeed();

    /**
     * Whether the channeling player stands on Kinetic's slick.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @return true where the slick film fills the cell their feet stand in
     */
    boolean channelerStandsOnSlick();
}
