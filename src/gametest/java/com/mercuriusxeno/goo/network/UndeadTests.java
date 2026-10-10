package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
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

/**
 * Gametests for nether Undead through the real self delivery: at noon under
 * open sky the sun burns an undead player and spares one under a roof;
 * harming heals an undead player; and once nether runs dry the held effect
 * ends, its nether hearts and its undeath with it
 * (decision undead-nether-hearts-burn-in-sunlight).
 */
public final class UndeadTests {

    private static final Identifier NETHER_UNDEAD = Identifier.parse("goo:nether_undead");
    private static final BlockPos OPEN_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ROOFED_POS = new BlockPos(4, 1, 1);
    /** The roof sits three above the roofed player's feet, over its head. */
    private static final int ROOF_HEIGHT = 3;
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
    private static final String SHOULD_NOT_BURN = "The undead player under a roof should not burn, stands at %.1f";
    private static final String SHOULD_REACH_HEALTH =
            "The sun should burn past the nether to real health from %.1f, stands at %.1f";
    private static final String SHOULD_HEAL ="Harming should heal an undead player from %.1f, stands at %.1f";
    private static final String SHOULD_STAND_PAID = "Undead should stand while nether pays its upkeep";
    private static final String SHOULD_END = "Undead, its nether hearts and its undeath should end once nether runs dry";

    private UndeadTests() {
    }

    /**
     * Two undead players at noon, one under open sky and one under a stone
     * roof: after a second the open one has burned and the roofed one stands whole.
     *
     * @param helper the gametest helper
     */
    public static void undeadBurnsInSunNotUnderRoof(GameTestHelper helper) {
        // a roof wider than the cell, so a player settling off its center stays under it
        BlockPos roof = ROOFED_POS.above(ROOF_HEIGHT);
        for (BlockPos cell : BlockPos.betweenClosed(roof.offset(-1, 0, -1), roof.offset(1, 0, 1))) {
            helper.setBlock(cell, Blocks.STONE);
        }
        ServerPlayer open = undeadAt(helper, OPEN_POS);
        ServerPlayer roofed = undeadAt(helper, ROOFED_POS);
        tickPinned(helper, open, OPEN_POS);
        tickPinned(helper, roofed, ROOFED_POS);
        helper.runAfterDelay(SUN_TICKS + 1, () -> {
            float openHealth = open.getHealth();
            float roofedHealth = roofed.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(open);
            helper.getLevel().getServer().getPlayerList().remove(roofed);
            helper.assertTrue(openHealth < open.getMaxHealth(), String.format(SHOULD_BURN, openHealth));
            helper.assertTrue(roofedHealth == roofed.getMaxHealth(), String.format(SHOULD_NOT_BURN, roofedHealth));
            helper.succeed();
        });
    }

    /**
     * A wounded undead player stands at noon under open sky with nether over
     * its missing hearts: the sun's burn reaches its real health past the
     * nether, as aggravated damage does.
     *
     * @param helper the gametest helper
     */
    public static void undeadSunburnIsAggravated(GameTestHelper helper) {
        ServerPlayer player = undeadAt(helper, OPEN_POS);
        player.setHealth(WOUNDED_HEALTH);
        // lay the nether afresh over the hearts the wound left missing
        player.setData(GooAttachments.HEART_OVERLAY, HeartOverlay.NONE.hold(HeartKind.UNDEAD, WOUNDED_HEALTH,
                player.getMaxHealth(), HeartOverlay.WHOLE_HIT, player.level().getGameTime()));
        SelfDeliveryTests.tickFor(helper, player, SUN_TICKS);
        helper.runAfterDelay(SUN_TICKS + 1, () -> {
            float health = player.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(health < WOUNDED_HEALTH, String.format(SHOULD_REACH_HEALTH, WOUNDED_HEALTH, health));
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
        GooSourceScanner.deplete(player, GooTypes.NETHER, held - PAID_TICKS * undead.upkeep());
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
