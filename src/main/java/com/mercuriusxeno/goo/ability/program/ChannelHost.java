package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * A host a held channel runs on: it carries the hold's aim for this tick
 * and breaks blocks as the channeling player would.
 * decision flatten-disc-cursor-breaks-above-the-plane
 */
public interface ChannelHost extends StepHost {

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
     * Whether the block at a position belongs to a block tag.
     *
     * @param pos the position
     * @param tag the block tag
     * @return true where the block standing there is in the tag
     */
    boolean blockIn(BlockPos pos, TagKey<Block> tag);

    /**
     * Whether the channeling player can reach a block to break it.
     *
     * @param pos the block
     * @return true within the player's block interaction range
     */
    boolean reaches(BlockPos pos);

    /**
     * Breaks a block as the channeling player, dropping its loot and playing
     * its break particles.
     *
     * @param pos the block
     */
    void breakBlock(BlockPos pos);
}
