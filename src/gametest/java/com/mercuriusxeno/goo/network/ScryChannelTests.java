package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HeldSweepPulses;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for Scry cast through the real cast path, one ring a press: a
 * mock player holding astral goo casts the astral scry probe once, whose
 * ring runs a damage step where astral's Scry runs its glisten, and the
 * front travels on with no key held to a zombie standing behind a stone
 * wall the player cannot see through, striking it once as it crosses; and
 * Scry pressed once a second for a minute holds its live pulses level.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 * decision scry-is-instant-and-no-longer-channeled
 */
@RegistersGameTests
public final class ScryChannelTests {
    private static final String SCRY_GLISTENS_THE_HIDDEN_ZOMBIE = "scry_glistens_the_hidden_zombie";
    private static final String SCRY_HELD_A_MINUTE_HOLDS_ITS_PULSES_LEVEL = "scry_held_a_minute_holds_its_pulses_level";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SCRY_GLISTENS_THE_HIDDEN_ZOMBIE, ScryChannelTests::scryGlistensTheHiddenZombie);
        GooTestFunctions.registerTest(registrar, SCRY_HELD_A_MINUTE_HOLDS_ITS_PULSES_LEVEL,
                ScryChannelTests::scryHeldAMinuteHoldsItsPulsesLevel);
    }

    private static final int SECOND = 20;
    private static final int FIVE_SECONDS = 100;
    /** The last tick of the minute, on the same beat of the presses as {@link #FIVE_SECONDS}. */
    private static final int A_MINUTE = 1_200;
    /** Enough astral goo to pay Scry each second of a minute and some over. */
    private static final int MINUTE_OF_GOO = 70;
    /** Ticks past a press by which its front, out to its reach in forty ticks, has landed. */
    private static final int LANDED_AFTER = 45;
    /** Astral Scry itself, whose child step lays only the astral overlay, so a minute of it harms no other test's mob. */
    private static final Identifier ASTRAL_SCRY = Identifier.fromNamespaceAndPath("goo", "astral_scry");
    private static final String SCRY_REQUIRED = "Ability registry must hold astral scry";
    private static final String HELD_LEVEL = "A minute of presses should hold as many pulses as five seconds did: %s then, %s now";
    private static final String STILL_PRESSED = "The presses should still be sending pulses at a minute";
    private static final String NONE_AFTER = "No pulse should stand for the caster once its last front landed: %s";

    /**
     * A mock player presses Astral Scry once a second for a minute: the
     * server holds as many live pulses for it at the minute as at five
     * seconds, and none once the last front has reached its range.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param helper the gametest helper
     */
    public static void scryHeldAMinuteHoldsItsPulsesLevel(GameTestHelper helper) {
        AbilityDefinition scry = AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_SCRY);
        helper.assertTrue(scry != null, SCRY_REQUIRED);
        ServerPlayer player = scryer(helper, MINUTE_OF_GOO);
        KnownRecipes.teachRequires(player, scry);
        // the pulses the server holds for the caster at five seconds and at the minute
        int[] live = new int[2];
        for (int press = 1; press <= A_MINUTE; press += SECOND) {
            helper.runAfterDelay(press, () -> SelfDeliveryTests.invoke(player, GooTypes.ASTRAL, ASTRAL_SCRY));
        }
        int lastPress = 1 + (A_MINUTE - 1) / SECOND * SECOND;
        helper.runAfterDelay(FIVE_SECONDS, () -> live[0] = HeldSweepPulses.SERVER.liveFor(player.getUUID()));
        helper.runAfterDelay(A_MINUTE, () -> live[1] = HeldSweepPulses.SERVER.liveFor(player.getUUID()));
        helper.runAfterDelay(A_MINUTE + 1, () -> {
            helper.assertTrue(live[1] > 0, STILL_PRESSED);
            helper.assertTrue(live[1] == live[0], String.format(HELD_LEVEL, live[0], live[1]));
        });
        helper.runAfterDelay(lastPress + LANDED_AFTER, () -> {
            int afterTheLast = HeldSweepPulses.SERVER.liveFor(player.getUUID());
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(afterTheLast == 0, String.format(NONE_AFTER, afterTheLast));
            helper.succeed();
        });
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The wall's column, between the player and the zombie, three blocks high and the bay's width. */
    private static final int WALL_X = 3;
    private static final int WALL_HEIGHT = 3;
    private static final int BAY_WIDTH = 7;
    private static final BlockPos ZOMBIE_POS = new BlockPos(5, 1, 3);
    private static final int HELD_GOO = 2;
    /** The probe's cost, as astral_scry_probe.json sets it, in mB. */
    private static final int PROBE_COST = 1_000;
    private static final float PROBE_DAMAGE = 3f;
    private static final float TOLERANCE = 0.01f;
    private static final String TEST_PACK = "gootest";
    private static final Identifier SCRY_PROBE = Identifier.fromNamespaceAndPath(TEST_PACK, "astral_scry_probe");
    private static final String ABILITY_REQUIRED = "Ability registry must hold the scry probe";
    private static final String NOT_SEEN = "The wall should hide the zombie from the player";
    private static final String STRUCK_ONCE = "The front should strike the hidden zombie once, for %s, it lost %s";
    private static final String PAID_ONCE = "The press should pay the probe's cost once, %s mB, it paid %s";

    private ScryChannelTests() {
    }

    /**
     * A mock player casts the scry probe once and lets go, with a stone wall
     * between it and a zombie four blocks off. The probe's ring crosses its
     * eight block reach in two seconds, kept inside the bay so it strikes no
     * other test's player, passing the zombie on its twentieth tick: once the
     * ring has landed the zombie, unseen, has lost the probe's damage once,
     * and the player has paid the probe's cost once.
     *
     * @param helper the gametest helper
     */
    public static void scryGlistensTheHiddenZombie(GameTestHelper helper) {
        AbilityDefinition probe = AbilityRegistry.of(helper.getLevel()).getAbility(SCRY_PROBE);
        helper.assertTrue(probe != null, ABILITY_REQUIRED);
        for (int z = 0; z < BAY_WIDTH; z++) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float startHealth = zombie.getHealth();
        ServerPlayer player = scryer(helper);
        KnownRecipes.teachRequires(player, probe);
        helper.assertFalse(player.hasLineOfSight(zombie), NOT_SEEN);
        int[] heldBefore = new int[1];
        helper.runAfterDelay(1, () -> {
            heldBefore[0] = held(player);
            SelfDeliveryTests.invoke(player, GooTypes.ASTRAL, SCRY_PROBE);
        });
        helper.runAfterDelay(1 + LANDED_AFTER, () -> {
            int paid = heldBefore[0] - held(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = startHealth - zombie.getHealth();
            helper.assertTrue(Math.abs(lost - PROBE_DAMAGE) < TOLERANCE, String.format(STRUCK_ONCE, PROBE_DAMAGE, lost));
            helper.assertTrue(paid == PROBE_COST, String.format(PAID_ONCE, PROBE_COST, paid));
            helper.succeed();
        });
    }

    private static int held(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ASTRAL, 0);
    }

    private static ServerPlayer scryer(GameTestHelper helper) {
        return scryer(helper, HELD_GOO);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer scryer(GameTestHelper helper, int heldGoo) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ASTRAL, heldGoo * GooStacks.THOUSAND));
        return player;
    }
}
