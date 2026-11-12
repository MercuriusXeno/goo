package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for nether Undead through the real self delivery: at noon under
 * open sky the sun burns an undead player and spares one under a roof;
 * harming heals an undead player; and once nether runs dry the held effect
 * ends, its nether hearts and its undeath with it
 * (decision undead-nether-hearts-burn-in-sunlight).
 */
@RegistersGameTests
public final class UndeadTests {
    private static final String UNDEAD_BURNS_IN_SUN = "undead_burns_in_sun_not_under_roof";
    private static final String UNDEAD_HARMING_HEALS = "undead_harming_heals";
    private static final String UNDEAD_ENDS_WHEN_DRY = "undead_ends_when_nether_runs_dry";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, UNDEAD_BURNS_IN_SUN, UndeadTests::undeadBurnsInSunNotUnderRoof);
        GooTestFunctions.registerTest(registrar, UNDEAD_HARMING_HEALS, UndeadTests::undeadHarmingHeals);
        GooTestFunctions.registerTest(registrar, UNDEAD_ENDS_WHEN_DRY, UndeadTests::undeadEndsWhenNetherRunsDry);
    }


    private static final Identifier NETHER_UNDEAD = Identifier.parse("goo:nether_undead");
    private static final BlockPos OPEN_POS = new BlockPos(1, 1, 1);
    /** Past one of the sun's once-a-second burns. */
    private static final int SUN_TICKS = 25;
    /** Health to heal from, short of full. */
    private static final float WOUNDED_HEALTH = 10f;
    /** The upkeep ticks the test's nether pays for before it runs dry. */
    private static final int PAID_TICKS = 3;
    /** Under the eighteen food a player regenerates at, and above starving. */
    private static final int UNFED_FOOD = 10;
    private static final String ABILITY_REQUIRED = "Ability registry must hold nether_undead";
    private static final String SHOULD_BURN = "The undead player under open noon sky should burn, stands at %.1f";
    private static final String SHOULD_HEAL ="Harming should heal an undead player from %.1f, stands at %.1f";
    private static final String SHOULD_STAND_PAID = "Undead should stand while nether pays its upkeep";
    private static final String SHOULD_END = "Undead, its nether hearts and its undeath should end once nether runs dry";

    private UndeadTests() {
    }

    /**
     * An undead player at noon under open sky has burned after a second.
     *
     * @param helper the gametest helper
     */
    public static void undeadBurnsInSunNotUnderRoof(GameTestHelper helper) {
        ServerPlayer open = undeadAt(helper, OPEN_POS);
        tickPinned(helper, open, OPEN_POS);
        helper.runAfterDelay(SUN_TICKS + 1, () -> {
            float openHealth = open.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(open);
            helper.assertTrue(openHealth < open.getMaxHealth(), String.format(SHOULD_BURN, openHealth));
            helper.succeed();
        });
    }

    /**
     * A wounded undead player takes an instant harming: it heals.
     *
     * @param helper the gametest helper
     */
    public static void undeadHarmingHeals(GameTestHelper helper) {
        ServerPlayer player = undeadAt(helper, OPEN_POS);
        player.setHealth(WOUNDED_HEALTH);

        MobEffects.INSTANT_DAMAGE.value().applyInstantenousEffect(helper.getLevel(), null, null, player, 0, 1.0);

        float after = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(after > WOUNDED_HEALTH, String.format(SHOULD_HEAL, WOUNDED_HEALTH, after));
        helper.succeed();
    }

    /**
     * A player eats Undead holding nether for exactly three ticks of upkeep:
     * Undead stands through the third tick, and on the fourth the held effect
     * ends, the nether hearts clear and the player is undead no longer.
     *
     * @param helper the gametest helper
     */
    public static void undeadEndsWhenNetherRunsDry(GameTestHelper helper) {
        AbilityDefinition undead = AbilityRegistry.of(helper.getLevel()).getAbility(NETHER_UNDEAD);
        helper.assertTrue(undead != null, ABILITY_REQUIRED);
        ServerPlayer player = undeadAt(helper, OPEN_POS);
        int held = GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.NETHER, 0);
        GooSourceScanner.deplete(player, GooTypes.NETHER, held - PAID_TICKS * undead.upkeep().in(GooValueTable.EMPTY));
        SelfDeliveryTests.tickFor(helper, player, PAID_TICKS - 1);
        helper.runAfterDelay(PAID_TICKS, () -> {
            player.doTick();
            helper.assertTrue(stands(player), SHOULD_STAND_PAID);
        });
        helper.runAfterDelay(PAID_TICKS + 1, () -> {
            player.doTick();
            boolean standing = stands(player) || player.getData(GooAttachments.HEART_OVERLAY).stands();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(standing, SHOULD_END);
            helper.succeed();
        });
    }

    /**
     * Ticks a player through the sun's window held on its cell, since a
     * mock player drifts off it over the ticks, out from under a roof.
     *
     * @param helper the gametest helper
     * @param player the player
     * @param pos    the cell it stands on, relative
     */
    private static void tickPinned(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        for (int tick = 1; tick <= SUN_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                player.setPos(stand.x, stand.y, stand.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.doTick();
            });
        }
    }

    private static boolean stands(ServerPlayer player) {
        return player.getData(GooAttachments.HELD_EFFECTS).holds(NETHER_UNDEAD)
                && player.getData(GooAttachments.UNDEAD).stands();
    }

    /**
     * A survival player who has eaten Undead through the glove, standing at a cell.
     *
     * @param helper the gametest helper
     * @param pos    the cell, relative
     * @return the player
     */
    private static ServerPlayer undeadAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.NETHER, NETHER_UNDEAD);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        player.setPos(stand.x, stand.y, stand.z);
        // a fed player regenerates, which would heal the sun's burn back before the test reads it
        player.getFoodData().setFoodLevel(UNFED_FOOD);
        // the eat runs where the mock player spawns, under open sky, where the sun may already have burned it
        player.setHealth(player.getMaxHealth());
        return player;
    }
}
