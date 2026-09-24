package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabLayout;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Provides {@code /goo lab}, which builds the Goo Lab from {@link LabLayout}
 * at the lab origin (decision lab-built-from-code). {@link GooCommand} gates
 * the tree to operators.
 */
public final class LabCommand {

    /**
     * Subcommand name for the lab tree.
     */
    static final String CMD_LAB = "lab";
    /**
     * Subcommand name for the build.
     */
    private static final String CMD_BUILD = "build";
    /**
     * Prefix of the build report, followed by the placement count.
     */
    private static final String MSG_BUILT = "Goo Lab built: ";
    /**
     * Suffix of the build report.
     */
    private static final String MSG_PLACEMENTS = " placements";

    private LabCommand() {
    }

    /**
     * Builds the children of {@code /goo lab}.
     *
     * @param lab the lab literal to attach the children to
     * @return the lab literal with its children
     */
    static ArgumentBuilder<CommandSourceStack, ?> children(ArgumentBuilder<CommandSourceStack, ?> lab) {
        return lab.then(Commands.literal(CMD_BUILD).executes(LabCommand::build));
    }

    /**
     * Builds the lab plan into the source's level at the lab origin.
     *
     * @param ctx the command context
     * @return the number of placements set
     * @throws CommandSyntaxException when a planned block state does not parse
     */
    private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int placed = LabBuilder.build(source.getLevel(), LabBuilder.worldOrigin(), LabLayout.plan());
        source.sendSuccess(() -> Component.literal(MSG_BUILT + placed + MSG_PLACEMENTS), true);
        return placed;
    }
}
