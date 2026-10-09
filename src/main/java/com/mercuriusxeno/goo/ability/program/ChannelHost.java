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
}
