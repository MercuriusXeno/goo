package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooConstantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
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
 * Tests every workbench operation's body over temp copies of the files, with no HTTP between, so the loopback page
 * and the in-game manager answer alike (decision goo-workbench-opens-the-in-game-manager-screen). The operations read
 * and write files, so this test writes under a temp folder.
 */
class WorkbenchOperationsTest {

    private static final List<String> SECTIONS = List.of("_constants", "_groups", "_restricted", "_conversions",
            "_post_conversions");
    private static final String INVENTORY = "{\"items\":[{\"id\":\"minecraft:iron_nugget\",\"name\":\"Iron Nugget\"}],"
            + "\"item_tags\":[],\"goo_types\":[],\"recipes\":[]}";
    // An inventory written before the dump dropped its block sections, which stands until the next dump
    // (decision workbench-drops-the-blocks-list).
    private static final String INVENTORY_WITH_BLOCKS = "{\"items\":[{\"id\":\"minecraft:iron_nugget\","
            + "\"name\":\"Iron Nugget\"}],\"blocks\":[{\"id\":\"minecraft:fire\",\"name\":\"Fire\"}],"
            + "\"item_tags\":[],\"block_tags\":[{\"id\":\"minecraft:fire\",\"members\":[\"minecraft:fire\"]}],"
            + "\"goo_types\":[],\"recipes\":[]}";

    @TempDir
    Path temp;

    private WorkbenchPaths paths;
    private WorkbenchOperations operations;
    private final AtomicInteger dumpsRun = new AtomicInteger();

    @BeforeEach
    void standOperations() throws IOException {
        paths = new WorkbenchPaths(temp.resolve("base_values.json"), temp.resolve("goo_inventory.json"),
                temp.resolve("goo_inventory"), temp.resolve("goo_audit.txt"), temp.resolve("goo_cliques.txt"),
                temp.resolve("goo_audit.json"));
        Files.write(paths.baseValues(), BaseValuesModelTest.sampleBytes());
        operations = new WorkbenchOperations(paths, () -> "{\n}\n".getBytes(StandardCharsets.UTF_8), () -> {
            dumpsRun.incrementAndGet();
            Files.writeString(paths.inventoryFile(), INVENTORY);
        }, (draft, progress) -> 0);
    }

    private WorkbenchReply answered(String operation, String posted) {
        WorkbenchReply reply = operations.answer(operation, posted);
        assertEquals(200, reply.status(), reply.text());
        return reply;
    }

    private JsonObject answeredJson(String operation, String posted) {
        return JsonParser.parseString(answered(operation, posted).text()).getAsJsonObject();
    }

    private static String posted(String fileText) {
        return ModelJson.toJson(BaseValuesModel.parse(fileText), "posted");
    }

