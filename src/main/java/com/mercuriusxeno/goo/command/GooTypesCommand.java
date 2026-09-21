package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides {@code /goo types}, which lists every entry of the goo type
 * registry in force, bundled and datapack-added alike, so an operator can
 * read which types a world's datapacks stood.
 */
public final class GooTypesCommand {

    /**
     * Subcommand name for the type listing.
     */
    private static final String CMD_TYPES = "types";
    /**
     * Prefix of the listing message, followed by the entry count.
     */
    private static final String MSG_TYPES_PREFIX = "Goo types (";
    /**
     * Separator between the entry count and the ids.
     */
    private static final String MSG_TYPES_MID = "): ";
    /**
     * Separator between ids.
     */
    private static final String MSG_COMMA = ", ";

    private GooTypesCommand() {
    }

    /**
     * Builds the {@code /goo types} subcommand.
     *
     * @return the types argument builder
     */
    public static ArgumentBuilder<CommandSourceStack, ?> subcommand() {
        return Commands.literal(CMD_TYPES).executes(GooTypesCommand::listTypes);
    }

    /**
     * Sends the ids of every registry entry to the command source.
     *
     * @param ctx the command context
     * @return the number of entries listed
     */
    private static int listTypes(CommandContext<CommandSourceStack> ctx) {
        HolderLookup.RegistryLookup<GooTypeDefinition> registry =
                ctx.getSource().registryAccess().lookupOrThrow(GooTypes.REGISTRY);
        List<String> ids = sortedIds(registry.listElementIds());
        ctx.getSource().sendSuccess(() -> Component.literal(formatListing(ids)), false);
        return ids.size();
    }

    /**
     * Orders the keys by their full id, so the listing reads the same on every run.
     *
     * @param keys the element keys of the registry
     * @return the ids as strings, ascending
     */
    public static List<String> sortedIds(Stream<ResourceKey<GooTypeDefinition>> keys) {
        return keys.map(key -> key.identifier().toString())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Composes the listing line: the count, then the ids comma-separated.
     *
     * @param ids the sorted ids
     * @return the message text
     */
    static String formatListing(List<String> ids) {
        return MSG_TYPES_PREFIX + ids.size() + MSG_TYPES_MID + String.join(MSG_COMMA, ids);
    }
}
