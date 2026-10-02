package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for the glove's melee damage: a player holding the exo gauntlet
 * hits a mob for the fist's 1 plus the gauntlet's 6
 * (decision glove-damage-by-tier). The target is a villager, which wears no
 * armor to shave the hit.
 */
public final class GloveDamageTests {

    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    private static final BlockPos PLAYER_POS = MOB_POS.west(2);
    private static final int SETTLE_TICKS = 1;
    /** An attack speed whose cooldown passes within the half tick vanilla's attack adds, for a full-strength first swing. */
    private static final double INSTANT_ATTACK_SPEED = 1024;
    /** The fist's 1 plus the exo gauntlet's 6. */
    private static final float EXO_GAUNTLET_HIT = 7.0f;
    private static final float DAMAGE_TOLERANCE = 0.01f;
    private static final String SHOULD_TAKE_HIT = "Villager should lose %.1f health to the exo gauntlet's hit, lost %.1f";

    private GloveDamageTests() {
    }

    /**
     * A mock player holding the exo gauntlet attacks a villager, which loses
     * seven health.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void exoGauntletHitsForSeven(GameTestHelper helper) {
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, MOB_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.EXO_GAUNTLET.get()));
        player.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(INSTANT_ATTACK_SPEED);
        // One entity tick applies the held item's modifiers, as vanilla does on its equipment check.
        player.doTick();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float before = villager.getHealth();
            player.attack(villager);
            float lost = before - villager.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(lost - EXO_GAUNTLET_HIT) < DAMAGE_TOLERANCE,
                    String.format(SHOULD_TAKE_HIT, EXO_GAUNTLET_HIT, lost));
            helper.succeed();
        });
    }
}
