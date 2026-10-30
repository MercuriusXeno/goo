package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooConstantResolver;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    private static final long ONE_SECOND = 1000;

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
        }, (draft, progress) -> 0, () -> GooTypes.BUNDLED);
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
         * The model a read or a save answers carries the members and no checks member
         * (decision values-checks-go-with-the-checks-tab).
         */
        @Test
        void modelReplyCarriesNoChecks() throws IOException {
            JsonObject read = answeredJson(WorkbenchOperations.MODEL, "");
            JsonObject saved = answeredJson(WorkbenchOperations.SAVE_MODEL, posted(SampleDrafts.diamondLackingYore()));
            assertTrue(saved.has("members"));
            assertFalse(read.has("checks"), read.keySet().toString());
            assertFalse(saved.has("checks"), saved.keySet().toString());
        }

        /**
         * A promote over a draft holding diamond with no yore term, which the retired old rule failed, answers on
         * its first post and leaves the draft's text in the live file (decision values-checks-go-with-the-checks-tab).
         */
        @Test
        void promoteOverAnyDraftAnswersOnce() throws IOException {
            answered(WorkbenchOperations.SAVE_MODEL, posted(SampleDrafts.diamondLackingYore()));
            String drafted = Files.readString(paths.draft());
            answered(WorkbenchOperations.PROMOTE, "{}");
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

    }

    /**
     * The in-game manager posts the members changed since its last save, which merge into the draft by member key
     * (decision requests-post-only-the-changed-members).
     */
    @Nested
    class ChangeSets {
        private static final String DRAFT = """
                {
                    "stick": {"leaf": 12},
                    "torch": {"leaf": 4},
                    "apple": {"leaf": 2}
                }
                """;

        @BeforeEach
        void standTheDraft() throws IOException {
            Files.writeString(paths.draft(), DRAFT);
        }

        // The member a one-entry file holds, as the manager posts it.
        private static JsonObject member(String fileText) {
            return JsonParser.parseString(posted(fileText)).getAsJsonObject().getAsJsonArray("members").get(0)
                    .getAsJsonObject();
        }

        private static String changeSet(List<JsonObject> changed, List<String> removed) {
            JsonObject changes = new JsonObject();
            JsonArray members = new JsonArray();
            changed.forEach(members::add);
            changes.add("changed", members);
            JsonArray keys = new JsonArray();
            removed.forEach(keys::add);
            changes.add("removed", keys);
            return changes.toString();
        }

        private Map<String, String> leafByKey(String posted) {
            return answeredJson(WorkbenchOperations.RESOLVE_VALUES, posted).getAsJsonArray("entries").asList()
                    .stream().map(JsonElement::getAsJsonObject).collect(Collectors.toMap(
                            entry -> entry.get("key").getAsString(),
                            entry -> entry.getAsJsonObject("value").get("leaf").getAsString()));
        }

        /**
         * The manager's open asks for no inventory, so a resolve and a constants evaluate on a world with no inventory
         * dump it first and read it, rather than answering as if no tag held a member.
         */
        @Test
        void draftReadsOnAWorldWithNoInventoryDumpItFirst() {
            answered(WorkbenchOperations.RESOLVE_VALUES, "");
            assertEquals(1, dumpsRun.get());
            assertTrue(Files.isRegularFile(paths.inventoryFile()));
        }

        /**
         * A constants evaluate on a world with no inventory dumps it first.
         */
        @Test
        void constantsEvaluateOnAWorldWithNoInventoryDumpsItFirst() {
            answered(WorkbenchOperations.EVALUATE_CONSTANTS, "");
            assertEquals(1, dumpsRun.get());
        }

        /**
         * An empty post resolves the draft as it stands.
         */
        @Test
        void emptyPostResolvesTheDraft() {
            assertEquals(Map.of("stick", "12", "torch", "4", "apple", "2"), leafByKey(""));
        }

        /**
         * A change set holding one changed member resolves that member's new value and the draft's for the rest.
         */
        @Test
        void changedMemberResolvesOverTheDraft() {
            String posted = changeSet(List.of(member("{\n    \"stick\": {\"leaf\": 20}\n}")), List.of());
            assertEquals(Map.of("stick", "20", "torch", "4", "apple", "2"), leafByKey(posted));
        }

        /**
         * A save of a change set writes the draft with the changed member, without the removed one, with an added
         * member after the key it follows, and every other member as it stood.
         */
        @Test
        void saveMergesTheChangeSetIntoTheDraft() throws IOException {
            JsonObject bread = member("{\n    \"bread\": {\"leaf\": 6}\n}");
            bread.addProperty("after", "stick");
            String posted = changeSet(List.of(member("{\n    \"stick\": {\"leaf\": 20}\n}"), bread),
                    List.of("torch"));
            answered(WorkbenchOperations.SAVE_MODEL, posted);
            JsonObject written = JsonParser.parseString(Files.readString(paths.draft())).getAsJsonObject();
            assertEquals(List.of("stick", "bread", "apple"), List.copyOf(written.keySet()));
            assertEquals(20, written.getAsJsonObject("stick").get("leaf").getAsInt());
            assertEquals(2, written.getAsJsonObject("apple").get("leaf").getAsInt());
        }
    }

    @Nested
    class LiveApply {

        private final List<String> applied = new java.util.ArrayList<>();
        private WorkbenchOperations live;

        @BeforeEach
        void bindTheFakeApplier() {
            live = new WorkbenchOperations(paths, () -> new byte[0], () -> { }, (draft, progress) -> 0,
                    applied::add, () -> GooTypes.BUNDLED);
        }

        private void saveWithBase(String base) {
            WorkbenchReply model = live.answer(WorkbenchOperations.MODEL, "");
            JsonObject json = JsonParser.parseString(model.text()).getAsJsonObject();
            json.getAsJsonArray("members").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject().addProperty("text", base);
            assertEquals(200, live.answer(WorkbenchOperations.SAVE_MODEL, json.toString()).status());
        }

        /**
         * A save hands the applier the draft it wrote, so the world runs the draft at once
         * (decision save-applies-the-draft-live-promote-renames-it).
         */
        @Test
        void saveHandsTheApplierTheDraftItWrote() throws IOException {
            saveWithBase("196");
            assertEquals(List.of(Files.readString(paths.draft())), applied);
        }

        /**
         * Promote renames the draft over the live file and hands the applier the live file, which now holds the
         * former draft (decision save-applies-the-draft-live-promote-renames-it).
         */
        @Test
        void promoteHandsTheApplierTheLiveFile() throws IOException {
            saveWithBase("197");
            String drafted = Files.readString(paths.draft());
            assertEquals(200, live.answer(WorkbenchOperations.PROMOTE, "{}").status());
            assertEquals(drafted, Files.readString(paths.baseValues()));
            assertEquals(List.of(drafted, drafted), applied);
        }

        /**
         * Discard copies the live file over the draft and hands the applier the live file, so the world runs the
         * live values again (decision save-applies-the-draft-live-promote-renames-it).
         */
        @Test
        void discardHandsTheApplierTheLiveFile() throws IOException {
            saveWithBase("198");
            assertEquals(200, live.answer(WorkbenchOperations.DISCARD, "{}").status());
            String liveText = Files.readString(paths.baseValues());
            assertEquals(liveText, Files.readString(paths.draft()));
            assertEquals(liveText, applied.getLast());
        }
    }

    /**
     * A draft read drops each entry naming a goo type the known set does not hold
     * (decision draft-load-drops-entries-naming-an-unknown-type).
     */
    @Nested
    class UnknownTypes {
        private static final String DRAFT_WITH_AEON = """
                {
                    "_constants": {
                        "x": {"leaf": 4},
                        "old": {"aeon": 2}
                    },
                    "stick": {"leaf": 1},
                    "relic": {"aeon": 3},
                    "fossil": "$x.aeon",
                    "plank": "$x.leaf"
                }
                """;
        private static final String DRAFT_WITHOUT_AEON = """
                {
                    "_constants": {
                        "x": {"leaf": 4}
                    },
                    "stick": {"leaf": 1},
                    "plank": "$x.leaf"
                }
                """;
        private static final String DRAFT_WITH_A_DATAPACK_TYPE = """
                {
                    "stick": {"leaf": 1},
                    "relic": {"mypack:aeon": 3}
                }
                """;

        private WorkbenchOperations knowing(List<ResourceKey<GooTypeDefinition>> known) {
            return new WorkbenchOperations(paths, () -> new byte[0], () -> { }, (draft, progress) -> 0, () -> known);
        }

        private static List<String> keys(JsonArray members) {
            return members.asList().stream().map(member -> member.getAsJsonObject().get("key").getAsString())
                    .toList();
        }

        /**
         * A per-type key and a $prefab.type term naming aeon each drop their entry from the model and the draft
         * file, and every other entry stays as the draft spelled it.
         */
        @Test
        void entriesNamingAnUnknownTypeDropFromTheModelAndTheDraft() throws IOException {
            Files.writeString(paths.draft(), DRAFT_WITH_AEON);
            WorkbenchReply reply = knowing(GooTypes.BUNDLED).answer(WorkbenchOperations.MODEL, "");
            assertEquals(200, reply.status(), reply.text());
            JsonArray members = JsonParser.parseString(reply.text()).getAsJsonObject().getAsJsonArray("members");
            assertEquals(List.of("_constants", "stick", "plank"), keys(members));
            assertEquals(List.of("x"), keys(members.get(0).getAsJsonObject().getAsJsonArray("entries")));
            assertEquals(DRAFT_WITHOUT_AEON, Files.readString(paths.draft()));
        }

        /**
         * An entry keyed by a datapack type the known set holds reads back intact.
         */
        @Test
        void entryNamingAKnownDatapackTypeSurvives() throws IOException {
            Files.writeString(paths.draft(), DRAFT_WITH_A_DATAPACK_TYPE);
            List<ResourceKey<GooTypeDefinition>> known = new java.util.ArrayList<>(GooTypes.BUNDLED);
            known.add(GooTypes.byId("mypack:aeon"));
            WorkbenchReply reply = knowing(known).answer(WorkbenchOperations.MODEL, "");
            assertEquals(200, reply.status(), reply.text());
            JsonObject relic = JsonParser.parseString(reply.text()).getAsJsonObject().getAsJsonArray("members")
                    .asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(member -> "relic".equals(member.get("key").getAsString())).findFirst().orElseThrow();
            assertEquals("{\"mypack:aeon\": 3}", relic.get("text").getAsString());
            assertEquals(DRAFT_WITH_A_DATAPACK_TYPE, Files.readString(paths.draft()));
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

        /**
         * The groups reads keep their inventory parse while the file is unchanged and parse again once a dump
         * rewrites it (decision inventory-parses-once-per-file-timestamp).
         */
        @Test
        void groupsInventoryReusesItsParseUntilTheFileChanges() throws IOException {
            Path file = paths.inventoryFile();
            Files.writeString(file, inventoryWithTags("minecraft:logs"));
            FileTime written = Files.getLastModifiedTime(file);
            GroupsJson.Inventory first = GroupsJson.Inventory.read(file);
            assertSame(first, GroupsJson.Inventory.read(file));
            Files.writeString(file, inventoryWithTags("minecraft:logs", "minecraft:planks"));
            Files.setLastModifiedTime(file, FileTime.fromMillis(written.toMillis() + ONE_SECOND));
            assertEquals(Set.of("minecraft:logs", "minecraft:planks"), GroupsJson.Inventory.read(file).tags().keySet());
        }

        private static String inventoryWithTags(String... tags) {
            String tagJson = java.util.Arrays.stream(tags)
                    .map(tag -> "{\"id\":\"" + tag + "\",\"members\":[\"minecraft:iron_nugget\"]}")
                    .collect(Collectors.joining(","));
            return "{\"items\":[{\"id\":\"minecraft:iron_nugget\",\"name\":\"Iron Nugget\"}],"
                    + "\"item_tags\":[" + tagJson + "],\"goo_types\":[],\"recipes\":[]}";
        }
    }

    /**
     * The Groups tab's apply forms (decision group-controls-name-add-and-build).
     */
    @Nested
    class GroupApply {
        private static final String FILE = """
                {
                    "_constants": {"log": {"leaf": 4}},
                    "_groups": {
                        "logs": ["iron_nugget"]
                    },
                    "#logs": "$log"
                }
                """;

        private JsonObject applied(JsonObject change) throws IOException {
            Files.writeString(paths.inventoryFile(), INVENTORY);
            JsonObject request = JsonParser.parseString(posted(FILE)).getAsJsonObject();
            change.entrySet().forEach(field -> request.add(field.getKey(), field.getValue()));
            return answeredJson(WorkbenchOperations.APPLY_TO_GROUP, request.toString()).getAsJsonObject("model");
        }

        private static JsonObject memberKeyed(JsonObject model, String key) {
            return model.getAsJsonArray("members").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(member -> member.has("key") && !member.get("key").isJsonNull()
                            && key.equals(member.get("key").getAsString())).findFirst().orElseThrow();
        }

        /**
         * The create form lands a named group in _groups with no member, and leaves a standing group as it was.
         */
        @Test
        void createFormLandsAGroupInGroups() throws IOException {
            JsonObject create = new JsonObject();
            create.addProperty("group", "fresh");
            create.addProperty("create", true);
            JsonObject groups = memberKeyed(applied(create), "_groups");
            Map<String, String> entries = groups.getAsJsonArray("entries").asList().stream()
                    .map(JsonElement::getAsJsonObject).collect(Collectors.toMap(entry -> entry.get("key").getAsString(),
                            entry -> entry.get("text").getAsString()));
            assertEquals("[]", entries.get("fresh"));
            assertTrue(entries.get("logs").contains("iron_nugget"), entries.toString());
            JsonObject again = new JsonObject();
            again.addProperty("group", "logs");
            again.addProperty("create", true);
            assertTrue(memberKeyed(applied(again), "_groups").toString().contains("iron_nugget"));
        }

        /**
         * A member line with a count and an extra writes count(base + $trait) and the trait beside the prefabs.
         */
        @Test
        void memberLineWritesCountTimesBasePlusExtra() throws IOException {
            JsonObject tweak = new JsonObject();
            tweak.addProperty("member", "minecraft:iron_nugget");
            tweak.addProperty("count", 2);
            tweak.addProperty("trait", "nugget_extra");
            tweak.addProperty("traitText", "{\"hex\": \"3\"}");
            JsonObject change = new JsonObject();
            change.addProperty("group", "logs");
            change.add("tweak", tweak);
            JsonObject model = applied(change);
            assertEquals("\"2($log + $nugget_extra)\"", memberKeyed(model, "iron_nugget").get("text").getAsString());
            assertTrue(memberKeyed(model, "_constants").toString().contains("nugget_extra"));
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
         * Derived-from answers, for each crafted item no entry values, its recipe's inputs grouped by item with the
         * count summed across the recipe's slots, and the recipe's output count: a table from four planks, planks
         * four to a log; the valued log answers nothing (decision
         * recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
         */
        @Test
        void derivedFromAnswersTheRecipeInputsTheFamilyReports() throws IOException {
            Files.writeString(paths.inventoryFile(), ValuationsTest.logPlankTableInventory());
            String logs = """
                    {
                        "oak_log": {"leaf": 16}
                    }
                    """;
            JsonObject items = answeredJson(WorkbenchOperations.DERIVED_FROM, posted(logs)).getAsJsonObject("items");
            assertEquals(JsonParser.parseString("""
                    {"inputs": [{"id": "minecraft:oak_planks", "count": 4}], "output": 1}
                    """), items.get("minecraft:crafting_table"));
            assertEquals(JsonParser.parseString("""
                    {"inputs": [{"id": "minecraft:oak_log", "count": 1}], "output": 4}
                    """), items.get("minecraft:oak_planks"));
            assertFalse(items.has("minecraft:oak_log"), items.toString());
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
    class DumpRun {

        /**
         * A dump run saves the posted model as the draft, hands each line the run prints to progress in order, and
         * answers its exit code with the dumps' sections.
         */
        @Test
        void runHandsProgressOnAndAnswersTheExitCode() throws IOException {
            WorkbenchOperations running = new WorkbenchOperations(paths, () -> new byte[0], () -> { },
                    (draft, progress) -> {
                        progress.accept("booting");
                        progress.accept("auditing");
                        return 0;
                    }, () -> GooTypes.BUNDLED);
            List<String> progress = new java.util.ArrayList<>();
            WorkbenchReply reply = running.answer(WorkbenchOperations.RUN_DUMPS, posted(sample()), progress::add);
            assertEquals(200, reply.status(), reply.text());
            assertEquals(List.of("booting", "auditing"), progress);
            JsonObject result = JsonParser.parseString(reply.text()).getAsJsonObject();
            assertEquals(0, result.get("exitCode").getAsInt());
            assertTrue(result.has("sections"));
            assertTrue(Files.isRegularFile(paths.draft()));
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
