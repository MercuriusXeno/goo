package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.lab.LabLayout;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Provides {@code /goo lab}: {@code build} builds the Goo Lab from
 * {@link LabLayout} at the lab origin and hands its invoker the kit
 * (decision lab-built-from-code); {@code kit} hands the kit alone (decision
 * lab-holds-bays-supply-pens-kit). {@link GooCommand} gates the tree to operators.
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
     * Subcommand name for the kit.
     */
    private static final String CMD_KIT = "kit";
    /**
     * Prefix of the build report, followed by the placement count.
     */
    private static final String MSG_BUILT = "Goo Lab built: ";
    /**
     * Suffix of the build report.
     */
    private static final String MSG_PLACEMENTS = " placements";
    /**
     * Prefix of the kit report, followed by the stack count.
     */
    private static final String MSG_KIT = "Goo Lab kit: ";
    /**
     * Suffix of the kit report.
     */
    private static final String MSG_STACKS = " stacks";

    private LabCommand() {
    }

    /**
     * Builds the children of {@code /goo lab}.
     *
     * @param lab the lab literal to attach the children to
     * @return the lab literal with its children
     */
    static ArgumentBuilder<CommandSourceStack, ?> children(ArgumentBuilder<CommandSourceStack, ?> lab) {
        return lab.then(Commands.literal(CMD_BUILD).executes(LabCommand::build))
                .then(Commands.literal(CMD_KIT).executes(LabCommand::kit));
    }

    /**
     * Builds the lab at the lab origin for the command's source.
     *
     * @param ctx the command context
     * @return the number of placements set
     * @throws CommandSyntaxException when a planned block state does not parse
     */
    private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return buildAt(ctx.getSource(), LabBuilder.worldOrigin());
    }

    /**
     * Builds the lab plan into the source's level at an origin, then hands the
     * source's player the kit. The build handler runs this; a gametest runs it at its own origin.
     *
     * @param source the command source
     * @param origin the world position of the plan's zero offset
     * @return the number of placements set
     * @throws CommandSyntaxException when a planned block state does not parse
     */
    public static int buildAt(CommandSourceStack source, BlockPos origin) throws CommandSyntaxException {
        int placed = LabBuilder.build(source.getLevel(), origin, LabBuilder.planFor(source.getLevel()));
        source.sendSuccess(() -> Component.literal(MSG_BUILT + placed + MSG_PLACEMENTS), true);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            int stacks = LabKit.give(player, source.getLevel());
            source.sendSuccess(() -> Component.literal(MSG_KIT + stacks + MSG_STACKS), false);
        }
        return placed;
    }

    /**
     * Hands the invoking player the kit.
     *
     * @param ctx the command context
     * @return the number of kit stacks handed out
     * @throws CommandSyntaxException when the source is not a player
     */
    private static int kit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int stacks = LabKit.give(source.getPlayerOrException(), source.getLevel());
        source.sendSuccess(() -> Component.literal(MSG_KIT + stacks + MSG_STACKS), false);
        return stacks;
    }
}
