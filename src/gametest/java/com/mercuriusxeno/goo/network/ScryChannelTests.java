package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HeldSweepPulses;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Gametest for Scry's sweep through the real channel path: a mock player
 * holding astral goo casts the astral scry probe, whose sweep runs a damage
 * step where astral's Scry runs its glisten, and the front reaches a zombie
 * standing behind a stone wall the player cannot see through, striking it
 * once as it crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 */
@RegistersGameTests
public final class ScryChannelTests {
    private static final String SCRY_GLISTENS_THE_HIDDEN_ZOMBIE = "scry_glistens_the_hidden_zombie";

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

    private static final String SCRY_HELD_A_MINUTE_HOLDS_ITS_PULSES_LEVEL = "scry_held_a_minute_holds_its_pulses_level";
    private static final int SECOND = 20;
    private static final int TEN_SECONDS = 200;
    private static final int LAST_SECOND = 1_200;
    /** The hold lets go on the tick a pulse leaves, so a front still travels as it ends. */
    private static final int A_MINUTE = 1_201;
    /** Enough astral goo to pay Scry each second of a minute's hold and some over. */
    private static final int MINUTE_OF_GOO = 70;
    /** Ticks past the hold's end by which Scry's last front, ninety-six blocks out in forty ticks, has landed. */
    private static final int LANDED_AFTER = 45;
    /** Astral Scry itself, whose child step lays only the astral overlay, so a minute's hold harms no other test's mob. */
    private static final Identifier ASTRAL_SCRY = Identifier.fromNamespaceAndPath("goo", "astral_scry");
    private static final String SCRY_REQUIRED = "Ability registry must hold astral scry";
    private static final String HELD_LEVEL = "A minute's hold should hold no more pulses than at ten seconds: %s then, %s now";
    private static final String STILL_HELD = "The hold should still be sending pulses at a minute";
    private static final String NONE_AFTER = "No pulse should stand for the caster once the hold ended and its fronts landed: %s";

    /**
     * A mock player holds Astral Scry for a minute: the server holds no
     * more live pulses for it at the minute than at ten seconds, and none
     * once the hold has ended and its last front has reached its range.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param helper the gametest helper
     */
    public static void scryHeldAMinuteHoldsItsPulsesLevel(GameTestHelper helper) {
        AbilityDefinition scry = AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_SCRY);
        helper.assertTrue(scry != null, SCRY_REQUIRED);
        ServerPlayer player = scryer(helper, MINUTE_OF_GOO);
        KnownRecipes.teachRequires(player, scry);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ASTRAL), ASTRAL_SCRY.toString(),
                player.getEyePosition(), player.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        // the most pulses the server holds for the caster over the tenth second and over the last
        int[] most = new int[2];
        for (int held = 1; held <= A_MINUTE; held++) {
            int heldTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                int live = HeldSweepPulses.SERVER.liveFor(player.getUUID());
                if (heldTick > TEN_SECONDS - SECOND && heldTick <= TEN_SECONDS) {
                    most[0] = Math.max(most[0], live);
                } else if (heldTick > LAST_SECOND - SECOND && heldTick <= LAST_SECOND) {
                    most[1] = Math.max(most[1], live);
                }
            });
        }
        helper.runAfterDelay(LAST_SECOND + 1, () -> {
            helper.assertTrue(most[1] > 0, STILL_HELD);
            helper.assertTrue(most[1] <= most[0], String.format(HELD_LEVEL, most[0], most[1]));
        });
        helper.runAfterDelay(A_MINUTE + LANDED_AFTER, () -> {
            int afterTheHold = HeldSweepPulses.SERVER.liveFor(player.getUUID());
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(afterTheHold == 0, String.format(NONE_AFTER, afterTheHold));
            helper.succeed();
        });
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** The wall's column, between the player and the zombie, three blocks high and the bay's width. */
    private static final int WALL_X = 3;
    private static final int WALL_HEIGHT = 3;
    private static final int BAY_WIDTH = 7;
    private static final BlockPos ZOMBIE_POS = new BlockPos(5, 1, 3);
    /**
     * The probe's pulse eases out to its eight block reach, kept inside the
     * bay so it strikes no other test's player: ten ticks carry it past the
     * zombie four blocks off, inside its first pulse.
     */
    private static final int HOLD_TICKS = 10;
    private static final int HELD_GOO = 2;
    private static final float PROBE_DAMAGE = 3f;
    private static final float TOLERANCE = 0.01f;
    private static final String TEST_PACK = "gootest";
    private static final Identifier SCRY_PROBE = Identifier.fromNamespaceAndPath(TEST_PACK, "astral_scry_probe");
    private static final String ABILITY_REQUIRED = "Ability registry must hold the scry probe";
    private static final String NOT_SEEN = "The wall should hide the zombie from the player";
    private static final String STRUCK_ONCE = "The front should strike the hidden zombie once, for %s, it lost %s";

    private ScryChannelTests() {
    }

    /**
     * A mock player holds the scry probe for ten ticks with a stone wall
     * between it and a zombie four blocks off: the zombie, unseen, loses the
     * probe's damage once, as the front crosses it and never again.
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
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.ASTRAL), SCRY_PROBE.toString(),
                player.getEyePosition(), player.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = startHealth - zombie.getHealth();
            helper.assertTrue(Math.abs(lost - PROBE_DAMAGE) < TOLERANCE, String.format(STRUCK_ONCE, PROBE_DAMAGE, lost));
            helper.succeed();
        });
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
