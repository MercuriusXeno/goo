package com.mercuriusxeno.goo.ability.program;

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
     * Toggles the lever, button, door, trapdoor or fence gate at a block as a
     * hand would, the first time the hold reaches it; a later reach in the
     * same hold, or a block holding no such device, toggles nothing
     * (decision signal-wave-toggles-each-device-once).
     *
     * @param pos the block
     */
    void toggleOnceThisHold(BlockPos pos);

    /**
     * Toggles every lever, button, door, trapdoor or fence gate standing in
     * the cells as a hand would, each once however many of its cells the
     * list holds, with no memory of earlier toggles
     * (decision pulser-toggles-rapidly-while-held).
     *
     * @param cells the blocks to toggle the devices of
     */
    void toggleEachDevice(List<BlockPos> cells);
}
