package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Server side of a plexer menu pick: the picked item becomes the plexer's target through the one
 * targeting rule, so the known-item gate and the refusal fizzle stay as they are, and Cancel clears
 * the target; a pick naming a plexer out of the player's reach, or a position holding none, changes
 * nothing.
 * decision right-click-on-the-plexer-opens-the-known-items-menu
 * decision cancel-cell-clears-the-plexer-target
 */
public final class PlexerTargetPickHandler {

    /** The block reach a pick allows beyond the player's own, matching a block use. */
    private static final double REACH_SLACK = 1.0;

    private PlexerTargetPickHandler() {}

    /**
     * Applies the pick on the server thread.
     *
     * @param payload the pick
     * @param context the network context
     */
    public static void handle(PlexerTargetPickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                pick(player, payload.plexer(), payload.item());
            }
        });
    }

    /**
     * Sets the plexer's target to the picked item, or clears it for Cancel, when the pick reaches a plexer.
     *
     * @param player the picking player
     * @param pos    the position of the plexer the menu opened on
     * @param item   the id of the item picked, empty for Cancel
     */
    public static void pick(ServerPlayer player, BlockPos pos, Optional<Identifier> item) {
        PlexerBlockEntity plexer = reachedPlexer(player.isWithinBlockInteractionRange(pos, REACH_SLACK),
                player.level().getBlockEntity(pos));
        if (plexer == null) {
            return;
        }
        if (item.isEmpty()) {
            CutawayInteractionHelper.clearTargetItem(plexer);
        } else {
            CutawayInteractionHelper.applyTargetItem(plexer, player,
                    new ItemStack(BuiltInRegistries.ITEM.getValue(item.get())));
        }
    }

    /**
     * The plexer a pick acts on.
     *
     * @param withinReach whether the position lies within the player's block reach
     * @param atPosition  the block entity standing at the position, if any
     * @return the plexer, or null when the pick changes nothing
     */
    static @Nullable PlexerBlockEntity reachedPlexer(boolean withinReach, @Nullable BlockEntity atPosition) {
        return withinReach && atPosition instanceof PlexerBlockEntity plexer ? plexer : null;
    }
}
