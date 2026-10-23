package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.workbench.InGameWorkbench;
import com.mercuriusxeno.goo.workbench.ValuesWorkbenchServer;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * /goo workbench: serves the values workbench page on loopback from the running game, over the world's goo_values
 * datapack, and a second /goo workbench or /goo workbench stop ends it; the shipped jar runs it, so it stands
 * outside the dev gate (decision goo-workbench-command-serves-the-page-from-the-game).
 */
final class WorkbenchCommand {
    static final String CMD_WORKBENCH = "workbench";
    private static final String CMD_STOP = "stop";
    private static final int ANY_FREE_PORT = 0;
    private static final String JAR_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String PACK_MCMETA = "pack.mcmeta";
    private static final String PACK_MCMETA_TEMPLATE = """
            {
              "pack": {
                "description": "Goo values the values workbench promotes, loaded over the jar's",
                "min_format": %d,
                "max_format": %d
              }
            }
            """;
    private static final String MSG_SERVING = "Values workbench at ";
    private static final String MSG_PROMOTES = ". Promote writes ";
    private static final String MSG_RELOAD = "; /reload then loads it. /goo workbench stop ends the page.";
    private static final String MSG_STOPPED = "The values workbench page has stopped; the game runs on.";
    private static final String MSG_NOT_RUNNING = "No values workbench page is running.";
    private static final String MSG_FAILED = "The values workbench could not start: ";
    private static final String NO_JAR_VALUES = "The jar holds no ";

    private WorkbenchCommand() {
    }

    /**
     * @param requiresOp the operator check every /goo subcommand reads
     * @return /goo workbench, with stop beneath it
     */
    static ArgumentBuilder<CommandSourceStack, ?> subcommand(Predicate<CommandSourceStack> requiresOp) {
        return Commands.literal(CMD_WORKBENCH).requires(requiresOp).executes(WorkbenchCommand::toggle)
                .then(Commands.literal(CMD_STOP).executes(WorkbenchCommand::stop));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (InGameWorkbench.close(server)) {
            ctx.getSource().sendSuccess(() -> Component.literal(MSG_STOPPED), false);
            return 1;
        }
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        WorkbenchPaths paths = WorkbenchPaths.inWorld(world);
        ValuesWorkbenchServer page;
        try {
            writePackMeta(WorkbenchPaths.packRoot(world));
            page = InGameWorkbench.open(server, () -> ValuesWorkbenchServer.start(paths, WorkbenchCommand::jarValues,
                    InGameDumps.inventory(server, paths), InGameDumps.dumps(server, paths), ANY_FREE_PORT));
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal(MSG_FAILED + e.getMessage()));
            return 0;
        }
        String url = page.url();
        ctx.getSource().sendSuccess(() -> Component.literal(MSG_SERVING).append(link(url))
                .append(Component.literal(MSG_PROMOTES + paths.baseValues() + MSG_RELOAD)), false);
        openBrowserForTheOwner(ctx.getSource(), server, url);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        boolean stopped = InGameWorkbench.close(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(stopped ? MSG_STOPPED : MSG_NOT_RUNNING), false);
        return stopped ? 1 : 0;
    }

    private static Component link(String url) {
        return Component.literal(url).withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withUnderlined(true).withColor(ChatFormatting.AQUA));
    }

    /**
     * Opens the browser only for the player hosting a singleplayer world, whose machine serves the page; a
     * dedicated server's console or a remote player gets the link alone.
     *
     * @param source the command's source
     * @param server the running server
     * @param url    the page's address
     */
    private static void openBrowserForTheOwner(CommandSourceStack source, MinecraftServer server, String url) {
        ServerPlayer player = source.getPlayer();
        if (player != null && server.isSingleplayerOwner(player.nameAndId())) {
            Util.getPlatform().openUri(URI.create(url));
        }
    }

    private static void writePackMeta(Path packRoot) throws IOException {
        Path mcmeta = packRoot.resolve(PACK_MCMETA);
        if (Files.isRegularFile(mcmeta)) {
            return;
        }
        Files.createDirectories(packRoot);
        int format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
        Files.writeString(mcmeta, PACK_MCMETA_TEMPLATE.formatted(format, format), StandardCharsets.UTF_8);
    }

    /**
     * @return the jar's base_values.json, the shipped values a pack dev's first draft starts from
     */
    private static byte[] jarValues() throws IOException {
        try (InputStream in = WorkbenchCommand.class.getResourceAsStream(JAR_VALUES)) {
            if (in == null) {
                throw new IOException(NO_JAR_VALUES + JAR_VALUES);
            }
            return in.readAllBytes();
        }
    }
}
