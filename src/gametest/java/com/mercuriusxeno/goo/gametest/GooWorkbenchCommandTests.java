package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.InGameWorkbench;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs /goo workbench on the test server and drives the page's server over loopback: the draft seeds from the
 * jar's values in the world's goo_values datapack, the inventory and the audit run in-process, and Promote
 * writes the datapack the game loads (decision goo-workbench-command-serves-the-page-from-the-game).
 */
@RegistersGameTests
public final class GooWorkbenchCommandTests {
    private static final String WORKBENCH_SERVES = "workbench_serves";
    private static final Pattern LOOPBACK_URL = Pattern.compile("http://127\\.0\\.0\\.1:\\d+");
    private static final String JAR_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String VALUES_FOLDER = "datapacks/goo_values/data/goo/goo_values";

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
     * Runs the command, then drives the page's server off the server thread, which keeps ticking to answer the
     * in-process dumps, and succeeds once every request has answered.
     *
     * @param helper the gametest helper
     */
    public static void servesTheWorldsValues(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        deleteTree(world.resolve("datapacks/goo_values"));
        deleteTree(world.resolve("goo_workbench"));
        List<String> replies = new ArrayList<>();
        server.getCommands().performPrefixedCommand(capturing(server, replies), "goo workbench");
        Matcher url = LOOPBACK_URL.matcher(String.join("\n", replies));
        helper.assertTrue(url.find(), "/goo workbench answered no loopback URL: " + replies);
        String base = url.group();
        CompletableFuture<String> drive = CompletableFuture.supplyAsync(() -> drivePage(base, world));
        helper.succeedWhen(() -> {
            helper.assertTrue(drive.isDone(), "the page's requests are still answering");
            String failure = drive.join();
            InGameWorkbench.close(server);
            helper.assertTrue(failure.isEmpty(), failure);
        });
    }

    /**
     * @return an empty string where every request answered as the decision says, or what went wrong
     */
    private static String drivePage(String base, Path world) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            JsonObject model = JsonParser.parseString(get(client, base + "/api/model")).getAsJsonObject();
            Path values = world.resolve(VALUES_FOLDER);
            Path live = Path.of(model.get("path").getAsString());
            if (!live.startsWith(values)) {
                return "the model's path " + live + " lies outside " + values;
            }
            if (!Arrays.equals(jarValues(), Files.readAllBytes(values.resolve("base_values.draft.json")))) {
                return "the draft does not equal the jar's base_values.json";
            }
            JsonObject inventory = JsonParser.parseString(get(client, base + "/api/inventory")).getAsJsonObject();
            if (inventory.getAsJsonArray("items").isEmpty()) {
                return "the inventory answered no items";
            }
            String run = post(client, base + "/api/dumps/run", model.toString());
            if (!run.contains("\"exitCode\":0")) {
                return "the dump run did not finish clean: " + run;
            }
            if (!Files.isRegularFile(world.resolve("goo_workbench/goo_audit.json"))) {
                return "the dump run wrote no goo_audit.json under the world folder";
            }
            if (ProcessHandle.current().descendants().anyMatch(GooWorkbenchCommandTests::isGradle)) {
                return "the in-game run spawned a gradle process";
            }
            post(client, base + "/api/promote", "{\"acknowledged\": true}");
            if (!Files.isRegularFile(values.resolve("base_values.json"))
                    || !Files.isRegularFile(world.resolve("datapacks/goo_values/pack.mcmeta"))) {
                return "Promote left no base_values.json and pack.mcmeta in the datapack";
            }
            return "";
        } catch (IOException | RuntimeException e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    private static boolean isGradle(ProcessHandle process) {
        return process.info().commandLine().map(line -> line.contains("gradle")).orElse(false);
    }

    private static String get(HttpClient client, String url) throws IOException, InterruptedException {
        return answered(client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()), url);
    }

    private static String post(HttpClient client, String url, String body) throws IOException, InterruptedException {
        return answered(client.send(HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()), url);
    }

    private static String answered(HttpResponse<String> response, String url) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException(url + " answered " + response.statusCode() + ": " + response.body());
        }
        return response.body();
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
