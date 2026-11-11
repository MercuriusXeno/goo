package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.command.ValuesManagers;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.workbench.AbilityDrafts;
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
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Drives the Abilities tab's operations on the test server with the in-game ability files bound: a Save writes one
 * draft for the edited ability outside every pack's data folder, and the server's abilities read the edit with no
 * /reload (decision ability-drafts-promote-per-file-like-base-values). It runs in an environment of its own, so its
 * batch never shares the world's drafts with another.
 */
@RegistersGameTests
public final class AbilityManagerTests {
    private static final String SAVE_WRITES_A_DRAFT = "ability_manager_save_writes_a_draft_and_applies_live";
    private static final Identifier EDITED = Identifier.fromNamespaceAndPath("goo", "unstable_lurker");
    private static final int EDITED_COST = 777;
    private static final String PROMOTE_LANDS_THE_DRAFT = "ability_manager_promote_lands_the_draft_live";
    private static final String DISCARD_DROPS_THE_DRAFTS = "ability_manager_discard_drops_the_drafts";
    private static final int CONFLICT = 409;

    private AbilityManagerTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SAVE_WRITES_A_DRAFT, AbilityManagerTests::saveWritesADraft);
        GooTestFunctions.registerTest(registrar, PROMOTE_LANDS_THE_DRAFT, AbilityManagerTests::promoteLandsTheDraft);
        GooTestFunctions.registerTest(registrar, DISCARD_DROPS_THE_DRAFTS, AbilityManagerTests::discardDropsTheDrafts);
    }

    /**
     * Saves Lurker at 777 and promotes: the world pack's live file holds the edit, no draft stands, the server prices
     * 777, and a second promote with no draft of either kind answers 409; then the promoted file goes and the
     * abilities apply afresh, so later batches read the shipped Lurker. A values draft standing from another batch is
     * set aside first, so this promote moves no base_values.json into the world.
     *
     * @param helper the gametest helper
     */
    public static void promoteLandsTheDraft(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorkbenchPaths paths = WorkbenchPaths.inWorld(server.getWorldPath(LevelResource.ROOT).toAbsolutePath()
                .normalize());
        Path live = paths.abilities().resolve(EDITED.getPath() + ".json");
        Path valuesAside = paths.draft().resolveSibling("base_values.draft.aside");
        try {
            WorkbenchOperations operations = ValuesManagers.operationsFor(server);
            if (Files.isRegularFile(paths.draft())) {
                Files.move(paths.draft(), valuesAside);
            }
            try {
                answered(helper, operations, WorkbenchOperations.SAVE_ABILITIES, costEdit(EDITED_COST));
                answered(helper, operations, WorkbenchOperations.PROMOTE, "");
                helper.assertTrue(Files.isRegularFile(live) && Files.readString(live).contains("777"),
                        "the promote left no live file holding the edit at " + live);
                helper.assertTrue(!Files.isRegularFile(AbilityDrafts.fileOf(paths.abilityDrafts(), EDITED)),
                        "a draft still stands after the promote");
                AbilityDefinition held = AbilityRegistry.of(server).getAbility(EDITED);
                int heldCost = held == null ? -1 : held.cost().in(GooValues.of(server));
                helper.assertTrue(heldCost == EDITED_COST, "after the promote the server prices " + EDITED + " at "
                        + heldCost + ", not " + EDITED_COST);
                WorkbenchReply again = operations.answer(WorkbenchOperations.PROMOTE, "");
                helper.assertTrue(again.status() == CONFLICT, "a promote with no draft answered " + again.status());
            } finally {
                Files.deleteIfExists(live);
                operations.answer(WorkbenchOperations.DISCARD, "");
                if (Files.isRegularFile(valuesAside)) {
                    Files.move(valuesAside, paths.draft(), StandardCopyOption.REPLACE_EXISTING);
                }
                // The promote and discard lay the values draft over the gametests' owned values; lay them back.
                GametestOwnedValues.apply(server);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }

    /**
     * Saves Lurker at 777 and discards: no ability draft stands and the server prices the shipped cost again.
     *
     * @param helper the gametest helper
     */
    public static void discardDropsTheDrafts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        WorkbenchPaths paths = WorkbenchPaths.inWorld(server.getWorldPath(LevelResource.ROOT).toAbsolutePath()
                .normalize());
        AbilityDefinition shipped = AbilityRegistry.of(server).getAbility(EDITED);
        helper.assertTrue(shipped != null, "the server holds no " + EDITED);
        int shippedCost = shipped.cost().in(GooValues.of(server));
        try {
            WorkbenchOperations operations = ValuesManagers.operationsFor(server);
            answered(helper, operations, WorkbenchOperations.SAVE_ABILITIES, costEdit(EDITED_COST));
            answered(helper, operations, WorkbenchOperations.DISCARD, "");
            helper.assertTrue(AbilityDrafts.read(paths.abilityDrafts()).isEmpty(), "a draft stands after the discard");
            AbilityDefinition held = AbilityRegistry.of(server).getAbility(EDITED);
            int heldCost = held == null ? -1 : held.cost().in(GooValues.of(server));
            helper.assertTrue(heldCost == shippedCost, "after the discard the server prices " + EDITED + " at "
                    + heldCost + ", not the shipped " + shippedCost);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // The discard lays the values draft over the gametests' owned values; lay them back.
            GametestOwnedValues.apply(server);
        }
        helper.succeed();
    }

    /**
     * Saves Lurker at a cost of 777, reads the draft and the cost the server holds, then saves the shipped cost back
     * and drops the draft, so the abilities the later batches read stand as they were.
     *
     * @param helper the gametest helper
     */
    public static void saveWritesADraft(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        WorkbenchPaths paths = WorkbenchPaths.inWorld(world);
        Path draft = AbilityDrafts.fileOf(paths.abilityDrafts(), EDITED);
        AbilityDefinition shipped = AbilityRegistry.of(server).getAbility(EDITED);
        helper.assertTrue(shipped != null, "the server holds no " + EDITED);
        int shippedCost = shipped.cost().in(GooValues.of(server));
        Set<Identifier> before = idsOf(server);
        try {
            WorkbenchOperations operations = ValuesManagers.operationsFor(server);
            answered(helper, operations, WorkbenchOperations.ABILITY_MODEL, "");
            answered(helper, operations, WorkbenchOperations.SAVE_ABILITIES, costEdit(EDITED_COST));
            helper.assertTrue(Files.isRegularFile(draft), "the save wrote no draft at " + draft);
            helper.assertTrue(!draft.startsWith(WorkbenchPaths.packRoot(world).resolve("data")),
                    "the draft stands inside the pack's data folder: " + draft);
            AbilityDefinition held = AbilityRegistry.of(server).getAbility(EDITED);
            int heldCost = held == null ? -1 : held.cost().in(GooValues.of(server));
            helper.assertTrue(heldCost == EDITED_COST, "after the save the server prices " + EDITED + " at "
                    + heldCost + ", not " + EDITED_COST);
            Set<Identifier> after = idsOf(server);
            helper.assertTrue(after.equals(before), "the save changed the abilities the server holds: before "
                    + before.size() + ", after " + after.size() + ", added " + added(before, after));
            answered(helper, operations, WorkbenchOperations.SAVE_ABILITIES, costEdit(shippedCost));
            Files.deleteIfExists(draft);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }

    private static Set<Identifier> idsOf(MinecraftServer server) {
        return AbilityRegistry.of(server).all().stream().map(AbilityDefinition::id).collect(Collectors.toSet());
    }

    private static Set<Identifier> added(Set<Identifier> before, Set<Identifier> after) {
        Set<Identifier> added = new HashSet<>(after);
        added.removeAll(before);
        return added;
    }

    private static String costEdit(int cost) {
        JsonObject changes = new JsonObject();
        changes.addProperty("cost", cost);
        JsonObject abilities = new JsonObject();
        abilities.add(EDITED.toString(), changes);
        JsonObject posted = new JsonObject();
        posted.add("abilities", abilities);
        return posted.toString();
    }

    private static String answered(GameTestHelper helper, WorkbenchOperations operations, String operation,
                                   String posted) {
        WorkbenchReply reply = operations.answer(operation, posted);
        helper.assertTrue(reply.status() == WorkbenchReply.OK, operation + " answered " + reply.status() + ": "
                + reply.text());
        return reply.text();
    }
}
