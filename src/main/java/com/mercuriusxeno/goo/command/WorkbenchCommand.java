package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.network.OpenValuesManagerPayload;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * /goo workbench: stands the world's workbench operations over its goo_values datapack and opens the values manager
 * on the calling player's client; the jar starts no HTTP server and opens no browser
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class WorkbenchCommand {
    static final String CMD_WORKBENCH = "workbench";
    static final String MSG_OPENING = "Opening the values manager.";
    static final String MSG_NEEDS_CLIENT = "The values manager opens from an opped client: run /goo workbench in game.";
    private static final String MSG_FAILED = "The values manager could not stand the world's values: ";

    private WorkbenchCommand() {
    }

    /**
     * @param requiresOp the operator check every /goo subcommand reads
     * @return /goo workbench
     */
    static ArgumentBuilder<CommandSourceStack, ?> subcommand(Predicate<CommandSourceStack> requiresOp) {
        return Commands.literal(CMD_WORKBENCH).requires(requiresOp).executes(WorkbenchCommand::open);
    }

    private static int open(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            ValuesManagers.operationsFor(source.getServer());
        } catch (IOException e) {
            source.sendFailure(Component.literal(MSG_FAILED + e.getMessage()));
            return 0;
        }
        return openFor(source.getPlayer(), player -> PacketDistributor.sendToPlayer(player,
                new OpenValuesManagerPayload()), message -> source.sendSuccess(() -> Component.literal(message),
                false));
    }

    /**
     * Opens the manager for a player, or tells a source with no player where the manager opens.
     *
     * @param player   the calling player, null for the console or a command block
     * @param sendOpen sends the open payload to the player
     * @param tell     tells the source a message
     * @param <P>      the player's type
     * @return 1 where the manager opens, 0 where no player called
     */
    static <P> int openFor(@Nullable P player, Consumer<P> sendOpen, Consumer<String> tell) {
        if (player == null) {
            tell.accept(MSG_NEEDS_CLIENT);
            return 0;
        }
        sendOpen.accept(player);
        tell.accept(MSG_OPENING);
        return 1;
    }
}
