package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gametests for aggravated damage through Leaf Barkskin invoked on a survival
 * mock player by the real self delivery: fire and axes pass the bark to take
 * real health and burn twice their hearts in bark, an arrow breaks bark alone,
 * and a striking mob takes thorns by the bark count (decisions
 * aggravated-damage-is-a-per-heart-rule and barkskin-bark-hearts-thorn-and-burn).
 */
public final class BarkskinTests {

    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    private static final BlockPos ATTACKER_POS = new BlockPos(2, 1, 3);
    private static final BlockPos AXE_ATTACKER_POS = new BlockPos(1, 1, 2);
    private static final float FULL_HEALTH = 20f;
    private static final int FULL_BARK = 10;
    private static final float FIRE = 4f;
    /** Four fire is four halves: four burned for it and four again, of twenty. */
    private static final int BARK_AFTER_FIRE = 12;
    private static final float ONE_POINT = 1f;
    private static final int BURN_FACTOR = 2;
    private static final float TOLERANCE = 1e-4f;
    private static final String SHOULD_BURN_THROUGH =
            "Fire should take %.1f health and burn bark to %d halves: health %.1f, %d halves";
    private static final String SHOULD_ONLY_BREAK_BARK = "An arrow should strip half a bark and spare health: health %.1f, %d halves";
    private static final String SHOULD_THORN = "The zombie should take %d thorns: health %.1f of %.1f";
    private static final String SHOULD_AXE_BURN =
            "An axe should take health and burn it twice in bark halves: lost %.1f, halves %d to %d";

    private BarkskinTests() {
    }

    /**
     * Fire on a barkskinned player takes its whole amount from health and burns
     * twice its hearts in bark; an arrow then breaks one bark and spares health.
     *
     * @param helper the gametest helper
     */
    public static void fireBurnsThroughArrowBreaksBark(GameTestHelper helper) {
        ServerPlayer player = barked(helper);
        HeartOverlayTests.hurt(helper, player, player.damageSources().inFire(), FIRE);
        float afterFire = player.getHealth();
        int barkAfterFire = HeartOverlayTests.halves(player);

        Arrow arrow = EntityType.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        HeartOverlayTests.hurt(helper, player, player.damageSources().arrow(arrow, null), ONE_POINT);
        float afterArrow = player.getHealth();
        int barkAfterArrow = HeartOverlayTests.halves(player);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(Math.abs(afterFire - (FULL_HEALTH - FIRE)) < TOLERANCE && barkAfterFire == BARK_AFTER_FIRE,
                String.format(SHOULD_BURN_THROUGH, FIRE, BARK_AFTER_FIRE, afterFire, barkAfterFire));
        helper.assertTrue(Math.abs(afterArrow - afterFire) < TOLERANCE && barkAfterArrow == BARK_AFTER_FIRE - 1,
                String.format(SHOULD_ONLY_BREAK_BARK, afterArrow, barkAfterArrow));
        helper.succeed();
    }

    /**
     * A bare-handed zombie striking a barkskinned player takes thorns by the
     * bark standing when it struck; a zombie striking with an axe takes health
     * and burns twice its hearts in bark.
     *
     * @param helper the gametest helper
     */
    public static void thornsAndTheAxe(GameTestHelper helper) {
        ServerPlayer player = barked(helper);
        Zombie zombie = unarmored(helper, ATTACKER_POS);
        float zombieMax = zombie.getMaxHealth();
        zombie.doHurtTarget(helper.getLevel(), player);
        float zombieHealth = zombie.getHealth();

        player.invulnerableTime = 0;
        int barkBefore = HeartOverlayTests.halves(player);
        float healthBefore = player.getHealth();
        Zombie axeman = unarmored(helper, AXE_ATTACKER_POS);
        axeman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        axeman.doHurtTarget(helper.getLevel(), player);
        float lost = healthBefore - player.getHealth();
        int barkAfter = HeartOverlayTests.halves(player);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(Math.abs(zombieMax - FULL_BARK - zombieHealth) < TOLERANCE,
                String.format(SHOULD_THORN, FULL_BARK, zombieHealth, zombieMax));
        int burned = BURN_FACTOR * (int) Math.ceil(lost);
        helper.assertTrue(lost > 0f && barkAfter == Math.max(0, barkBefore - burned),
                String.format(SHOULD_AXE_BURN, lost, barkBefore, barkAfter));
        helper.succeed();
    }

    private static Zombie unarmored(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        // the zombie's natural armor would cut the thorns; the test reads the thorns whole
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
        return zombie;
    }

    private static ServerPlayer barked(GameTestHelper helper) {
        return HeartOverlayTests.selfInvoked(helper, GooTypes.LEAF, LEAF_BARKSKIN);
    }
}
