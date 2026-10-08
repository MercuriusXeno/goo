package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Gametest for the share of a hit Stoneskin's stone hearts take, routed by
 * the live level's damage sources so the real damage type and item tags
 * decide: an explosion and a pickaxe find a stone heart worth half a heart,
 * a plain physical hit takes the brew's multiplier, and a hit that bypasses
 * armor lands whole (decision stoneskin-stone-hearts-block-regeneration).
 * It sits in the hearts package to reach the package-private routing.
 */
public final class StoneskinRoutingTests {

    private static final BlockPos PICK_ZOMBIE = new BlockPos(1, 1, 1);
    private static final BlockPos BARE_ZOMBIE = new BlockPos(3, 1, 3);
    /** A multiplier no shipped kind uses, so a plain hit answering it can only have read the brew's. */
    private static final float BREW_MULTIPLIER = 0.37f;
    private static final String SHOULD_SHARE = "A %s hit should take a %s share of stone, took %s";
    private static final String EXPLOSION = "explosion";
    private static final String PICKAXE = "pickaxe";
    private static final String PLAIN = "plain physical";
    private static final String MAGIC = "magic";
    private static final Identifier ROCK_STONESKIN = Identifier.parse("goo:rock_stoneskin");
    private static final float SIX_HEALTH = 6f;
    /** Under natural regeneration's threshold, so only stone could restore health. */
    private static final int HUNGRY_FOOD = 10;
    private static final String SHOULD_STONE_FIRST_MISSING = "Stoneskin should prime stone at slot %d, read %s";
    private static final String SHOULD_KEEP_HEALTH = "Ending Stoneskin should leave health at %.1f, read %.1f";

    private StoneskinRoutingTests() {
    }

    /**
     * Routes four live sources through the stone share: an explosion, a
     * zombie holding an iron pickaxe, an unarmed zombie, and magic.
     *
     * @param helper the gametest helper
     */
    public static void stoneskinRoutesHitsBySource(GameTestHelper helper) {
        Zombie withPickaxe = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, PICK_ZOMBIE);
        withPickaxe.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        Zombie bare = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, BARE_ZOMBIE);
        bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        DamageSources sources = helper.getLevel().damageSources();
        assertShare(helper, EXPLOSION, sources.explosion(null, null), HeartOverlayEvents.STONE_BRITTLE_SHARE);
        assertShare(helper, PICKAXE, sources.mobAttack(withPickaxe), HeartOverlayEvents.STONE_BRITTLE_SHARE);
        assertShare(helper, PLAIN, sources.mobAttack(bare), BREW_MULTIPLIER);
        assertShare(helper, MAGIC, sources.magic(), HeartOverlay.WHOLE_HIT);
        helper.succeed();
    }

    /**
     * A player at six health eats Stoneskin: stone primes over the first
     * missing heart, and ending it leaves real health at six.
     * heart-effects-crawl-while-held
     *
     * @param helper the gametest helper
     */
    public static void stoneskinEndsLeavingHealthAsItStood(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.ROCK);
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(HUNGRY_FOOD);
        player.getFoodData().setSaturation(0);
        player.setHealth(SIX_HEALTH);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(ROCK_STONESKIN));
        SelfDeliveryTests.invoke(player, GooTypes.ROCK, ROCK_STONESKIN);
        SelfDeliveryTests.eatThrough(player);
        HeartOverlay stoned = player.getData(GooAttachments.HEART_OVERLAY);
        int firstMissing = HeartOverlay.filledSlots(SIX_HEALTH);

        SelfDeliveryTests.invoke(player, GooTypes.ROCK, ROCK_STONESKIN);

        float health = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(stoned.shieldAt(firstMissing) == HeartOverlay.FULL_SHIELD
                        && stoned.shieldHalves() == HeartOverlay.FULL_SHIELD,
                String.format(SHOULD_STONE_FIRST_MISSING, firstMissing, stoned.shields()));
        helper.assertTrue(health == SIX_HEALTH, String.format(SHOULD_KEEP_HEALTH, SIX_HEALTH, health));
        helper.succeed();
    }

    private static void assertShare(GameTestHelper helper, String hit, DamageSource source, float expected) {
        float share = HeartOverlayEvents.stoneShare(source, BREW_MULTIPLIER);
        helper.assertTrue(share == expected, String.format(SHOULD_SHARE, hit, expected, share));
    }
}
