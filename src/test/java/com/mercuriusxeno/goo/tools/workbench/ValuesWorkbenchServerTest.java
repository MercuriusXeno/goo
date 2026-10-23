package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooConstantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the workbench's handlers on a loopback server over temp copies of the files: the model, the
 * save, the inventory and an item's image (decision workbench-authors-base-values-whole). The criterion
 * names a real server on an ephemeral port, so this test binds one and writes under a temp folder.
 */
class ValuesWorkbenchServerTest {

    private static final List<String> SECTIONS = List.of("_constants", "_groups", "_restricted", "_conversions",
            "_post_conversions");
    private static final byte[] NUGGET_PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final String INVENTORY = "{\"items\":[{\"id\":\"minecraft:iron_nugget\",\"name\":\"Iron Nugget\"}],"
            + "\"blocks\":[],\"item_tags\":[],\"block_tags\":[],\"goo_types\":[],\"recipes\":[]}";

    @TempDir
    Path temp;

    private ValuesWorkbenchServer server;
    private WorkbenchPaths paths;
    private final AtomicInteger dumpsRun = new AtomicInteger();
    private final HttpClient client = HttpClient.newHttpClient();

    private static final String CANNED_AUDIT = """
            Goo Audit Report
            ================

            EXPRESSION WARNINGS (0 issues in base_values.json expressions)
            --------------------------------------------------
              All expressions are valid.

            LEAST VALUE PER TYPE (1 types)
            --------------------------------------------------
              metal: 1 at iron_nugget

            NO VALUE (0 registered items have no goo value)
            --------------------------------------------------
              All registered items have goo values.
            """;
    private static final String CANNED_CLIQUES = """
            CLIQUES AND LOSSY RECIPES

            A clique is a group of items linked by recipes.

            METAL
            =====

            Clique: iron_nugget (3 items, 2 recipes)
            """;
    private final List<String> savedWhenRunStarted = new java.util.ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        paths = new WorkbenchPaths(temp.resolve("base_values.json"), temp.resolve("goo_inventory.json"),
                temp.resolve("goo_inventory"), temp.resolve("goo_audit.txt"), temp.resolve("goo_cliques.txt"),
                temp.resolve("goo_audit.json"));
        Files.write(paths.baseValues(), BaseValuesModelTest.shippedBytes());
        server = ValuesWorkbenchServer.start(paths, () -> {
            dumpsRun.incrementAndGet();
            Files.writeString(paths.inventoryFile(), INVENTORY);
        }, progress -> {
            savedWhenRunStarted.add(Files.readString(paths.baseValues()));
            progress.accept("booting the test server");
            Files.writeString(paths.auditDump(), CANNED_AUDIT);
            Files.writeString(paths.cliqueDump(), CANNED_CLIQUES);
            progress.accept("goo:values_dumps passed");
            return 0;
        }, 0);
    }

    private static String auditJson(int sections) {
        JsonArray array = new JsonArray();
        for (int i = 1; i <= sections; i++) {
            JsonObject section = new JsonObject();
            section.addProperty("section", "RULE " + i);
            section.addProperty("header", "RULE " + i + " (1 lines)");
            JsonArray lines = new JsonArray();
            lines.add("  line of rule " + i);
            section.add("lines", lines);
            JsonArray flags = new JsonArray();
            if (i == 2) {
                flags.add("rule 2 flags this");
            }
            section.add("flags", flags);
            array.add(section);
        }
        JsonObject root = new JsonObject();
        root.add("sections", array);
        return root.toString();
    }

    /**
     * The audit endpoint answers the sections goo_audit.json carries, in its order with each one's flags,
     * and reads the file afresh on every request, so a rule added later shows with no restart.
     */
    @Test
    void auditAnswersTheJsonSections() throws IOException, InterruptedException {
        Files.writeString(paths.auditJson(), auditJson(3));
        JsonArray sections = JsonParser.parseString(text(get("/api/audit"))).getAsJsonObject()
                .getAsJsonArray("sections");
        assertEquals(List.of("RULE 1", "RULE 2", "RULE 3"), sections.asList().stream()
                .map(section -> section.getAsJsonObject().get("section").getAsString()).toList());
        assertEquals("rule 2 flags this", sections.get(1).getAsJsonObject().getAsJsonArray("flags").get(0)
                .getAsString());
        Files.writeString(paths.auditJson(), auditJson(4));
        assertEquals(4, JsonParser.parseString(text(get("/api/audit"))).getAsJsonObject()
                .getAsJsonArray("sections").size());
    }

    /**
     * The run saves the posted model first, streams the runner's lines, then answers both dumps section by
     * section, the least value and the clique tables first, with the run's duration.
     */
    @Test
    void runSavesStreamsAndAnswersTheDumps() throws IOException, InterruptedException {
        JsonObject model = JsonParser.parseString(text(get("/api/model"))).getAsJsonObject();
        model.getAsJsonArray("members").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .addProperty("text", "191");
        HttpResponse<java.util.stream.Stream<String>> response = client.send(HttpRequest.newBuilder(
                        URI.create(server.url() + "/api/dumps/run")).POST(HttpRequest.BodyPublishers.ofString(
                        model.toString())).build(), HttpResponse.BodyHandlers.ofLines());
        assertEquals(200, response.statusCode());
        List<JsonObject> events = response.body().map(line -> JsonParser.parseString(line).getAsJsonObject())
                .toList();
        assertTrue(savedWhenRunStarted.getFirst().contains("\"base\": 191,"));
        assertEquals(List.of("booting the test server", "goo:values_dumps passed"), events.stream()
                .filter(event -> event.has("progress")).map(event -> event.get("progress").getAsString()).toList());
        JsonObject result = events.getLast().getAsJsonObject("result");
        assertTrue(result.get("durationSeconds").getAsDouble() >= 0);
        List<String> headers = result.getAsJsonArray("sections").asList().stream()
                .map(section -> section.getAsJsonObject().get("header").getAsString()).toList();
        assertEquals(List.of("LEAST VALUE PER TYPE (1 types)", "CLIQUES AND LOSSY RECIPES", "METAL",
                "Goo Audit Report", "EXPRESSION WARNINGS (0 issues in base_values.json expressions)",
                "NO VALUE (0 registered items have no goo value)"), headers);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private HttpResponse<byte[]> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /**
     * The model answers every top-level section of the file, in file order.
     */
    @Test
    void modelAnswersEverySectionInFileOrder() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = get("/api/model");
        assertEquals(200, response.statusCode());
        JsonArray members = JsonParser.parseString(text(response)).getAsJsonObject().getAsJsonArray("members");
        List<String> keys = members.asList().stream()
                .map(member -> member.getAsJsonObject().get("key").getAsString()).toList();
        List<String> sections = keys.stream().filter(SECTIONS::contains).toList();
        assertEquals(SECTIONS, sections);
        assertEquals(List.copyOf(JsonParser.parseString(Files.readString(paths.baseValues())).getAsJsonObject()
                .keySet()), keys);
    }

    /**
     * A saved model with one changed entry rewrites the file.
     */
    @Test
    void savedModelRewritesTheFile() throws IOException, InterruptedException {
        byte[] before = Files.readAllBytes(paths.baseValues());
        JsonObject model = JsonParser.parseString(text(get("/api/model"))).getAsJsonObject();
        JsonObject constants = model.getAsJsonArray("members").get(0).getAsJsonObject();
        constants.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("text", "193");
        HttpResponse<byte[]> saved = client.send(HttpRequest.newBuilder(URI.create(server.url() + "/api/model"))
                .PUT(HttpRequest.BodyPublishers.ofString(model.toString())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, saved.statusCode());
        byte[] after = Files.readAllBytes(paths.baseValues());
        assertNotEquals(new String(before, StandardCharsets.UTF_8), new String(after, StandardCharsets.UTF_8));
        assertTrue(new String(after, StandardCharsets.UTF_8).contains("\"base\": 193,"));
    }

    /**
     * A standing inventory is served as it stands, with no dump run.
     */
    @Test
    void inventoryIsServed() throws IOException, InterruptedException {
        Files.writeString(paths.inventoryFile(), INVENTORY);
        HttpResponse<byte[]> response = get("/api/inventory");
        assertEquals(200, response.statusCode());
        JsonObject inventory = JsonParser.parseString(text(response)).getAsJsonObject();
        for (String section : List.of("items", "blocks", "item_tags", "block_tags", "goo_types", "recipes")) {
            assertTrue(inventory.has(section), section);
        }
        assertEquals(0, dumpsRun.get());
    }

    /**
     * An absent inventory runs the dump once and serves what it wrote.
     */
    @Test
    void absentInventoryRunsTheDump() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = get("/api/inventory");
        assertEquals(200, response.statusCode());
        assertEquals(1, dumpsRun.get());
        JsonElement items = JsonParser.parseString(text(response)).getAsJsonObject().get("items");
        assertEquals("minecraft:iron_nugget", items.getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString());
    }

    /**
     * An item's image is served as PNG bytes from the inventory folder.
     */
    @Test
    void itemImageIsServed() throws IOException, InterruptedException {
        Path nugget = paths.imageFolder().resolve("minecraft/iron_nugget.png");
        Files.createDirectories(nugget.getParent());
        Files.write(nugget, NUGGET_PNG);
        HttpResponse<byte[]> response = get("/api/image?id=minecraft:iron_nugget");
        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(NUGGET_PNG, response.body());
    }

    /**
     * An id climbing out of the image folder is refused.
     */
    @Test
    void imageIdOutsideTheFolderIsRefused() throws IOException, InterruptedException {
        assertEquals(400, get("/api/image?id=minecraft:../../base_values").statusCode());
    }

    private JsonArray evaluated(String fileText) throws IOException, InterruptedException {
        String model = ModelJson.toJson(BaseValuesModel.parse(fileText), "posted");
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(server.url()
                        + "/api/constants/evaluate")).POST(HttpRequest.BodyPublishers.ofString(model)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        return JsonParser.parseString(text(response)).getAsJsonObject().getAsJsonArray("constants");
    }

    private static JsonObject constantNamed(JsonArray constants, String name) {
        return constants.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(constant -> name.equals(constant.get("name").getAsString())).findFirst().orElseThrow();
    }

    /**
     * A scalar constant resolves through the game's parser.
     */
    @Test
    void scalarConstantResolves() throws IOException, InterruptedException {
        JsonObject doubled = constantNamed(evaluated("""
                {
                    "_constants": {
                        "base": 192,
                        "doubled": "2 $base"
                    }
                }
                """), "doubled");
        assertEquals("384", doubled.get("value").getAsString());
        assertTrue(doubled.get("error").isJsonNull());
    }

    /**
     * A tree constant reading another tree's type resolves per type.
     */
    @Test
    void treeConstantResolves() throws IOException, InterruptedException {
        JsonObject stripped = constantNamed(evaluated("""
                {
                    "_constants": {
                        "log": {"leaf": 32},
                        "stripped_log": {"leaf": "$log.leaf * 3 / 4"}
                    }
                }
                """), "stripped_log");
        assertEquals("tree", stripped.get("kind").getAsString());
        assertEquals("24", stripped.getAsJsonObject("tree").get("leaf").getAsString());
    }

    /**
     * An unknown name is refused with an error naming it.
     */
    @Test
    void unknownConstantIsRefused() throws IOException, InterruptedException {
        JsonObject broken = constantNamed(evaluated("""
                {
                    "_constants": {
                        "broken": "$nope + 1"
                    }
                }
                """), "broken");
        assertTrue(broken.get("error").getAsString().contains("$nope") || broken.get("error").getAsString()
                .contains("nope"), broken.toString());
    }

    /**
     * The shipped _constants block resolves whole, each constant to the value the game's parser gives it.
     */
    @Test
    void shippedConstantsResolveAsTheGameResolvesThem() throws IOException, InterruptedException {
        String shipped = new String(BaseValuesModelTest.shippedBytes(), StandardCharsets.UTF_8);
        JsonArray constants = evaluated(shipped);
        JsonObject block = JsonParser.parseString(shipped).getAsJsonObject().getAsJsonObject("_constants");
        assertEquals(List.copyOf(block.keySet()), constants.asList().stream()
                .map(constant -> constant.getAsJsonObject().get("name").getAsString()).toList());
        Map<String, GooConstantResolver.ResolvedConstant> game = GooConstantResolver.resolve(block).stream()
                .collect(Collectors.toMap(GooConstantResolver.ResolvedConstant::name, Function.identity()));
        for (JsonElement element : constants) {
            JsonObject constant = element.getAsJsonObject();
            String name = constant.get("name").getAsString();
            assertTrue(constant.get("error").isJsonNull(), name + ": " + constant.get("error"));
            GooConstantResolver.ResolvedConstant expected = game.get(name);
            if (expected.tree()) {
                expected.perType().forEach((type, amount) ->
                        assertEquals(amount, constant.getAsJsonObject("tree").get(type).getAsString(), name));
            } else {
                assertEquals(String.valueOf(expected.scalar()), constant.get("value").getAsString(), name);
            }
        }
        assertEquals("192", constantNamed(constants, "base").get("value").getAsString());
        assertEquals("384", constantNamed(constants, "low").get("value").getAsString());
        assertEquals("10368", constantNamed(constants, "iron_ingot").getAsJsonObject("tree").get("metal")
                .getAsString());
    }

    /**
     * The page's root answers the single-page app: the five tabs and the inventory sidebar.
     */
    @Test
    void rootServesThePage() throws IOException, InterruptedException {
        HttpResponse<byte[]> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        String page = text(response);
        for (String tab : List.of("Values", "Groups", "Variables", "Processes", "Audit")) {
            assertTrue(page.contains(">" + tab + "<"), tab);
        }
        assertTrue(page.contains("id=\"inventory-sidebar\""));
    }

    /**
     * The Values table heads read the item, its definition, amounts, remainder and source, and the hover line
     * reads the value with no melts or costs (decision values-tab-drops-melts-and-costs).
     */
    @Test
    void valuesTableHeadsReadNoMeltsOrCosts() throws IOException, InterruptedException {
        String page = text(get("/"));
        int body = page.indexOf("id=\"family-rows\"");
        String head = page.substring(page.lastIndexOf("<thead>", body), body);
        List<String> heads = java.util.regex.Pattern.compile("<th>([^<]+)</th>").matcher(head).results()
                .map(match -> match.group(1)).toList();
        assertEquals(List.of("Item", "Defined as", "Amounts", "Remainder", "Source"), heads);
        assertFalse(page.contains("' · melts '"), "hover text still reads melts");
        assertFalse(page.contains("hover.costs"), "hover text still reads costs");
    }
}
