package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.function.Predicate;

/**
 * Provides {@code /goo outsider reset}, which puts the calling player's own
 * Outsider room back as it is first laid, for testing the room from its start.
 * {@link GooCommand} registers it in a dev launch alone.
 * decision reset-command-registers-in-a-dev-launch-only-and-needs-op
 * decision reset-command-returns-my-room-to-nine-by-nine-and-clears-its-blocks
 */
public final class OutsiderCommand {

    /**
     * Subcommand name for the Outsider tree.
     */
    static final String CMD_OUTSIDER = "outsider";
    /**
     * Subcommand name for the room reset.
     */
    private static final String CMD_RESET = "reset";
    /**
     * The reset's report.
     */
    private static final String MSG_RESET = "Your Outsider room stands as first laid: 9 by 9, nothing built in it.";
    /**
     * The reset's refusal for a player who never entered a room.
     */
    private static final String MSG_NO_ROOM = "You hold no Outsider room to reset.";

    private OutsiderCommand() {
    }

    /**
     * Builds {@code /goo outsider} with {@code reset} beneath it, each needing op.
     *
     * @param requiresOp the operator check every /goo subcommand reads
     * @return /goo outsider
     */
    static ArgumentBuilder<CommandSourceStack, ?> subcommand(Predicate<CommandSourceStack> requiresOp) {
        return Commands.literal(CMD_OUTSIDER).requires(requiresOp)
                .then(Commands.literal(CMD_RESET).requires(requiresOp).executes(OutsiderCommand::reset));
    }

    /**
     * Resets the calling player's own room; a player holding none is told so and nothing changes.
     *
     * @param ctx the command context
     * @return 1 once the room is reset, 0 for a player with no room
     * @throws CommandSyntaxException when the source is not a player
     */
    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!OutsiderRooms.resetRoom(source.getPlayerOrException())) {
            source.sendFailure(Component.literal(MSG_NO_ROOM));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(MSG_RESET), true);
        return 1;
    }
}
