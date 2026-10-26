package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.command.ValuesManagers;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.workbench.BaseValuesModel;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mercuriusxeno.goo.workbench.WorkbenchPaths;
import com.mercuriusxeno.goo.workbench.WorkbenchReply;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Drives the values manager's operations on the test server with the in-game applier bound: a Save changes the
 * value the server holds for the edited item with no /reload and no /goo regen
 * (decision save-applies-the-draft-live-promote-renames-it). It runs in an environment of its own, so its batch never
 * shares the world's goo_values folder with workbench_serves.
 */
@RegistersGameTests
public final class GooValuesManagerTests {
    private static final String SAVE_APPLIES_LIVE = "values_manager_save_applies_live";
    private static final String EDITED_KEY = "dragon_egg";
    private static final Identifier EDITED_ITEM = Identifier.withDefaultNamespace(EDITED_KEY);
    private static final String EDITED_TEXT = "{\"void\": 1234}";
    private static final long EDITED_TOTAL = 1234;
    private static final String DRAFT_DROPS_UNKNOWN_TYPES = "values_draft_drops_unknown_types";
    private static final String RETIRED_KEY = "goo_retired_type_probe";
    private static final String RETIRED_TEXT = "{\"aeon\": 3}";

    private GooValuesManagerTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SAVE_APPLIES_LIVE, GooValuesManagerTests::saveAppliesLive);
        GooTestFunctions.registerTest(registrar, DRAFT_DROPS_UNKNOWN_TYPES,
                GooValuesManagerTests::draftLoadDropsUnknownTypes);
    }

    /**
     * Saves a draft valuing the dragon egg at 1234 void, reads the value the server holds, then saves the draft it
     * found back, so the values the later batches read stand as they were.
     *
     * @param helper the gametest helper
     */
    public static void saveAppliesLive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorkbenchOperations operations;
        try {
            operations = ValuesManagers.operationsFor(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String original = answered(helper, operations, WorkbenchOperations.MODEL, "");
        JsonObject edited = JsonParser.parseString(original).getAsJsonObject();
        JsonObject entry = edited.getAsJsonArray("members").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(member -> member.get("key") != null && EDITED_KEY.equals(member.get("key").getAsString()))
                .findFirst().orElse(null);
        helper.assertTrue(entry != null, "the draft holds no " + EDITED_KEY + " entry to edit");
        entry.addProperty("text", EDITED_TEXT);
        GooValue held;
        try {
            answered(helper, operations, WorkbenchOperations.SAVE_MODEL, edited.toString());
            held = GooValues.of(server).lookup(EDITED_ITEM);
        } finally {
            answered(helper, operations, WorkbenchOperations.SAVE_MODEL, original);
        }
        helper.assertTrue(held != null && held.typeCount() == 1
                && held.totalExact().equals(GooFraction.of(EDITED_TOTAL)),
                "after the save the server holds " + held + " for " + EDITED_ITEM + ", not " + EDITED_TEXT);
        helper.succeed();
    }

    /**
     * Writes a draft holding an entry keyed by aeon, a type the server's goo_type registry does not hold, and reads
     * the model: the reply and the draft file both lack the entry
     * (decision draft-load-drops-entries-naming-an-unknown-type).
     *
     * @param helper the gametest helper
     */
    public static void draftLoadDropsUnknownTypes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path draft = WorkbenchPaths.inWorld(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())
                .draft();
        try {
            WorkbenchOperations operations = ValuesManagers.operationsFor(server);
            answered(helper, operations, WorkbenchOperations.MODEL, "");
            String standing = Files.readString(draft);
            Files.writeString(draft, BaseValuesModel.parse(standing).withTopLevel(RETIRED_KEY, RETIRED_TEXT)
                    .serialize());
            String model = answered(helper, operations, WorkbenchOperations.MODEL, "");
            boolean inReply = JsonParser.parseString(model).getAsJsonObject().getAsJsonArray("members").asList()
                    .stream().map(JsonElement::getAsJsonObject)
                    .anyMatch(member -> member.get("key") != null && !member.get("key").isJsonNull()
                            && RETIRED_KEY.equals(member.get("key").getAsString()));
            helper.assertTrue(!inReply, "the model reply still holds " + RETIRED_KEY);
            helper.assertTrue(!Files.readString(draft).contains(RETIRED_KEY),
                    "the draft file still holds " + RETIRED_KEY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }

    private static String answered(GameTestHelper helper, WorkbenchOperations operations, String operation,
                                   String posted) {
        WorkbenchReply reply = operations.answer(operation, posted);
        helper.assertTrue(reply.status() == WorkbenchReply.OK, operation + " answered " + reply.status() + ": "
                + reply.text());
        return reply.text();
    }
}
