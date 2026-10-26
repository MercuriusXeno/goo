package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.command.ValuesManagers;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mercuriusxeno.goo.workbench.WorkbenchReply;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Runs /goo workbench on the test server and drives the workbench operations it stands, as the values manager's
 * requests do: the draft seeds from the jar's values in the world's goo_values datapack, the inventory and the audit
 * run in-process, and Promote writes the datapack the game loads (decision
 * goo-workbench-opens-the-in-game-manager-screen).
 */
@RegistersGameTests
public final class GooWorkbenchCommandTests {
    private static final String WORKBENCH_SERVES = "workbench_serves";
    private static final String JAR_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String VALUES_FOLDER = "datapacks/goo_values/data/goo/goo_values";
    private static final String NEEDS_CLIENT = "opens from an opped client";

    private GooWorkbenchCommandTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, WORKBENCH_SERVES, GooWorkbenchCommandTests::servesTheWorldsValues);
    }

    /**
     * Runs the command from the console, which is told the manager opens from a client, then drives the operations
     * off the server thread, which keeps ticking to answer the in-process dumps, and succeeds once every operation
     * has answered.
     *
     * @param helper the gametest helper
     */
    public static void servesTheWorldsValues(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        ValuesManagers.close(server);
        deleteTree(world.resolve("datapacks/goo_values"));
        deleteTree(world.resolve("goo_workbench"));
        List<String> replies = new ArrayList<>();
        server.getCommands().performPrefixedCommand(capturing(server, replies), "goo workbench");
        helper.assertTrue(String.join(" ", replies).contains(NEEDS_CLIENT),
                "/goo workbench told the console nothing of the client: " + replies);
        WorkbenchOperations operations;
        try {
            operations = ValuesManagers.operationsFor(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CompletableFuture<String> drive = CompletableFuture.supplyAsync(() -> driveOperations(operations, world));
        helper.succeedWhen(() -> {
            helper.assertTrue(drive.isDone(), "the operations are still answering");
            String failure = drive.join();
            ValuesManagers.close(server);
            helper.assertTrue(failure.isEmpty(), failure);
        });
    }

    /**
     * @return an empty string where every operation answered as the decision says, or what went wrong
     */
    private static String driveOperations(WorkbenchOperations operations, Path world) {
        try {
            JsonObject model = JsonParser.parseString(answered(operations, WorkbenchOperations.MODEL, ""))
                    .getAsJsonObject();
            Path values = world.resolve(VALUES_FOLDER);
            Path live = Path.of(model.get("path").getAsString());
            if (!live.startsWith(values)) {
                return "the model's path " + live + " lies outside " + values;
            }
            if (!Arrays.equals(jarValues(), Files.readAllBytes(values.resolve("base_values.draft.json")))) {
                return "the draft does not equal the jar's base_values.json";
            }
            JsonObject inventory = JsonParser.parseString(answered(operations, WorkbenchOperations.INVENTORY, ""))
                    .getAsJsonObject();
            if (inventory.getAsJsonArray("items").isEmpty()) {
                return "the inventory answered no items";
            }
            JsonObject run = operations.runDumps(model.toString(), line -> { });
            if (run.get("exitCode").getAsInt() != 0) {
                return "the dump run did not finish clean: " + run;
            }
            if (!Files.isRegularFile(world.resolve("goo_workbench/goo_audit.json"))) {
                return "the dump run wrote no goo_audit.json under the world folder";
            }
            if (ProcessHandle.current().descendants().anyMatch(GooWorkbenchCommandTests::isGradle)) {
                return "the in-game run spawned a gradle process";
            }
            answered(operations, WorkbenchOperations.PROMOTE, "{}");
            if (!Files.isRegularFile(values.resolve("base_values.json"))
                    || !Files.isRegularFile(world.resolve("datapacks/goo_values/pack.mcmeta"))) {
                return "Promote left no base_values.json and pack.mcmeta in the datapack";
            }
            return "";
        } catch (IOException | RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static String answered(WorkbenchOperations operations, String operation, String posted)
            throws IOException {
        WorkbenchReply reply = operations.answer(operation, posted);
        if (reply.status() != WorkbenchReply.OK) {
            throw new IOException(operation + " answered " + reply.status() + ": " + reply.text());
        }
        return reply.text();
    }

    private static boolean isGradle(ProcessHandle process) {
        return process.info().commandLine().map(line -> line.contains("gradle")).orElse(false);
    }

    private static byte[] jarValues() throws IOException {
        try (InputStream in = GooWorkbenchCommandTests.class.getResourceAsStream(JAR_VALUES)) {
            return Objects.requireNonNull(in, JAR_VALUES).readAllBytes();
        }
    }

    private static CommandSourceStack capturing(MinecraftServer server, List<String> replies) {
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return server.createCommandSourceStack().withSource(capture);
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