    private static String sample() {
        try {
            return new String(BaseValuesModelTest.sampleBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> failingTitles(JsonArray checks) {
        return checks.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(check -> !check.get("passed").getAsBoolean())
                .map(check -> check.get("title").getAsString()).toList();
    }

    /**
     * An operation no constant names answers not found, naming the name it was given.
     */
    @Test
    void unknownOperationIsRefusedByName() {
        WorkbenchReply reply = operations.answer("no/such", "");
        assertEquals(404, reply.status());
        assertTrue(reply.text().contains("no/such"), reply.text());
    }

    @Nested
    class DraftModel {

        private JsonObject savedWithBase(String base) {
            JsonObject model = answeredJson(WorkbenchOperations.MODEL, "");
            model.getAsJsonArray("members").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject().addProperty("text", base);
            return answeredJson(WorkbenchOperations.SAVE_MODEL, model.toString());
        }

        /**
         * The model answers every top-level section of the file, in file order.
         */
        @Test
        void modelAnswersEverySectionInFileOrder() throws IOException {
            List<String> keys = answeredJson(WorkbenchOperations.MODEL, "").getAsJsonArray("members").asList()
                    .stream().map(member -> member.getAsJsonObject().get("key").getAsString()).toList();
            assertEquals(SECTIONS, keys.stream().filter(SECTIONS::contains).toList());
            assertEquals(List.copyOf(JsonParser.parseString(Files.readString(paths.baseValues())).getAsJsonObject()
                    .keySet()), keys);
        }

        /**
         * A save writes the draft and leaves the live file's bytes as they were, and the answer says the draft
         * differs (decision workbench-authors-a-draft-and-promotes-by-rename).
         */
        @Test
        void saveWritesTheDraftAndLeavesTheLiveFile() throws IOException {
            JsonObject draft = savedWithBase("193").getAsJsonObject("draft");
            assertTrue(Files.readString(paths.draft()).contains("\"base\": 193,"));
            assertArrayEquals(BaseValuesModelTest.sampleBytes(), Files.readAllBytes(paths.baseValues()));
            assertTrue(draft.get("differs").getAsBoolean());
            assertEquals(paths.draft().toString(), draft.get("path").getAsString());
        }

        /**
         * Promote renames the draft over the live file, leaving no draft behind
         * (decision workbench-authors-a-draft-and-promotes-by-rename).
         */
        @Test
        void promoteRenamesTheDraftOverTheLiveFile() throws IOException {
            savedWithBase("194");
            String drafted = Files.readString(paths.draft());
            JsonObject promoted = answeredJson(WorkbenchOperations.PROMOTE, "{}");
            assertFalse(Files.exists(paths.draft()));
            assertEquals(drafted, Files.readString(paths.baseValues()));
            assertFalse(promoted.getAsJsonObject("draft").get("differs").getAsBoolean());
        }

        /**
         * Discard copies the live file back over the draft
         * (decision workbench-authors-a-draft-and-promotes-by-rename).
         */
        @Test
        void discardCopiesTheLiveFileOverTheDraft() throws IOException {
            savedWithBase("195");
            JsonObject discarded = answeredJson(WorkbenchOperations.DISCARD, "{}");
            assertEquals(Files.readString(paths.baseValues()), Files.readString(paths.draft()));
            assertFalse(discarded.getAsJsonObject("draft").get("differs").getAsBoolean());
        }

        /**
         * A save of a draft lacking diamond's yore still answers the model, with the old rule failing beside it
         * (decision type-source-checks-are-rules-that-gate-promote).
         */
        @Test
        void saveAnswersTheFailingCheckBesideTheModel() throws IOException {
            JsonObject answer = answeredJson(WorkbenchOperations.SAVE_MODEL, posted(SampleDrafts.diamondLackingYore()));
            assertTrue(answer.has("members"));
            assertEquals(List.of("old-ships-from-scutes-diamonds-and-scrap"),
                    failingTitles(answer.getAsJsonArray("checks")));
        }

        /**
         * Promote over a failing check answers 409 listing it, and the same post acknowledging the failures
         * promotes (decision type-source-checks-are-rules-that-gate-promote).
         */
        @Test
        void promoteOverAFailingCheckWaitsOnAcknowledgement() throws IOException {
            answered(WorkbenchOperations.SAVE_MODEL, posted(SampleDrafts.diamondLackingYore()));
            String drafted = Files.readString(paths.draft());
            WorkbenchReply refused = operations.answer(WorkbenchOperations.PROMOTE, "{}");
            assertEquals(409, refused.status());
            assertEquals(List.of("old-ships-from-scutes-diamonds-and-scrap"),
                    failingTitles(JsonParser.parseString(refused.text()).getAsJsonObject().getAsJsonArray("failing")));
            assertArrayEquals(BaseValuesModelTest.sampleBytes(), Files.readAllBytes(paths.baseValues()));
            answered(WorkbenchOperations.PROMOTE, "{\"acknowledged\": true}");
            assertEquals(drafted, Files.readString(paths.baseValues()));
        }

        /**
         * Promote with no draft standing is refused with a conflict and leaves the live file as it was.
         */
        @Test
        void promoteWithNoDraftIsRefused() throws IOException {
            assertEquals(409, operations.answer(WorkbenchOperations.PROMOTE, "{}").status());
            assertArrayEquals(BaseValuesModelTest.sampleBytes(), Files.readAllBytes(paths.baseValues()));
        }

        /**
         * A save sending no members array is refused as a bad request naming the cause.
         */
        @Test
        void saveWithNoMembersIsABadRequest() {
            WorkbenchReply refused = operations.answer(WorkbenchOperations.SAVE_MODEL, "{}");
            assertEquals(400, refused.status());
            assertTrue(refused.text().contains("members"), refused.text());
        }
    }

    @Nested
    class Inventory {

        /**
         * A standing inventory answers as it stands, with no dump run.
         */
        @Test
        void standingInventoryAnswersWithNoDump() throws IOException {
            Files.writeString(paths.inventoryFile(), INVENTORY);
            JsonObject inventory = answeredJson(WorkbenchOperations.INVENTORY, "");
            for (String section : List.of("items", "item_tags", "goo_types", "recipes")) {
                assertTrue(inventory.has(section), section);
            }
            assertEquals(0, dumpsRun.get());
        }

        /**
         * An absent inventory runs the dump once and answers what it wrote.
         */
        @Test
        void absentInventoryRunsTheDump() {
            JsonObject inventory = answeredJson(WorkbenchOperations.INVENTORY, "");
            assertEquals(1, dumpsRun.get());
            assertEquals("minecraft:iron_nugget", inventory.getAsJsonArray("items").get(0).getAsJsonObject()
                    .get("id").getAsString());
        }

        /**
         * A groups read on a fresh tree waits on the same one-time dump the inventory runs.
         */
        @Test
        void groupsOnAbsentInventoryWaitForTheDump() {
            answered(WorkbenchOperations.DESCRIBE_GROUPS, answered(WorkbenchOperations.MODEL, "").text());
            assertEquals(1, dumpsRun.get());
        }

        /**
         * An inventory still carrying blocks reads its known ids from the items section alone, so a block id with
         * no item form reads unknown (decision workbench-drops-the-blocks-list).
         */
        @Test
        void inventoryStillCarryingBlocksReadsKnownIdsFromItemsAlone() throws IOException {
            Files.writeString(paths.inventoryFile(), INVENTORY_WITH_BLOCKS);
            JsonArray groups = answeredJson(WorkbenchOperations.DESCRIBE_GROUPS, posted("""
                    {
                        "_groups": {
                            "hot": ["fire", "iron_nugget"]
                        },
                        "#hot": 1
                    }
                    """)).getAsJsonArray("groups");
            JsonArray members = groups.asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(group -> "hot".equals(group.get("name").getAsString())).findFirst().orElseThrow()
                    .getAsJsonArray("members");
            Map<String, Boolean> known = members.asList().stream().map(JsonElement::getAsJsonObject)
                    .collect(Collectors.toMap(member -> member.get("id").getAsString(),
                            member -> member.get("known").getAsBoolean()));
            assertEquals(Map.of("minecraft:fire", false, "minecraft:iron_nugget", true), known);
        }
    }

    @Nested
    class ValuesAndProcesses {

        /**
         * Resolve answers each entry of the posted model with its amount per type, as the loader values it.
         */
        @Test
        void resolveAnswersEachEntrysAmountsPerType() {
            JsonArray entries = answeredJson(WorkbenchOperations.RESOLVE_VALUES, posted("""
                    {
                        "stick": {"leaf": 12},
                        "torch": "denied"
                    }
                    """)).getAsJsonArray("entries");
            JsonObject stick = entries.get(0).getAsJsonObject();
            assertEquals("stick", stick.get("key").getAsString());
            assertEquals("12", stick.getAsJsonObject("value").get("leaf").getAsString());
            assertTrue(entries.get(1).getAsJsonObject().get("denied").getAsBoolean());
        }

        /**
         * Processes answers the posted model's conversions described.
         */
        @Test
        void processesDescribesThePostedConversions() {
            JsonObject processes = answeredJson(WorkbenchOperations.PROCESSES, posted(sample()));
            assertEquals(JsonParser.parseString(ProcessesJson.described(BaseValuesModel.parse(sample()))),
                    processes);
        }
    }

    @Nested
    class Constants {

        private JsonArray evaluated(String fileText) {
            return answeredJson(WorkbenchOperations.EVALUATE_CONSTANTS, posted(fileText)).getAsJsonArray("constants");
        }

        private static JsonObject constantNamed(JsonArray constants, String name) {
            return constants.asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(constant -> name.equals(constant.get("name").getAsString())).findFirst().orElseThrow();
        }

        private WorkbenchReply named(String operation, String name, String fileText) {
            JsonObject body = JsonParser.parseString(posted(fileText)).getAsJsonObject();
            body.addProperty("name", name);
            return operations.answer(operation, body.toString());
        }

        /**
         * A scalar constant resolves through the game's parser.
         */
        @Test
        void scalarConstantResolves() {
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
        void treeConstantResolves() {
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
        void unknownConstantIsRefused() {
            JsonObject broken = constantNamed(evaluated("""
                    {
                        "_constants": {
                            "broken": "$nope + 1"
                        }
                    }
                    """), "broken");
            assertTrue(broken.get("error").getAsString().contains("nope"), broken.toString());
        }

        /**
         * usedBy answers the terminus items alone, while readers keeps each direct reader with its kind
         * (decision used-by-draws-the-terminus-items-alone).
         */
        @Test
        void usedByAnswersTheTerminusItemsAlone() {
            JsonObject base = constantNamed(evaluated("""
                    {
                        "_constants": {
                            "base": 192,
                            "doubled": "2 $base"
                        },
                        "_groups": {
                            "logs": ["oak_log"]
                        },
                        "#logs": "$base",
                        "stick": "$base / 4",
                        "plank": "$doubled"
                    }
                    """), "base");
            assertEquals(List.of("oak_log", "stick", "plank"), base.getAsJsonArray("usedBy").asList().stream()
                    .map(JsonElement::getAsString).toList());
            assertEquals(List.of("doubled constant", "#logs group", "stick item"),
                    base.getAsJsonArray("readers").asList().stream()
                            .map(reader -> reader.getAsJsonObject().get("id").getAsString() + " "
                                    + reader.getAsJsonObject().get("kind").getAsString()).toList());
        }

        /**
         * The shipped _constants block resolves whole, each constant to the value the game's parser gives it.
         */
        @Test
        void shippedConstantsResolveAsTheGameResolvesThem() {
            JsonArray constants = evaluated(sample());
            JsonObject block = JsonParser.parseString(sample()).getAsJsonObject().getAsJsonObject("_constants");
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
        }

        /**
         * The constant hover answers $gold_nugget's expression as the file spells it, one resolved value per type
         * and the items terminusOf reaches, and 404 for a name no constant holds (decision
         * references-link-to-their-variable-and-hover-a-card).
         */
        @Test
        void constantHoverAnswersExpressionResolutionAndTerminus() {
            WorkbenchReply reply = named(WorkbenchOperations.CONSTANT_HOVER, "gold_nugget", sample());
            assertEquals(200, reply.status(), reply.text());
            JsonObject hover = JsonParser.parseString(reply.text()).getAsJsonObject();
            assertEquals("{\"metal\": \"4 $iron_nugget.metal\", \"glow\": 1440, \"opulent\": \"$wealth.opulent\"}",
                    hover.get("expression").getAsString());
            JsonObject tree = hover.getAsJsonObject("tree");
            assertEquals(Map.of("metal", "4608", "glow", "1440", "opulent", "192"), tree.keySet().stream()
                    .collect(Collectors.toMap(type -> type, type -> tree.get(type).getAsString())));
            assertEquals(BaseValuesModel.parse(sample()).terminusOf("gold_nugget", Map.of()),
                    hover.getAsJsonArray("usedBy").asList().stream().map(JsonElement::getAsString).toList());
            assertEquals(404, named(WorkbenchOperations.CONSTANT_HOVER, "no_such_constant", sample()).status());
        }

        /**
         * A delete answers the model with the constant and its reader cascade gone, and refuses a name no constant
         * holds (decision deleting-a-variable-inlines-its-expression-into-each-reader).
         */
        @Test
        void deleteAnswersTheModelWithoutTheCascade() {
            String file = """
                    {
                        "_constants": {
                            "base": 192,
                            "doubled": "2 $base",
                            "kept": 4
                        },
                        "stick": "$doubled / 4",
                        "torch": "$kept"
                    }
                    """;
            WorkbenchReply reply = named(WorkbenchOperations.DELETE_CONSTANT, "base", file);
            assertEquals(200, reply.status(), reply.text());
            JsonArray members = JsonParser.parseString(reply.text()).getAsJsonObject().getAsJsonArray("members");
            assertEquals(List.of("_constants", "torch"), members.asList().stream()
                    .map(member -> member.getAsJsonObject().get("key").getAsString()).toList());
            assertEquals(List.of("kept"), members.get(0).getAsJsonObject().getAsJsonArray("entries").asList()
                    .stream().map(entry -> entry.getAsJsonObject().get("key").getAsString()).toList());
            assertNotEquals(200, named(WorkbenchOperations.DELETE_CONSTANT, "nope", file).status());
        }
    }
}
