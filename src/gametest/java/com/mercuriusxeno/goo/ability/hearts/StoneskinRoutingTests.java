package com.mercuriusxeno.goo.ability.hearts;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    private static void assertShare(GameTestHelper helper, String hit, DamageSource source, float expected) {
        float share = HeartOverlayEvents.stoneShare(source, BREW_MULTIPLIER);
        helper.assertTrue(share == expected, String.format(SHOULD_SHARE, hit, expected, share));
    }
}
