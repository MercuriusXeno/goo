package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabFirstJoin;
import com.mercuriusxeno.goo.lab.LabFirstJoinMarker;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.lab.LabPen;
import com.mercuriusxeno.goo.lab.LabPlacement;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gametests for the Goo Lab save's first join: it builds the lab, hands the
 * kit and teaches every recipe once per save, reads a lab already standing as
 * built, and leaves a forget standing across a rejoin. Every gametest in a run
 * shares one level, so each test hands the routine a fresh marker and its own origin.
 * decision goo-lab-save-builds-and-kits-on-first-join
 * decision goo-lab-save-teaches-every-recipe-on-first-join
 */
@RegistersGameTests
public final class LabFirstJoinTests {
    private static final String LAB_FIRST_JOIN_BUILDS_AND_KITS = "lab_first_join_builds_and_kits";
    private static final String LAB_SECOND_JOIN_KEEPS_WHAT_THE_PLAYER_LEFT = "lab_second_join_keeps_what_the_player_left";
    private static final String LAB_FIRST_JOIN_READS_A_STANDING_LAB_AS_BUILT = "lab_first_join_reads_a_standing_lab_as_built";
    private static final String LAB_FIRST_JOIN_TEACHES_EVERY_ABILITY = "lab_first_join_teaches_every_ability";
    private static final String LAB_FORGET_HOLDS_ACROSS_A_REJOIN = "lab_forget_holds_across_a_rejoin";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LAB_FIRST_JOIN_BUILDS_AND_KITS, LabFirstJoinTests::firstJoinBuildsAndKits);
        GooTestFunctions.registerTest(registrar, LAB_SECOND_JOIN_KEEPS_WHAT_THE_PLAYER_LEFT,
                LabFirstJoinTests::secondJoinKeepsWhatThePlayerLeft);
        GooTestFunctions.registerTest(registrar, LAB_FIRST_JOIN_READS_A_STANDING_LAB_AS_BUILT,
                LabFirstJoinTests::firstJoinReadsAStandingLabAsBuilt);
        GooTestFunctions.registerTest(registrar, LAB_FIRST_JOIN_TEACHES_EVERY_ABILITY,
                LabFirstJoinTests::firstJoinTeachesEveryAbility);
        GooTestFunctions.registerTest(registrar, LAB_FORGET_HOLDS_ACROSS_A_REJOIN,
                LabFirstJoinTests::forgetHoldsAcrossARejoin);
    }

    private static final String REMOVAL = "removal";
    private static final String FIT_OUT_FAILED = "First join failed: ";
    private static final String PLANNED_MISSING = "First join should build the planned block at ";
    private static final String GLOVE_MISSING = "First join should hand the goo glove";
    private static final String VAT_MISSING = "First join should hand the stocked vat";
    private static final String MARKER_UNBUILT = "First join should write the marker built";
    private static final String FOREIGN_GONE = "A second join should leave the player's block standing";
    private static final String INVENTORY_REPLACED = "The join should leave the inventory holding the player's item alone";
    private static final String PEN_EMPTY = "The standing lab's pen should hold mobs before the join";
    private static final String PEN_RESPAWNED = "A join reading a standing lab should spawn no pen mob again";
    private static final String NO_ABILITIES = "The gametest server should load abilities to unlock";
    private static final String ABILITY_LOCKED = "First join should unlock the ability ";
    private static final String TAUGHT_NOTHING = "First join should teach the player before the forget";
    private static final String FORGET_UNDONE = "A rejoin after a forget should leave the player knowing stone alone";

    private LabFirstJoinTests() {
    }

    /**
     * Runs the first join on a fresh marker and asserts a planned block stands,
     * the kit's glove and vat are in the inventory and the marker reads built.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void firstJoinBuildsAndKits(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabFirstJoinMarker marker = new LabFirstJoinMarker();
        fitOut(helper, origin, player, marker);
        assertPlanned(helper, origin, floorPlacement(plan));
        helper.assertTrue(player.getInventory().contains(new ItemStack(GooItems.GOO_GLOVE.get())), GLOVE_MISSING);
        helper.assertTrue(player.getInventory().contains(stack -> stack.is(GooItems.VAT.get())), VAT_MISSING);
        helper.assertTrue(marker.isBuilt(), MARKER_UNBUILT);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Runs the first join, sets a foreign block over a planned placement and
     * replaces the inventory with one item, runs the join again on the same
     * marker, and asserts the block and the item are as the player left them.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void secondJoinKeepsWhatThePlayerLeft(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabFirstJoinMarker marker = new LabFirstJoinMarker();
        fitOut(helper, origin, player, marker);
        LabPlacement altered = floorPlacement(plan);
        assertPlanned(helper, origin, altered);
        BlockPos alteredPos = LabBuilder.worldPos(origin, altered.offset());
        helper.getLevel().setBlock(alteredPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        holdAppleAlone(player);
        fitOut(helper, origin, player, marker);
        helper.assertTrue(helper.getLevel().getBlockState(alteredPos).is(Blocks.DIAMOND_BLOCK), FOREIGN_GONE);
        assertHoldsAppleAlone(helper, player.getInventory());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Builds the lab through LabBuilder, runs the first join on a fresh marker,
     * and asserts the pen's mob count and the inventory are unchanged and the marker reads built.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void firstJoinReadsAStandingLabAsBuilt(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        LabTests.buildPlan(helper, origin, plan);
        LabPen pen = plan.pens().getFirst();
        int mobsBefore = mobsInside(helper, origin, pen);
        helper.assertTrue(mobsBefore > 0, PEN_EMPTY);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        holdAppleAlone(player);
        LabFirstJoinMarker marker = new LabFirstJoinMarker();
        fitOut(helper, origin, player, marker);
        helper.assertTrue(mobsInside(helper, origin, pen) == mobsBefore, PEN_RESPAWNED);
        assertHoldsAppleAlone(helper, player.getInventory());
        helper.assertTrue(marker.isBuilt(), MARKER_UNBUILT);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Runs the first join on a fresh marker and asserts every loaded ability is known to the player.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void firstJoinTeachesEveryAbility(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        fitOut(helper, origin, player, new LabFirstJoinMarker());
        KnownItems known = PlayerKnowledge.of(player);
        Collection<AbilityDefinition> abilities = AbilityRegistry.of(helper.getLevel()).all();
        helper.assertFalse(abilities.isEmpty(), NO_ABILITIES);
        for (AbilityDefinition ability : abilities) {
            helper.assertTrue(ability.isKnownTo(known), ABILITY_LOCKED + ability.id());
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Runs the first join, forgets, teaches stone, runs the join again on the
     * same marker, and asserts the player knows stone alone.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void forgetHoldsAcrossARejoin(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabFirstJoinMarker marker = new LabFirstJoinMarker();
        fitOut(helper, origin, player, marker);
        helper.assertTrue(PlayerKnowledge.of(player).items().size() > 1, TAUGHT_NOTHING);
        LabKit.forget(player);
        PlayerKnowledge.learn(player, Items.STONE);
        fitOut(helper, origin, player, marker);
        helper.assertTrue(PlayerKnowledge.of(player).items().equals(Set.of(PlayerKnowledge.idOf(Items.STONE))),
                FORGET_UNDONE);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Runs the first-join routine, failing the test when a planned state does not parse.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param player the joining player
     * @param marker the save's marker
     */
    static void fitOut(GameTestHelper helper, BlockPos origin, ServerPlayer player, LabFirstJoinMarker marker) {
        try {
            LabFirstJoin.fitOut(helper.getLevel(), origin, player, marker);
        } catch (CommandSyntaxException e) {
            helper.fail(FIT_OUT_FAILED + e.getMessage());
        }
    }

    /**
     * Answers a placement on the floor plate, the lab's lowest planned layer.
     *
     * @param plan the plan
     * @return the placement
     */
    private static LabPlacement floorPlacement(LabPlan plan) {
        return plan.placements().stream().filter(p -> p.offset().y() == 0).findFirst().orElseThrow();
    }

    /**
     * Asserts a placement's planned block stands at its world position.
     *
     * @param helper    the gametest helper
     * @param origin    the world position of the plan's zero offset
     * @param placement the placement
     */
    private static void assertPlanned(GameTestHelper helper, BlockPos origin, LabPlacement placement) {
        BlockPos pos = LabBuilder.worldPos(origin, placement.offset());
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(LabTests.plannedBlock(placement)),
                PLANNED_MISSING + placement.offset());
    }

    /**
     * Replaces a player's inventory with one apple, the item a test reads back to tell the player's inventory from the kit.
     *
     * @param player the player
     */
    private static void holdAppleAlone(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.APPLE));
    }

    /**
     * Asserts an inventory holds one apple and nothing else.
     *
     * @param helper    the gametest helper
     * @param inventory the inventory
     */
    private static void assertHoldsAppleAlone(GameTestHelper helper, Inventory inventory) {
        int stacks = 0;
        boolean apple = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                stacks++;
                apple = stack.is(Items.APPLE) && stack.getCount() == 1;
            }
        }
        helper.assertTrue(stacks == 1 && apple, INVENTORY_REPLACED);
    }

    /**
     * Counts the non-player entities inside a pen's interior.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param pen    the pen
     * @return the entity count
     */
    private static int mobsInside(GameTestHelper helper, BlockPos origin, LabPen pen) {
        BlockPos low = LabBuilder.worldPos(origin, pen.interior().min());
        BlockPos high = LabBuilder.worldPos(origin, pen.interior().max());
        AABB inside = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
        return helper.getLevel().getEntities((Entity) null, inside, entity -> !(entity instanceof Player)).size();
    }
}
